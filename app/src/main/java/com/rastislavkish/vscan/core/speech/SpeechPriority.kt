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

enum class SpeechPriority {

    // Queues, and is dropped whenever a high or critical message appears
    LOW,
    // Queues, and is paused while higher priority messages are spoken, then resumed
    NORMAL,
    // Interrupts everything up to its own priority, can be stopped by the user
    HIGH,
    // Pauses everything else, queues behind other critical messages, and can't be stopped by the user
    CRITICAL,
    }
