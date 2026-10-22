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

import java.io.Closeable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.rastislavkish.vscan.core.image.ImageData

// Applies a lens to the images of another source, which can itself be a lens
class LensSource(
    val upstream: ImageSource,
    val lens: Lens,
    ): ImageSource {

    override suspend fun capture(flashlight: Boolean): ImageData = apply(upstream.capture(flashlight))
    override suspend fun getVideoFrame(): ImageData = apply(upstream.getVideoFrame())

    override fun openStream(): Closeable = upstream.openStream()
    override fun requestTorch(): Closeable = upstream.requestTorch()

    // Decoding may take a while for full-resolution photos
    private suspend fun apply(image: ImageData): ImageData = withContext(Dispatchers.Default) {
        if (lens.region.isFull) {
            if (lens.maxDimension!=null)
            image.scaled(lens.maxDimension)
            else
            image
            }
        else
        image.crop(lens.region.toPixels(image.width, image.height), lens.maxDimension)
        }
    }
