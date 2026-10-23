/*
* Copyright (C) 2026 Rastislav Kish
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, version 3.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/
package com.rastislavkish.vscan.core.files

import java.io.File
import java.io.IOException

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import com.rastislavkish.vscan.core.toml.Toml

import com.rastislavkish.vscan.ui.BrokenFileDialogs

// The VScan folder: the configs with their assets, the global assets, and the providers, secrets and settings files
// The app reads a private mirror of it. When the user links a folder, the folder is the source of truth: pull() brings its changes into the mirror, and writes go to both
// Thread-safe
class VScanFiles(
    val mirror: LocalFileTree,
    // Where the state of the linked folder as of the last synchronization is kept
    private val manifestFile: File,
    ) {

    @Volatile var linked: FileTree?=null
    private set

    // Problems the user should know about, e.g. the linked folder being unavailable
    @Volatile var onProblem: ((String) -> Unit)?=null
    // Files that can't be read, which the user has to fix
    @Volatile var onBrokenFile: ((BrokenFile) -> Unit)?=null

    // The modification time and size of the linked folder's files as they were copied to the mirror, by path
    // Changes made while the folder was unavailable are marked PENDING, and copied to it once it's back
    private val manifest: MutableMap<String, String> = loadManifest()

    fun read(path: String): ByteArray? = mirror.read(path)
    fun readText(path: String): String? = read(path)?.toString(Charsets.UTF_8)
    fun list(path: String): List<FileTree.Entry>? = mirror.list(path)
    // The mirror's file, e.g. for players needing a path
    fun file(path: String): File = mirror.file(path)

    // Null when the file doesn't exist, throws TomlException when it's broken
    fun <T> readToml(path: String, serializer: KSerializer<T>): T? {
        val text=readText(path) ?: return null
        return Toml.decode(serializer, text)
        }
    fun <T> writeToml(path: String, serializer: KSerializer<T>, value: T, header: String?=null, omit: Set<String> = setOf()) {
        write(path, Toml.encode(serializer, value, header, omit).toByteArray())
        }

    @Synchronized
    fun write(path: String, data: ByteArray) {
        mirror.write(path, data)

        val linked=linked ?: return
        try {
            val entry=linked.write(path, data)
            manifest[path]=key(entry)
            saveManifest()
            }
        catch (e: IOException) {
            manifest[path]=PENDING
            saveManifest()
            report("Saved only on this device until the linked folder is available again. ${e.message}")
            }
        }

    @Synchronized
    fun delete(path: String) {
        mirror.delete(path)
        manifest.keys.removeAll { it==path || it.startsWith("$path/") }

        try {
            linked?.delete(path)
            }
        catch (e: IOException) {
            manifest[path]=PENDING
            report("Deleted only on this device until the linked folder is available again. ${e.message}")
            }
        saveManifest()
        }

    // Brings the changes of the linked folder into the mirror, returning the paths of the changed files and directories
    @Synchronized
    fun pull(): Set<String> {
        val linked=linked ?: return setOf()
        val changed=mutableSetOf<String>()

        try {
            pushPending(linked)
            pullDirectory(linked, "", changed)
            }
        catch (e: IOException) {
            report("The linked folder is unavailable, using the copy on this device. ${e.message}")
            }
        finally {
            saveManifest()
            }
        return changed
        }

    // Whether the tree holds VScan files already
    fun hasVScanFiles(tree: FileTree): Boolean {
        return listOf(CONFIGS, SETTINGS, PROVIDERS).any { tree.exists(it) }
        }

    // useItsFiles: the folder's files replace the ones on this device, otherwise this device's files are copied to the folder
    // Returns the changed paths, throws IOException when the folder can't be used
    @Synchronized
    fun link(tree: FileTree, useItsFiles: Boolean): Set<String> {
        manifest.clear()

        if (useItsFiles) {
            val changed=mutableSetOf<String>()
            pullDirectory(tree, "", changed)
            linked=tree
            saveManifest()
            return changed
            }

        pushDirectory(tree, "")
        linked=tree
        saveManifest()
        return setOf()
        }
    @Synchronized
    fun unlink() {
        linked=null
        manifest.clear()
        saveManifest()
        }

    private fun pullDirectory(source: FileTree, path: String, changed: MutableSet<String>) {
        val entries=(source.list(path) ?: listOf()).filter { !isHidden(it.name) }

        for (entry in entries) {
            val child=FileTree.join(path, entry.name)
            val mirrorFile=mirror.file(child)

            if (entry.isDirectory) {
                if (mirrorFile.isFile) {
                    mirror.delete(child)
                    changed.add(child)
                    }
                pullDirectory(source, child, changed)
                continue
                }

            if (manifest[child]==key(entry) && mirrorFile.isFile)
            continue

            val data=source.read(child) ?: continue
            if (!mirrorFile.isFile || !mirrorFile.readBytes().contentEquals(data)) {
                mirror.write(child, data)
                changed.add(child)
                }
            manifest[child]=key(entry)
            }

        // Removed from the folder
        val names=entries.map { it.name }.toSet()
        for (entry in mirror.list(path) ?: listOf()) {
            if (isHidden(entry.name) || entry.name in names)
            continue

            val child=FileTree.join(path, entry.name)
            mirror.delete(child)
            manifest.keys.removeAll { it==child || it.startsWith("$child/") }
            changed.add(child)
            }
        }

    private fun pushPending(target: FileTree) {
        for (path in manifest.filterValues { it==PENDING }.keys) {
            val data=mirror.read(path)
            if (data!=null)
            manifest[path]=key(target.write(path, data))
            else {
                target.delete(path)
                manifest.remove(path)
                }
            }
        }

    private fun pushDirectory(target: FileTree, path: String) {
        for (entry in mirror.list(path) ?: listOf()) {
            if (isHidden(entry.name))
            continue

            val child=FileTree.join(path, entry.name)
            if (entry.isDirectory)
            pushDirectory(target, child)
            else
            manifest[child]=key(target.write(child, mirror.read(child) ?: continue))
            }
        }

    fun report(problem: String) {
        onProblem?.invoke(problem)
        }
    fun reportBrokenFile(file: BrokenFile) {
        onBrokenFile?.invoke(file)
        }

    private fun loadManifest(): MutableMap<String, String> {
        try {
            if (manifestFile.isFile)
            return Json.decodeFromString<Map<String, String>>(manifestFile.readText()).toMutableMap()
            }
        catch (e: Exception) {
            // Everything gets compared again
            }
        return mutableMapOf()
        }
    private fun saveManifest() {
        try {
            manifestFile.writeText(Json.encodeToString(manifest.toMap()))
            }
        catch (e: IOException) {
            }
        }

    companion object {

        private const val PENDING="pending"

        const val CONFIGS="configs"
        const val ASSETS="assets"
        const val PROVIDERS="providers.toml"
        const val SECRETS="secrets.toml"
        const val SETTINGS="settings.toml"

        // E.g. .git, or the temporary files of writes
        private fun isHidden(name: String): Boolean = name.startsWith(".")

        private fun key(entry: FileTree.Entry): String = "${entry.lastModified}:${entry.size}"

        private const val DEVICE_PREFERENCES="VScanDevice"
        private const val LINKED_FOLDER_KEY="linkedFolder"

        private var instance: VScanFiles?=null

        @Synchronized
        fun getInstance(context: Context): VScanFiles {
            if (instance==null) {
                val appContext=context.applicationContext
                val files=VScanFiles(LocalFileTree(File(appContext.filesDir, "VScan")), File(appContext.filesDir, "VScanSync.json"))

                val handler=Handler(Looper.getMainLooper())
                files.onProblem={ problem ->
                    handler.post { Toast.makeText(appContext, problem, Toast.LENGTH_LONG).show() }
                    }
                BrokenFileDialogs.install(appContext as Application)
                files.onBrokenFile={ file -> BrokenFileDialogs.add(file) }

                val uri=linkedFolderUri(appContext)
                if (uri!=null) {
                    if (appContext.contentResolver.persistedUriPermissions.any { it.uri==uri && it.isReadPermission && it.isWritePermission })
                    files.linked=DocumentFileTree(appContext, uri)
                    else
                    files.report("VScan lost the access to the linked folder, link it again in the settings")
                    }

                instance=files
                }

            return instance!!
            }

        fun linkedFolderUri(context: Context): Uri? {
            val text=context.getSharedPreferences(DEVICE_PREFERENCES, Context.MODE_PRIVATE).getString(LINKED_FOLDER_KEY, null)
            return text?.let { Uri.parse(it) }
            }

        // Keeps the access to the folder across restarts, throws IOException when it can't be used
        fun linkFolder(context: Context, uri: Uri, useItsFiles: Boolean): Set<String> {
            val appContext=context.applicationContext
            val files=getInstance(appContext)
            val flags=Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            appContext.contentResolver.takePersistableUriPermission(uri, flags)
            val changed=try {
                files.link(DocumentFileTree(appContext, uri), useItsFiles)
                }
            catch (e: IOException) {
                appContext.contentResolver.releasePersistableUriPermission(uri, flags)
                throw e
                }

            releaseOldFolder(appContext, uri)
            appContext.getSharedPreferences(DEVICE_PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(LINKED_FOLDER_KEY, uri.toString())
            .commit()
            return changed
            }
        // The files stay on this device
        fun unlinkFolder(context: Context) {
            val appContext=context.applicationContext
            getInstance(appContext).unlink()
            releaseOldFolder(appContext, null)
            appContext.getSharedPreferences(DEVICE_PREFERENCES, Context.MODE_PRIVATE).edit()
            .remove(LINKED_FOLDER_KEY)
            .commit()
            }

        private fun releaseOldFolder(context: Context, keep: Uri?) {
            val old=linkedFolderUri(context) ?: return
            if (old==keep)
            return
            try {
                context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            catch (e: SecurityException) {
                }
            }
        }
    }
