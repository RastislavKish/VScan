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
package com.rastislavkish.vscan.core.config

import kotlinx.serialization.*

import com.rastislavkish.vscan.core.speech.SpeechPriority

// Something a process does with the result of an iteration
// Operations run in order after every iteration, each skipped when its condition (a JavaScript expression) is false
@Serializable
sealed class Operation {

    abstract val condition: String?
    }

// Texts are templates
@Serializable
@SerialName("toast")
data class ToastOperation(
    override val condition: String?=null,
    val text: String="{{ lastOutput }}",
    ): Operation()

@Serializable
@SerialName("say")
data class SayOperation(
    override val condition: String?=null,
    val text: String="{{ lastOutput }}",
    val priority: SpeechPriority=SpeechPriority.NORMAL,
    ): Operation()

// Changes a variable of the process memory, which the following operations and iterations see
// The value is a JavaScript expression
@Serializable
@SerialName("set")
data class SetOperation(
    override val condition: String?=null,
    val name: String,
    val value: String,
    ): Operation()

@Serializable
enum class SoundInstance {
    // One instance per sound and process, so playing a sound again restarts it
    @SerialName("process") PROCESS,
    // A new instance on every play, overlapping the previous ones
    @SerialName("new") NEW,
    }

// Sounds are files in the app's assets/sounds folder, named with or without the extension
// A process instance of a looping sound keeps playing when played again, only taking the new volume and pan, so it can serve as a continuous indicator
// Without a sound, the operation is still useful to stop the process' other sounds
@Serializable
@SerialName("play")
data class PlayOperation(
    override val condition: String?=null,
    val sound: String?=null,
    // 0 loops until stopped
    val repeat: Int=1,
    // 0 to 1, full when not set
    val volume: NumberValue?=null,
    // -1 (left) to 1 (right), centered when not set
    val pan: NumberValue?=null,
    val instance: SoundInstance=SoundInstance.PROCESS,
    val stopOthers: Boolean=false,
    ): Operation()

// The device vibrates with one pattern at a time, so a vibration replaces the current one, whichever process started it
@Serializable
@SerialName("vibrate")
data class VibrateOperation(
    override val condition: String?=null,
    // Alternating off and on durations in milliseconds, starting with off
    val pattern: List<Long> = listOf(0, 100),
    // 0 loops until stopped
    val repeat: Int=1,
    ): Operation()

@Serializable
enum class Extraction {
    // The first number in the text
    @SerialName("number") NUMBER,
    // The number at a dot-separated path in a JSON text, e.g. objects.0.x
    @SerialName("json") JSON,
    // The first group of the pattern's first match, or the whole match without groups
    @SerialName("regex") REGEX,
    }

// A number setting of an operation: a constant, a number extracted from lastOutput, or a JavaScript expression
// Exactly one of value, extract and expression is meant to be set, path goes with JSON extraction and pattern with regex extraction
@Serializable
data class NumberValue(
    val value: Double?=null,
    val extract: Extraction?=null,
    val path: String?=null,
    val pattern: String?=null,
    val expression: String?=null,
    )
