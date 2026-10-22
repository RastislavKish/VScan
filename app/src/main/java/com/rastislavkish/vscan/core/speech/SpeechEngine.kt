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

// A speech synthesizer speaking one utterance at a time
// Implementations report the end of each utterance, whether finished or failed, through the callback given on creation
interface SpeechEngine {

    // Starts speaking the text, replacing whatever is being spoken
    fun speak(text: String, utteranceId: String)

    fun stop()
    }
