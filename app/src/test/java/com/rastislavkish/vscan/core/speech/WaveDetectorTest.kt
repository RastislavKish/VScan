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

    // A detector enabled at time 0
    private fun enabled(): WaveDetector {
        val detector=WaveDetector()
        detector.reset(0)
        return detector
        }

    @Test
    fun briefCoverIsAWave() {
        val detector=enabled()
        detector.onReading(false, 10)

        assertFalse(detector.onReading(true, 1000))
        assertTrue(detector.onReading(false, 1400))
        }

    @Test
    fun longCoverIsNotAWave() {
        val detector=enabled()

        detector.onReading(true, 1000)
        assertFalse(detector.onReading(false, 5000))
        }

    @Test
    fun coverWithoutInitialReadingIsAWave() {
        // Android doesn't always report the current state when the sensor is enabled again
        val detector=enabled()

        assertFalse(detector.onReading(true, 2000))
        assertTrue(detector.onReading(false, 2300))
        }

    @Test
    fun wavesAfterReenablingAreDetected() {
        val detector=enabled()
        detector.onReading(true, 1000)
        assertTrue(detector.onReading(false, 1200))

        // The next message enables the sensor again, which reports nothing until the next wave
        detector.reset(5000)

        assertFalse(detector.onReading(true, 7000))
        assertTrue(detector.onReading(false, 7300))
        }

    @Test
    fun alreadyCoveredSensorIsNotAWave() {
        // E.g. pulling the phone out of a pocket just as speech starts
        val detector=enabled()

        assertFalse(detector.onReading(true, 20))
        assertFalse(detector.onReading(false, 300))
        }

    @Test
    fun repeatedFarReadingsAreNotWaves() {
        val detector=enabled()
        detector.onReading(true, 1000)

        assertTrue(detector.onReading(false, 1100))
        assertFalse(detector.onReading(false, 1200))
        }

    @Test
    fun resetForgetsTheCover() {
        val detector=enabled()
        detector.onReading(true, 1000)

        detector.reset(1100)

        assertFalse(detector.onReading(false, 1200))
        }

    }
