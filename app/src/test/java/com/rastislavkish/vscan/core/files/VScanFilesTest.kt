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

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VScanFilesTest {

    @get:Rule
    val temporary=TemporaryFolder()

    private lateinit var files: VScanFiles
    private lateinit var folder: LocalFileTree
    private val problems=mutableListOf<String>()

    @Before
    fun setUp() {
        files=newFiles()
        folder=LocalFileTree(temporary.newFolder("linked"))
        }

    private fun newFiles(): VScanFiles {
        val root=File(temporary.root, "device")
        return VScanFiles(LocalFileTree(File(root, "VScan")), File(root, "manifest.json")).also {
            it.onProblem={ problem -> problems.add(problem) }
            }
        }

    private fun FileTree.text(path: String): String? = read(path)?.toString(Charsets.UTF_8)
    private fun FileTree.put(path: String, text: String) { write(path, text.toByteArray()) }

    @Test
    fun linkingAnEmptyFolderCopiesTheFilesThere() {
        files.write("configs/find/config.toml", "name = \"Find\"".toByteArray())
        files.write("settings.toml", "use_sounds = true".toByteArray())
        assertFalse(files.hasVScanFiles(folder))

        assertEquals(setOf<String>(), files.link(folder, false))

        assertEquals("name = \"Find\"", folder.text("configs/find/config.toml"))
        assertEquals("use_sounds = true", folder.text("settings.toml"))
        assertEquals(setOf<String>(), files.pull())
        }

    @Test
    fun linkingAFolderWithFilesReplacesTheLocalOnes() {
        files.write("configs/local/config.toml", "name = \"Local\"".toByteArray())
        folder.put("configs/find/config.toml", "name = \"Find\"")
        folder.put("assets/beep.wav", "RIFF")
        assertTrue(files.hasVScanFiles(folder))

        val changed=files.link(folder, true)

        assertEquals(setOf("configs/local", "configs/find/config.toml", "assets/beep.wav"), changed)
        assertNull(files.readText("configs/local/config.toml"))
        assertEquals("name = \"Find\"", files.readText("configs/find/config.toml"))
        }

    @Test
    fun pullBringsOnlyChanges() {
        files.link(folder, false)
        folder.put("configs/find/config.toml", "a")
        folder.put("configs/other/config.toml", "b")
        assertEquals(setOf("configs/find/config.toml", "configs/other/config.toml"), files.pull())
        assertEquals(setOf<String>(), files.pull())

        // An edit of the same size
        Thread.sleep(10)
        folder.put("configs/find/config.toml", "c")
        File(folder.root, "configs/find/config.toml").setLastModified(System.currentTimeMillis()+5000)
        assertEquals(setOf("configs/find/config.toml"), files.pull())
        assertEquals("c", files.readText("configs/find/config.toml"))

        folder.delete("configs/other")
        assertEquals(setOf("configs/other"), files.pull())
        assertNull(files.list("configs/other"))
        }

    @Test
    fun ownWritesAreNotChanges() {
        files.link(folder, false)
        files.write("providers.toml", "x = 1".toByteArray())

        assertEquals("x = 1", folder.text("providers.toml"))
        assertEquals(setOf<String>(), files.pull())

        files.delete("providers.toml")
        assertFalse(folder.exists("providers.toml"))
        assertEquals(setOf<String>(), files.pull())
        }

    @Test
    fun hiddenFilesAreIgnored() {
        files.link(folder, false)
        folder.put(".git/config", "x")
        folder.put("configs/.find.swp", "x")

        assertEquals(setOf<String>(), files.pull())
        }

    @Test
    fun theManifestSurvivesRestarts() {
        files.link(folder, false)
        folder.put("settings.toml", "a")
        files.pull()

        val restarted=newFiles()
        restarted.link(folder, true)
        assertEquals(setOf<String>(), restarted.pull())
        }

    // Fails while unavailable, e.g. without the permission or with the storage removed
    private class Switchable(private val tree: FileTree): FileTree by tree {

        var available=true

        private fun check() {
            if (!available)
            throw FileTreeException("Gone")
            }

        override fun list(path: String): List<FileTree.Entry>? { check(); return tree.list(path) }
        override fun read(path: String): ByteArray? { check(); return tree.read(path) }
        override fun write(path: String, data: ByteArray): FileTree.Entry { check(); return tree.write(path, data) }
        override fun delete(path: String) { check(); tree.delete(path) }
        }

    @Test
    fun anUnavailableFolderLeavesTheLocalCopy() {
        val switchable=Switchable(folder)
        files.link(switchable, false)
        folder.put("settings.toml", "a")
        files.pull()

        switchable.available=false
        assertEquals(setOf<String>(), files.pull())
        assertEquals("a", files.readText("settings.toml"))

        files.write("settings.toml", "b".toByteArray())
        assertEquals("b", files.readText("settings.toml"))
        assertEquals(2, problems.size)

        folder.put("removed.toml", "x")
        files.delete("removed.toml")

        // The changes reach the folder once it's back
        switchable.available=true
        assertEquals(setOf<String>(), files.pull())
        assertEquals("b", folder.text("settings.toml"))
        assertFalse(folder.exists("removed.toml"))
        assertEquals(setOf<String>(), files.pull())
        }

    @Test
    fun aFolderFailingToLinkLeavesTheLocalFiles() {
        files.write("settings.toml", "a".toByteArray())
        val switchable=Switchable(folder)
        switchable.available=false

        assertThrows(IOException::class.java) { files.link(switchable, false) }
        assertNull(files.linked)
        assertEquals("a", files.readText("settings.toml"))
        }
    }
