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
package com.rastislavkish.vscan.core.imagesource

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.image.ImageRect

class LensTest {

    @Test
    fun relativeRectMapsToPixels() {
        assertEquals(ImageRect(100, 75, 300, 225), RelativeRect(0.25f, 0.25f, 0.75f, 0.75f).toPixels(400, 300))
        assertEquals(ImageRect(0, 0, 400, 300), RelativeRect.FULL.toPixels(400, 300))
        }

    @Test
    fun tinyRegionsKeepAtLeastOnePixel() {
        val rect=RelativeRect(0.5f, 0.5f, 0.5001f, 0.5001f).toPixels(100, 100)

        assertEquals(1, rect.width)
        assertEquals(1, rect.height)
        }

    @Test(expected=IllegalArgumentException::class)
    fun regionsMustLieInTheImage() {
        RelativeRect(-0.1f, 0f, 0.5f, 0.5f)
        }

    @Test(expected=IllegalArgumentException::class)
    fun regionsMustNotBeEmpty() {
        RelativeRect(0.5f, 0f, 0.5f, 1f)
        }

    @Test
    fun zoomCutsAroundTheCenter() {
        assertEquals(RelativeRect(0.25f, 0.25f, 0.75f, 0.75f), Lens.zoom(2f).region)
        assertEquals(RelativeRect(0.5f, 0f, 1f, 0.5f), Lens.zoom(2f, 0.75f, 0.25f).region)
        }

    @Test
    fun zoomShiftsInsteadOfReachingOverTheEdge() {
        val region=Lens.zoom(4f, 0.95f, 0.05f).region

        assertEquals(0.75f, region.left, 0.0001f)
        assertEquals(1f, region.right, 0.0001f)
        assertEquals(0f, region.top, 0.0001f)
        assertEquals(0.25f, region.bottom, 0.0001f)
        }

    @Test
    fun zoomOfOneIsTheFullImage() {
        assertTrue(Lens.zoom(1f).region.isFull)
        }

    @Test(expected=IllegalArgumentException::class)
    fun zoomCantMagnifyLessThanOnce() {
        Lens.zoom(0.5f)
        }

    }
