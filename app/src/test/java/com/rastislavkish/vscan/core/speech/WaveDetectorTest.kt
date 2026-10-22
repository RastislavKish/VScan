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

import org.junit.Assert.*
import org.junit.Test

class WaveDetectorTest {

    @Test
    fun briefCoverIsAWave() {
        val detector=WaveDetector()
        detector.onReading(false, 0)

        assertFalse(detector.onReading(true, 1000))
        assertTrue(detector.onReading(false, 1400))
        }

    @Test
    fun longCoverIsNotAWave() {
        val detector=WaveDetector()
        detector.onReading(false, 0)

        detector.onReading(true, 1000)
        assertFalse(detector.onReading(false, 5000))
        }

    @Test
    fun uncoveringAfterEnablingIsNotAWave() {
        // E.g. pulling the phone out of a pocket just as speech starts
        val detector=WaveDetector()

        assertFalse(detector.onReading(true, 0))
        assertFalse(detector.onReading(false, 300))
        }

    @Test
    fun repeatedFarReadingsAreNotWaves() {
        val detector=WaveDetector()
        detector.onReading(false, 0)
        detector.onReading(true, 100)

        assertTrue(detector.onReading(false, 200))
        assertFalse(detector.onReading(false, 300))
        }

    @Test
    fun resetForgetsTheState() {
        val detector=WaveDetector()
        detector.onReading(false, 0)
        detector.onReading(true, 100)

        detector.reset()

        assertFalse(detector.onReading(false, 200))
        }

    }
