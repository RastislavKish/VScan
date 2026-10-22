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

import com.rastislavkish.vscan.core.image.ImageData

// A single stored image acting as a source, e.g. a shared image or one saved in the memory
class StaticImageSource(
    val image: ImageData,
    ): ImageSource {

    override suspend fun capture(flashlight: Boolean): ImageData = image
    override suspend fun getVideoFrame(): ImageData = image

    override fun openStream(): Closeable = NoOpHandle
    override fun requestTorch(): Closeable = NoOpHandle
    }
