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
package com.rastislavkish.vscan.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// Vibrates for processes; the device has one vibration at a time, so the last process to vibrate owns it
class VibrationSystem private constructor(context: Context) {

    private val vibrator: Vibrator=if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)
    context.getSystemService(VibratorManager::class.java).defaultVibrator
    else
    context.getSystemService(Vibrator::class.java)

    private val lock=Any()
    private var owner: Int?=null
    private var looping=false

    // timings alternate off and on durations, repeatIndex is where to loop from, -1 for no looping
    fun vibrate(owner: Int, timings: LongArray, repeatIndex: Int) {
        val effect=VibrationEffect.createWaveform(timings, repeatIndex)

        synchronized(lock) {
            this.owner=owner
            looping=repeatIndex>=0

            // Accessibility vibrations aren't silenced along with touch feedback
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ACCESSIBILITY))
            else
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build())
            }
        }

    // Stops the owner's looping vibration, or any of its vibrations, e.g. when the process was killed
    fun stop(owner: Int, loopingOnly: Boolean) {
        synchronized(lock) {
            if (this.owner!=owner || (loopingOnly && !looping))
            return

            vibrator.cancel()
            this.owner=null
            }
        }

    fun stopAll() {
        synchronized(lock) {
            vibrator.cancel()
            owner=null
            }
        }

    companion object {

        private var instance: VibrationSystem?=null

        @Synchronized
        fun getInstance(context: Context): VibrationSystem {
            if (instance==null) {
                instance=VibrationSystem(context.applicationContext)
                }

            return instance!!
            }
        }
    }
