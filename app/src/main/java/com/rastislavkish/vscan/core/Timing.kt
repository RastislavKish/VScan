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
package com.rastislavkish.vscan.core

import android.os.SystemClock
import android.util.Log

// Logs durations of the scanning pipeline phases, viewable with: adb logcat -s VScanTiming
object Timing {

    const val TAG="VScanTiming"

    fun now(): Long = SystemClock.elapsedRealtime()

    fun log(message: String) {
        Log.d(TAG, message)
        }
    fun logSince(label: String, start: Long) {
        log("$label: ${now()-start} ms")
        }

    inline fun <T> measure(label: String, block: () -> T): T {
        val start=now()
        try {
            return block()
            }
        finally {
            logSince(label, start)
            }
        }
    }
