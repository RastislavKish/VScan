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

// Recognizes a hand briefly waved over the proximity sensor
// A sensor covered for longer, e.g. in a pocket, doesn't count, and neither does one already covered when enabled
// Android may or may not report the current state on enabling the sensor, so a covered state is taken as preexisting only if reported right away
class WaveDetector(
    private val maxCoveredMillis: Long=1000,
    private val initialReadingMillis: Long=250,
    ) {

    private var enabledAt=0L
    private var coveredSince: Long?=null

    // Returns true when the reading completes a wave
    fun onReading(near: Boolean, timeMillis: Long): Boolean {
        if (near) {
            coveredSince=if (timeMillis-enabledAt<initialReadingMillis) null else timeMillis
            return false
            }

        val since=coveredSince ?: return false
        coveredSince=null

        return timeMillis-since<=maxCoveredMillis
        }

    // To be called when the sensor gets enabled
    fun reset(timeMillis: Long) {
        enabledAt=timeMillis
        coveredSince=null
        }
    }
