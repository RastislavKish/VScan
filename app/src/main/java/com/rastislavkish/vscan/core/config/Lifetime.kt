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

@Serializable
enum class IterationMode {
    // An iteration starts once the previous one has finished
    @SerialName("sync") SYNC,
    // Iterations start every interval, their analyses running at the same time, the results committed in order
    @SerialName("async") ASYNC,
    }

// How long and how often a process runs
// A limit of 0 means no limit, reaching one ends the process
@Serializable
data class Lifetime(
    // Seconds between iterations, 0 runs a single iteration
    val interval: Double=0.0,
    val mode: IterationMode=IterationMode.SYNC,
    // The most analyses running at once in the async mode, further iterations waiting for one to finish
    val maxInFlight: Int=3,
    // Seconds an analysis may take before its iteration fails, 0 = no limit
    val analysisTimeout: Double=60.0,
    // Seconds until the next iteration after a failed one, 0 = the interval
    val errorInterval: Double=0.0,
    // Multiplies the error interval for each further failure in a row
    val errorBackoff: Double=1.0,
    // Seconds the error interval grows up to, 0 = no limit
    val maxErrorInterval: Double=0.0,
    val capture: CaptureMode=CaptureMode.AUTO,
    // The torch while looping, the flash for photos
    val flashlight: FlashlightMode=FlashlightMode.DEFAULT,
    // A JavaScript expression holding the iterations while it's false, checked continually
    val iterationCondition: String?=null,
    val maxIterations: Int=0,
    // Seconds
    val maxDuration: Double=0.0,
    val maxTokens: Int=0,
    ) {

    val isLooping: Boolean
    get() = interval>0.0

    // Seconds until the next iteration after the given number of failed ones in a row
    fun intervalAfter(failures: Int): Double {
        if (failures==0)
        return interval

        val first=if (errorInterval>0.0) errorInterval else interval
        var result=first*Math.pow(errorBackoff, (failures-1).toDouble())
        if (maxErrorInterval>0.0)
        result=minOf(result, maxErrorInterval)
        return result
        }
    }
