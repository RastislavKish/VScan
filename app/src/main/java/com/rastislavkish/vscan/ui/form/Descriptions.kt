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
package com.rastislavkish.vscan.ui.form

import kotlinx.serialization.json.JsonPrimitive

import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.config.PlayOperation
import com.rastislavkish.vscan.core.config.SayOperation
import com.rastislavkish.vscan.core.config.SetOperation
import com.rastislavkish.vscan.core.config.SoundInstance
import com.rastislavkish.vscan.core.config.ToastOperation
import com.rastislavkish.vscan.core.config.VibrateOperation
import com.rastislavkish.vscan.core.imagesource.Crop
import com.rastislavkish.vscan.core.imagesource.Downscale
import com.rastislavkish.vscan.core.imagesource.LensOperation
import com.rastislavkish.vscan.core.imagesource.Zoom
import com.rastislavkish.vscan.core.speech.SpeechPriority

// One-line descriptions of the list items of the config editor
object Descriptions {

    private const val MAX_TEXT_LENGTH=100

    fun summarize(text: String, maxLength: Int=MAX_TEXT_LENGTH): String {
        val line=text.replace("\n", " ")
        return if (line.length<=maxLength) line else line.take(maxLength)+"..."
        }

    fun typeName(type: ParameterType): String {
        return when (type) {
            ParameterType.STRING -> "String"
            ParameterType.INT -> "Int"
            ParameterType.DOUBLE -> "Double"
            ParameterType.BOOL -> "Bool"
            ParameterType.IMAGE -> "Image"
            ParameterType.IMAGE_SOURCE -> "ImageSource"
            ParameterType.ANY -> "Any"
            }
        }

    fun parameter(parameter: Parameter): String {
        val parts=mutableListOf<String>()
        parts.add(if (parameter.short!=null) "${parameter.name} (-${parameter.short})" else parameter.name)
        parts.add(typeName(parameter.type))
        if (parameter.positional)
        parts.add(if (parameter.rest) "positional, the rest of the line" else "positional")
        parts.add(if (parameter.default!=null) "default ${summarize(parameter.default)}" else "required")
        return parts.joinToString(", ")
        }

    fun memoryVariable(name: String, value: JsonPrimitive): String = "$name = ${summarize(Values.formatMemoryValue(value))}"

    fun operation(operation: Operation): String {
        val parts=mutableListOf<String>()
        when (operation) {
            is ToastOperation -> parts.add("Toast ${summarize(operation.text)}")
            is SayOperation -> {
                parts.add("Say ${summarize(operation.text)}")
                if (operation.priority!=SpeechPriority.NORMAL)
                parts.add("${priorityName(operation.priority)} priority")
                }
            is PlayOperation -> {
                parts.add(if (operation.sound!=null) "Play ${operation.sound}" else "Play nothing")
                parts.addAll(repetition(operation.repeat))
                if (operation.instance==SoundInstance.NEW)
                parts.add("new instance")
                if (operation.stopOthers)
                parts.add("stopping the other sounds")
                }
            is VibrateOperation -> {
                parts.add("Vibrate ${Values.formatPattern(operation.pattern)}")
                parts.addAll(repetition(operation.repeat))
                }
            is SetOperation -> parts.add("Set ${operation.name} to ${summarize(operation.value)}")
            }

        if (operation.condition!=null)
        parts.add("if ${summarize(operation.condition!!)}")
        return parts.joinToString(", ")
        }

    fun lensOperation(operation: LensOperation): String {
        return when (operation) {
            is Crop -> {
                val region=operation.region
                "Crop from ${number(region.left)}, ${number(region.top)} to ${number(region.right)}, ${number(region.bottom)}"
                }
            is Zoom -> "Zoom ${number(operation.zoom)} times around ${number(operation.centerX)}, ${number(operation.centerY)}"
            is Downscale -> "Downscale to ${operation.maxDimension} pixels"
            }
        }

    fun priorityName(priority: SpeechPriority): String {
        return when (priority) {
            SpeechPriority.LOW -> "Low"
            SpeechPriority.NORMAL -> "Normal"
            SpeechPriority.HIGH -> "High"
            SpeechPriority.CRITICAL -> "Critical"
            }
        }

    private fun repetition(repeat: Int): List<String> {
        return when {
            repeat==0 -> listOf("looping")
            repeat>1 -> listOf("$repeat times")
            else -> listOf()
            }
        }

    private fun number(value: Float): String = Values.formatNumber(value.toString().toDouble())
    }
