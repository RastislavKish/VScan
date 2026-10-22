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

import java.io.ByteArrayOutputStream
import java.io.Closeable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

import kotlinx.coroutines.runBlocking

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

import com.rastislavkish.vscan.core.image.ImageData

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageSourcesTest {

    // Records how it's used, and returns a 400x200 image with red, green / blue, white quadrants
    private class FakeSource: ImageSource {

        val image: ImageData
        var lastFlashlight: Boolean?=null
        val streams=RefCounter {}
        val torches=RefCounter {}

        init {
            val bitmap=Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            val paint=Paint()
            for ((rect, color) in listOf(
                floatArrayOf(0f, 0f, 200f, 100f) to Color.RED,
                floatArrayOf(200f, 0f, 400f, 100f) to Color.GREEN,
                floatArrayOf(0f, 100f, 200f, 200f) to Color.BLUE,
                floatArrayOf(200f, 100f, 400f, 200f) to Color.WHITE,
                )) {
                paint.color=color
                canvas.drawRect(rect[0], rect[1], rect[2], rect[3], paint)
                }

            val stream=ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            image=ImageData.fromEncoded(stream.toByteArray())
            }

        override suspend fun capture(flashlight: Boolean): ImageData {
            lastFlashlight=flashlight
            return image
            }
        override suspend fun getVideoFrame(): ImageData = image

        override fun openStream(): Closeable = streams.acquire()
        override fun requestTorch(): Closeable = torches.acquire()
        }

    private fun centerColor(image: ImageData): Int {
        val bitmap=image.decode()
        return bitmap.getPixel(bitmap.width/2, bitmap.height/2)
        }

    @Test
    fun lensZoomsIntoTheRegion() = runBlocking {
        val lens=LensSource(FakeSource(), Lens.zoom(2f, 0.75f, 0.25f))

        val image=lens.capture(false)

        assertEquals(200, image.width)
        assertEquals(100, image.height)
        assertEquals(Color.GREEN, centerColor(image))
        }

    @Test
    fun lensAppliesToVideoFramesToo() = runBlocking {
        val lens=LensSource(FakeSource(), Lens(RelativeRect(0f, 0.5f, 0.5f, 1f)))

        assertEquals(Color.BLUE, centerColor(lens.getVideoFrame()))
        }

    @Test
    fun lensDownscales() = runBlocking {
        val upstream=FakeSource()

        val full=LensSource(upstream, Lens(maxDimension=100)).capture(false)
        val zoomed=LensSource(upstream, Lens.zoom(2f, maxDimension=50)).capture(false)

        assertEquals(100, full.width)
        assertEquals(50, full.height)
        assertEquals(50, zoomed.width)
        }

    @Test
    fun lensWithoutEffectPassesTheImageThrough() = runBlocking {
        val upstream=FakeSource()

        assertSame(upstream.image, LensSource(upstream, Lens()).capture(false))
        assertSame(upstream.image, LensSource(upstream, Lens(maxDimension=1000)).capture(false))
        }

    @Test
    fun lensesChain() = runBlocking {
        // The right half, then its bottom half, which is the white quadrant
        val lens=LensSource(
            LensSource(FakeSource(), Lens(RelativeRect(0.5f, 0f, 1f, 1f))),
            Lens(RelativeRect(0f, 0.5f, 1f, 1f)),
            )

        val image=lens.capture(false)

        assertEquals(200, image.width)
        assertEquals(100, image.height)
        assertEquals(Color.WHITE, centerColor(image))
        }

    @Test
    fun lensPassesFlashlightStreamAndTorchToUpstream() = runBlocking {
        val upstream=FakeSource()
        val lens=LensSource(upstream, Lens.zoom(2f))

        lens.capture(true)
        val stream=lens.openStream()
        val torch=lens.requestTorch()

        assertEquals(true, upstream.lastFlashlight)
        assertTrue(upstream.streams.isActive)
        assertTrue(upstream.torches.isActive)

        stream.close()
        torch.close()
        assertFalse(upstream.streams.isActive)
        assertFalse(upstream.torches.isActive)
        }

    @Test
    fun staticSourceAlwaysReturnsItsImage() = runBlocking {
        val image=FakeSource().image
        val source=StaticImageSource(image)

        assertSame(image, source.capture(true))
        assertSame(image, source.getVideoFrame())
        source.requestTorch().close()
        }

    @Test
    fun aliasFollowsItsTarget() = runBlocking {
        val first=FakeSource()
        val second=FakeSource()
        var target: ImageSource=first
        val alias=AliasSource { target }

        assertSame(first.image, alias.capture(false))

        target=second
        assertSame(second.image, alias.capture(false))
        }

    @Test
    fun aliasHandlesStayWithTheirSource() = runBlocking {
        val first=FakeSource()
        var target: ImageSource=first
        val alias=AliasSource { target }

        val torch=alias.requestTorch()
        target=FakeSource()
        torch.close()

        assertFalse(first.torches.isActive)
        }

    }
