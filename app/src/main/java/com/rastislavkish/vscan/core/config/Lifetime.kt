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

import com.rastislavkish.vscan.core.FlashlightMode

@Serializable
enum class CaptureMode {
    // A photo when single-shot, a stream frame when looping
    @SerialName("auto") AUTO,
    // Always a full photo, e.g. for reading a departure board in a loop
    @SerialName("photo") PHOTO,
    // Always a frame of the live stream
    @SerialName("frame") FRAME,
    }

// How long and how often a process runs
// A limit of 0 means no limit
@Serializable
data class Lifetime(
    // Seconds between iterations, 0 runs a single iteration
    val interval: Double=0.0,
    val capture: CaptureMode=CaptureMode.AUTO,
    // The torch while looping, the flash for photos
    val flashlight: FlashlightMode=FlashlightMode.DEFAULT,
    // A JavaScript expression deciding whether to run the next iteration
    val iterationCondition: String?=null,
    val maxIterations: Int=0,
    // Seconds
    val maxDuration: Double=0.0,
    val maxTokens: Int=0,
    ) {

    val isLooping: Boolean
    get() = interval>0.0
    }
