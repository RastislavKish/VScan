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
package com.rastislavkish.vscan.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

import kotlinx.coroutines.CompletableDeferred

import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.files.FileTree
import com.rastislavkish.vscan.core.files.VScanFiles
import com.rastislavkish.vscan.core.process.ProcessException

// Plays the sounds of processes, each stream belonging to the process that started it
// A sound is looked up in the config's assets folder, then in the global assets folder of the VScan folder, then among the app's built-in sounds
// It's loaded on first use and kept loaded, until its file changes
// Thread-safe
class SoundPlayer private constructor(context: Context) {

    private class Stream(
        val owner: Int,
        val sound: String,
        val id: Int,
        val looping: Boolean,
        // The process' own instance of the sound, which playing again reuses
        val processInstance: Boolean,
        )

    private val context=context.applicationContext
    private val files=VScanFiles.getInstance(context)
    private val lock=Any()

    // The volume is chosen once, as the pool's attributes can't change
    private val pool: SoundPool=SoundPool.Builder()
    .setMaxStreams(MAX_STREAMS)
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(if (Settings.getInstance(context).speechUsesAccessibilityVolume) AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY else AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build())
    .build()

    // Sample ids by sound key, and the pending loads
    private val samples=mutableMapOf<String, Int>()
    private val loads=mutableMapOf<Int, CompletableDeferred<Boolean>>()
    private val streams=mutableListOf<Stream>()

    private val soundFiles: List<String> by lazy {
        context.assets.list(SOUNDS_FOLDER)?.toList() ?: listOf()
        }

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            val load=synchronized(lock) { loads[sampleId] }
            load?.complete(status==0)
            }
        }

    // repeat 0 loops until stopped
    // Without a sound, it only stops the others if asked to
    // The config's id is where its own assets are
    suspend fun play(owner: Int, configId: String, sound: String?, repeat: Int, left: Float, right: Float, newInstance: Boolean, stopOthers: Boolean) {
        if (repeat<0)
        throw ProcessException("The repeat count can't be negative")

        val path=sound?.let { resolve(configId, it) }
        val looping=repeat==0

        synchronized(lock) {
            if (stopOthers)
            stopWhere { it.owner==owner && !(path!=null && !newInstance && it.processInstance && it.sound==path) }

            if (path==null)
            return

            if (!newInstance) {
                val existing=streams.firstOrNull { it.owner==owner && it.processInstance && it.sound==path }
                if (existing!=null) {
                    if (existing.looping && looping) {
                        pool.setVolume(existing.id, left, right)
                        return
                        }

                    stopWhere { it===existing }
                    }
                }
            }

        val sampleId=load(path!!)

        synchronized(lock) {
            val id=pool.play(sampleId, left, right, 1, if (looping) -1 else repeat-1, 1f)
            if (id==0)
            return

            streams.add(Stream(owner, path, id, looping, !newInstance))
            // Finished streams can't be told apart, so the oldest non-looping ones are forgotten
            while (streams.size>MAX_TRACKED_STREAMS) {
                val index=streams.indexOfFirst { !it.looping }
                streams.removeAt(if (index>=0) index else 0)
                }
            }
        }

    // Stops the owner's looping sounds, or all of them, e.g. when the process was killed
    fun stop(owner: Int, loopingOnly: Boolean) {
        synchronized(lock) {
            stopWhere { it.owner==owner && (it.looping || !loopingOnly) }
            }
        }

    private fun stopWhere(predicate: (Stream) -> Boolean) {
        val stopped=streams.filter(predicate)
        for (stream in stopped)
        pool.stop(stream.id)
        streams.removeAll(stopped)
        }

    // A key identifying the sound's file and its version: file:<path>:<modification time> or asset:<path>
    // Names may omit the extension, and may lead through subfolders, e.g. sounds/beep
    private fun resolve(configId: String, sound: String): String {
        for (folder in listOf("${VScanFiles.CONFIGS}/$configId/${VScanFiles.ASSETS}", VScanFiles.ASSETS)) {
            val path=FileTree.join(folder, sound)
            val directory=path.substringBeforeLast('/', "")
            val name=path.substringAfterLast('/')
            val entry=files.list(directory)?.filter { !it.isDirectory }?.let { entries ->
                entries.firstOrNull { it.name==name } ?: entries.firstOrNull { it.name.substringBeforeLast('.')==name }
                }

            if (entry!=null)
            return "$FILE_PREFIX${files.file(FileTree.join(directory, entry.name)).absolutePath}:${entry.lastModified}"
            }

        val file=soundFiles.firstOrNull { it==sound }
        ?: soundFiles.firstOrNull { it.substringBeforeLast('.')==sound }
        ?: throw ProcessException("Unknown sound $sound")

        return "$ASSET_PREFIX$SOUNDS_FOLDER/$file"
        }

    private suspend fun load(key: String): Int {
        val (sampleId, load)=synchronized(lock) {
            val sampleId=samples.getOrPut(key) {
                if (key.startsWith(FILE_PREFIX))
                pool.load(key.removePrefix(FILE_PREFIX).substringBeforeLast(':'), 1)
                else
                context.assets.openFd(key.removePrefix(ASSET_PREFIX)).use { pool.load(it, 1) }
                }
            Pair(sampleId, loads.getOrPut(sampleId) { CompletableDeferred() })
            }

        if (!load.await()) {
            synchronized(lock) {
                samples.remove(key)
                loads.remove(sampleId)
                }
            throw ProcessException("Couldn't load the sound ${key.substringAfterLast('/').substringBeforeLast(':')}")
            }

        return sampleId
        }

    companion object {

        // The built-in sounds in the app's assets
        const val SOUNDS_FOLDER="sounds"

        private const val FILE_PREFIX="file:"
        private const val ASSET_PREFIX="asset:"

        private const val MAX_STREAMS=16
        private const val MAX_TRACKED_STREAMS=64

        private var instance: SoundPlayer?=null

        @Synchronized
        fun getInstance(context: Context): SoundPlayer {
            if (instance==null) {
                instance=SoundPlayer(context)
                }

            return instance!!
            }
        }
    }
