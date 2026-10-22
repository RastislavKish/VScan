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
package com.rastislavkish.vscan.core.image

import org.junit.Assert.*
import org.junit.Test

class OrientationTest {

    // A 400x200 stored image, which is 200x400 upright for the dimension-swapping orientations
    private val w=400
    private val h=200

    @Test
    fun displaySizeSwapsForQuarterTurns() {
        assertEquals(400, Orientation.NORMAL.displayWidth(w, h))
        assertEquals(200, Orientation.ROTATE_90.displayWidth(w, h))
        assertEquals(400, Orientation.ROTATE_90.displayHeight(w, h))
        assertEquals(400, Orientation.ROTATE_180.displayWidth(w, h))
        assertEquals(200, Orientation.TRANSVERSE.displayWidth(w, h))
        }

    @Test
    fun fullImageMapsToFullImage() {
        for (orientation in Orientation.entries) {
            val display=ImageRect.ofSize(orientation.displayWidth(w, h), orientation.displayHeight(w, h))
            assertEquals(orientation.name, ImageRect.ofSize(w, h), orientation.toRaw(display, w, h))
            }
        }

    @Test
    fun topLeftCornerMapsToTheRightStoredCorner() {
        // For each orientation, the stored region that ends up in the top-left 10x10 corner of the upright image
        val expected=mapOf(
            Orientation.NORMAL to ImageRect(0, 0, 10, 10),
            Orientation.FLIP_HORIZONTAL to ImageRect(390, 0, 400, 10),
            Orientation.ROTATE_180 to ImageRect(390, 190, 400, 200),
            Orientation.FLIP_VERTICAL to ImageRect(0, 190, 10, 200),
            Orientation.TRANSPOSE to ImageRect(0, 0, 10, 10),
            // Rotating clockwise brings the bottom-left stored corner to the top-left
            Orientation.ROTATE_90 to ImageRect(0, 190, 10, 200),
            Orientation.TRANSVERSE to ImageRect(390, 190, 400, 200),
            Orientation.ROTATE_270 to ImageRect(390, 0, 400, 10),
            )

        for ((orientation, rect) in expected)
        assertEquals(orientation.name, rect, orientation.toRaw(ImageRect(0, 0, 10, 10), w, h))
        }

    @Test
    fun nonSquareRegionsKeepTheirShapeUnderQuarterTurns() {
        // A wide strip along the top of the upright image is a tall strip in the stored one
        val raw=Orientation.ROTATE_90.toRaw(ImageRect(0, 0, 200, 50), w, h)

        assertEquals(ImageRect(0, 0, 50, 200), raw)
        }

    @Test
    fun unknownExifValuesAreNormal() {
        assertEquals(Orientation.ROTATE_90, Orientation.fromExif(6))
        assertEquals(Orientation.NORMAL, Orientation.fromExif(0))
        assertEquals(Orientation.NORMAL, Orientation.fromExif(42))
        }

    @Test
    fun rectIntersection() {
        assertEquals(ImageRect(50, 0, 100, 40), ImageRect(50, -10, 150, 40).intersect(ImageRect.ofSize(100, 100)))
        assertTrue(ImageRect(200, 200, 300, 300).intersect(ImageRect.ofSize(100, 100)).isEmpty())
        }

    }
