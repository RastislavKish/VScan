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

import java.io.ByteArrayOutputStream

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

// Covers the BitmapRegionDecoder path, which Robolectric doesn't implement
@RunWith(AndroidJUnit4::class)
class ImageDataInstrumentedTest {

    // A 4000x3000 JPEG, the size of a typical 12 MP photo, with a green top-right quadrant and red elsewhere
    private fun largeJpeg(): ByteArray {
        val bitmap=Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint()

        canvas.drawColor(Color.RED)
        paint.color=Color.GREEN
        canvas.drawRect(2000f, 0f, 4000f, 1500f, paint)

        val stream=ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()

        return stream.toByteArray()
        }

    @Test
    fun cropsRegionOfLargeJpegAtFullResolution() {
        val image=ImageData.fromEncoded(largeJpeg())

        val crop=image.crop(ImageRect(2500, 500, 3500, 1000))
        val bitmap=crop.decode()

        assertEquals(1000, bitmap.width)
        assertEquals(500, bitmap.height)

        val pixel=bitmap.getPixel(500, 250)
        assertTrue(Color.green(pixel)>200 && Color.red(pixel)<60)
        }

    @Test
    fun cropsAndDownscalesRegion() {
        val image=ImageData.fromEncoded(largeJpeg())

        val crop=image.crop(ImageRect(0, 0, 4000, 1500), maxDimension=1000)

        assertEquals(1000, crop.width)
        assertEquals(375, crop.height)
        }

    }
