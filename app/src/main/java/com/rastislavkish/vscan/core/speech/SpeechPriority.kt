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
package com.rastislavkish.vscan.core.speech

import kotlinx.serialization.*

@Serializable
enum class SpeechPriority {

    // Queues, and is dropped whenever a high or critical message appears
    @SerialName("low") LOW,
    // Queues, and is paused while higher priority messages are spoken, then resumed
    @SerialName("normal") NORMAL,
    // Interrupts everything up to its own priority, can be stopped by the user
    @SerialName("high") HIGH,
    // Pauses everything else, queues behind other critical messages, and can't be stopped by the user
    @SerialName("critical") CRITICAL,
    }
