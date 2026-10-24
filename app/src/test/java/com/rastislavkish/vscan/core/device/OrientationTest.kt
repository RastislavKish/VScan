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
package com.rastislavkish.vscan.core.device

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.config.Lifetime

class OrientationTest {

    private fun assertOrientation(pitch: Double, roll: Double, orientation: Orientation?) {
        assertEquals(pitch, orientation!!.pitch, 0.01)
        assertEquals(roll, orientation.roll, 0.01)
        }

    @Test
    fun fromGravity() {
        // Upright in portrait, the camera looking at the horizon
        assertOrientation(0.0, 0.0, Orientation.fromGravity(0.0, 9.81, 0.0))
        // Lying on its back, the camera looking at the ground
        assertEquals(-90.0, Orientation.fromGravity(0.0, 0.0, 9.81)!!.pitch, 0.01)
        // Lying on its face, the camera looking at the sky
        assertEquals(90.0, Orientation.fromGravity(0.0, 0.0, -9.81)!!.pitch, 0.01)
        // Tilted back halfway, the camera looking down
        assertOrientation(-45.0, 0.0, Orientation.fromGravity(0.0, 1.0, 1.0))
        // The top turned right and left, and upside down
        assertOrientation(0.0, 90.0, Orientation.fromGravity(-9.81, 0.0, 0.0))
        assertOrientation(0.0, -90.0, Orientation.fromGravity(9.81, 0.0, 0.0))
        assertOrientation(0.0, 180.0, Orientation.fromGravity(0.0, -9.81, 0.0))

        assertNull(Orientation.fromGravity(0.0, 0.0, 0.0))
        }

    @Test
    fun errorIntervals() {
        val lifetime=Lifetime(interval=10.0, errorInterval=1.0, errorBackoff=2.0, maxErrorInterval=5.0)
        assertEquals(listOf(10.0, 1.0, 2.0, 4.0, 5.0), (0..4).map { lifetime.intervalAfter(it) })

        // Without an error interval, failures keep the interval
        assertEquals(10.0, Lifetime(interval=10.0).intervalAfter(3), 0.0)
        }
    }
