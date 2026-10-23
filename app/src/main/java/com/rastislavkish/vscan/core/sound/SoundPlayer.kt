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
import com.rastislavkish.vscan.core.process.ProcessException

// Plays the sounds of processes, each stream belonging to the process that started it
// Sounds are files in assets/sounds, loaded on first use and kept loaded
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
    private val lock=Any()

    // The volume is chosen once, as the pool's attributes can't change
    private val pool: SoundPool=SoundPool.Builder()
    .setMaxStreams(MAX_STREAMS)
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(if (Settings.getInstance(context).speechUsesAccessibilityVolume) AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY else AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build())
    .build()

    // Sample ids by asset path, and the pending loads
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
    suspend fun play(owner: Int, sound: String?, repeat: Int, left: Float, right: Float, newInstance: Boolean, stopOthers: Boolean) {
        if (repeat<0)
        throw ProcessException("The repeat count can't be negative")

        val path=sound?.let(::resolve)
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

    private fun resolve(sound: String): String {
        val file=soundFiles.firstOrNull { it==sound }
        ?: soundFiles.firstOrNull { it.substringBeforeLast('.')==sound }
        ?: throw ProcessException("Unknown sound $sound")

        return "$SOUNDS_FOLDER/$file"
        }

    private suspend fun load(path: String): Int {
        val (sampleId, load)=synchronized(lock) {
            val sampleId=samples.getOrPut(path) {
                context.assets.openFd(path).use { pool.load(it, 1) }
                }
            Pair(sampleId, loads.getOrPut(sampleId) { CompletableDeferred() })
            }

        if (!load.await()) {
            synchronized(lock) {
                samples.remove(path)
                loads.remove(sampleId)
                }
            throw ProcessException("Couldn't load the sound $path")
            }

        return sampleId
        }

    companion object {

        const val SOUNDS_FOLDER="sounds"

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
