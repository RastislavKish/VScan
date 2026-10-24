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
package com.rastislavkish.vscan.core.process

import android.content.Context
import android.widget.Toast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.PlayOperation
import com.rastislavkish.vscan.core.config.SayOperation
import com.rastislavkish.vscan.core.config.SetOperation
import com.rastislavkish.vscan.core.config.SoundInstance
import com.rastislavkish.vscan.core.config.ToastOperation
import com.rastislavkish.vscan.core.config.VibrateOperation
import com.rastislavkish.vscan.core.script.ScriptScope
import com.rastislavkish.vscan.core.sound.SoundPlayer
import com.rastislavkish.vscan.core.sound.VibrationSystem
import com.rastislavkish.vscan.core.speech.SpeechSystem
import com.rastislavkish.vscan.core.template.Template

class AndroidOperationExecutor(
    context: Context,
    ): OperationExecutor {

    private val context=context.applicationContext
    private val speechSystem=SpeechSystem.getInstance(this.context)
    private val soundPlayer=SoundPlayer.getInstance(this.context)
    private val vibrationSystem=VibrationSystem.getInstance(this.context)

    // Operations run every iteration, so their templates are parsed once
    private val templates=object: LinkedHashMap<String, Template>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Template>): Boolean = size>TEMPLATE_CACHE_SIZE
        }

    override suspend fun execute(operation: Operation, scope: ScriptScope, process: Process) {
        when (operation) {
            is ToastOperation -> {
                val text=render(operation.text, scope)
                if (text.isNotBlank())
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    }
                }
            is SayOperation -> {
                val text=render(operation.text, scope)
                if (text.isNotBlank())
                speechSystem.say(text, operation.priority)
                }
            is PlayOperation -> {
                val lastOutput=scope.evaluate("lastOutput") as? String
                val volume=operation.volume?.let { OperationValues.evaluate(it, "volume", lastOutput, scope) } ?: 1.0
                val pan=operation.pan?.let { OperationValues.evaluate(it, "pan", lastOutput, scope) } ?: 0.0
                val (left, right)=OperationValues.stereoVolumes(volume, pan)

                soundPlayer.play(process.pid, process.config.id, operation.sound, operation.repeat, left, right, operation.instance==SoundInstance.NEW, operation.stopOthers)
                }
            is VibrateOperation -> {
                val (timings, repeatIndex)=OperationValues.waveform(operation.pattern, operation.repeat)
                vibrationSystem.vibrate(process.pid, timings, repeatIndex)
                }
            // Carried out by the process itself
            is SetOperation -> {}
            }
        }

    // A finished process lets its last sounds and vibrations play out, only looping ones are stopped
    override fun release(process: Process) {
        val loopingOnly=process.status!=ProcessStatus.KILLED
        soundPlayer.stop(process.pid, loopingOnly)
        vibrationSystem.stop(process.pid, loopingOnly)
        }

    private fun render(source: String, scope: ScriptScope): String {
        val template=synchronized(templates) {
            templates.getOrPut(source) { Template.parse(source) }
            }

        return template.renderText(scope)
        }

    companion object {

        private const val TEMPLATE_CACHE_SIZE=64
        }
    }
