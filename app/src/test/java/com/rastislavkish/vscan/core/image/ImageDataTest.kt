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
import java.io.File
import java.util.Base64

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageDataTest {

    @get:Rule
    val temporaryFolder=TemporaryFolder()

    // A 400x200 image with differently colored quadrants: red, green on top, blue, white at the bottom
    private fun quadrants(): Bitmap {
        val bitmap=Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint()

        for ((rect, color) in listOf(
            listOf(0, 0, 200, 100) to Color.RED,
            listOf(200, 0, 400, 100) to Color.GREEN,
            listOf(0, 100, 200, 200) to Color.BLUE,
            listOf(200, 100, 400, 200) to Color.WHITE,
            )) {
            paint.color=color
            canvas.drawRect(rect[0].toFloat(), rect[1].toFloat(), rect[2].toFloat(), rect[3].toFloat(), paint)
            }

        return bitmap
        }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat): ByteArray {
        val stream=ByteArrayOutputStream()
        bitmap.compress(format, 95, stream)
        return stream.toByteArray()
        }

    private fun withExifOrientation(jpeg: ByteArray, orientation: Orientation): ByteArray {
        val file=File(temporaryFolder.root, "image.jpg")
        file.writeBytes(jpeg)

        val exif=ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.exifValue.toString())
        exif.saveAttributes()

        return file.readBytes()
        }

    // Asserts the dominant color of the center pixel, tolerating JPEG artifacts
    private fun assertCenterColor(expected: Int, bitmap: Bitmap) {
        val actual=bitmap.getPixel(bitmap.width/2, bitmap.height/2)

        for (shift in listOf(16, 8, 0)) {
            val e=(expected shr shift) and 0xFF
            val a=(actual shr shift) and 0xFF
            assertTrue("Expected #${Integer.toHexString(expected)}, got #${Integer.toHexString(actual)}", Math.abs(e-a)<40)
            }
        }

    @Test
    fun sendableOriginalsPassThroughUntouched() {
        val png=encode(quadrants(), Bitmap.CompressFormat.PNG)
        val image=ImageData.fromEncoded(png)

        assertSame(png, image.encoded)
        assertEquals(EncodedFormat.PNG, image.encodedFormat)
        assertArrayEquals(png, Base64.getDecoder().decode(image.base64))
        }

    @Test
    fun unsendableOriginalsAreReencodedToJpeg() {
        // A 2x2 24-bit BMP, which Android decodes but providers don't accept
        val bmp=ByteArray(70)
        val header=intArrayOf(
            0x42, 0x4D, 70, 0, 0, 0, 0, 0, 0, 0, 54, 0, 0, 0,
            40, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 1, 0, 24, 0,
            )
        header.forEachIndexed { i, value -> bmp[i]=value.toByte() }

        val image=ImageData.fromEncoded(bmp)

        assertEquals(EncodedFormat.UNKNOWN, image.format)
        assertEquals(EncodedFormat.JPEG, image.encodedFormat)
        assertEquals(EncodedFormat.JPEG, EncodedFormat.detect(image.encoded))
        }

    @Test
    fun bitmapImagesAreEncodedAsJpeg() {
        val image=ImageData.fromBitmap(quadrants())

        assertEquals(EncodedFormat.JPEG, image.encodedFormat)
        assertEquals(EncodedFormat.JPEG, EncodedFormat.detect(image.encoded))
        assertEquals(400, image.width)
        }

    @Test
    fun reportsDimensionsWithoutDecoding() {
        val image=ImageData.fromEncoded(encode(quadrants(), Bitmap.CompressFormat.PNG))

        assertEquals(400, image.width)
        assertEquals(200, image.height)
        }

    @Test
    fun decodeDownscalesToMaxDimension() {
        val image=ImageData.fromEncoded(encode(quadrants(), Bitmap.CompressFormat.PNG))

        val full=image.decode()
        val small=image.decode(100)

        assertEquals(400, full.width)
        assertEquals(100, small.width)
        assertEquals(50, small.height)
        }

    @Test
    fun cropDecodesOnlyTheRegion() {
        val image=ImageData.fromEncoded(encode(quadrants(), Bitmap.CompressFormat.PNG))

        val topRight=image.crop(ImageRect(200, 0, 400, 100))
        val bitmap=topRight.decode()

        assertEquals(200, topRight.width)
        assertEquals(100, topRight.height)
        assertCenterColor(Color.GREEN, bitmap)
        }

    @Test
    fun cropCanDownscale() {
        val image=ImageData.fromEncoded(encode(quadrants(), Bitmap.CompressFormat.PNG))

        val crop=image.crop(ImageRect(0, 100, 200, 200), maxDimension=50)

        assertEquals(50, crop.width)
        assertEquals(25, crop.height)
        assertCenterColor(Color.BLUE, crop.decode())
        }

    @Test
    fun cropIsClampedToTheImage() {
        val image=ImageData.fromEncoded(encode(quadrants(), Bitmap.CompressFormat.PNG))

        val crop=image.crop(ImageRect(300, 150, 500, 300))

        assertEquals(100, crop.width)
        assertEquals(50, crop.height)
        }

    @Test(expected=IllegalArgumentException::class)
    fun cropOutsideTheImageFails() {
        ImageData.fromEncoded(encode(quadrants(), Bitmap.CompressFormat.PNG)).crop(ImageRect(500, 0, 600, 100))
        }

    @Test
    fun scaledKeepsImagesThatAlreadyFit() {
        val image=ImageData.fromEncoded(encode(quadrants(), Bitmap.CompressFormat.PNG))

        assertSame(image, image.scaled(400))
        assertEquals(200, image.scaled(200).width)
        }

    @Test
    fun exifOrientationIsAppliedToSizeDecodeAndCrop() {
        val jpeg=withExifOrientation(encode(quadrants(), Bitmap.CompressFormat.JPEG), Orientation.ROTATE_90)
        val image=ImageData.fromEncoded(jpeg)

        assertEquals(Orientation.ROTATE_90, image.orientation)
        assertEquals(200, image.width)
        assertEquals(400, image.height)

        val decoded=image.decode()
        assertEquals(200, decoded.width)
        assertEquals(400, decoded.height)

        // Turned clockwise, the stored bottom-left (blue) quadrant becomes the upright top-left one
        assertCenterColor(Color.BLUE, image.crop(ImageRect(0, 0, 100, 200)).decode())
        assertCenterColor(Color.RED, image.crop(ImageRect(100, 0, 200, 200)).decode())
        assertCenterColor(Color.WHITE, image.crop(ImageRect(0, 200, 100, 400)).decode())
        }

    @Test
    fun exifOrientationIsKeptWhenPassingThrough() {
        val jpeg=withExifOrientation(encode(quadrants(), Bitmap.CompressFormat.JPEG), Orientation.ROTATE_90)

        assertSame(jpeg, ImageData.fromEncoded(jpeg).encoded)
        }

    @Test
    fun sampleSizeNeverUndershootsTheTarget() {
        assertEquals(1, ImageData.sampleSize(1.0f))
        assertEquals(1, ImageData.sampleSize(0.6f))
        assertEquals(2, ImageData.sampleSize(0.5f))
        assertEquals(2, ImageData.sampleSize(0.3f))
        assertEquals(4, ImageData.sampleSize(0.25f))
        }

    }
