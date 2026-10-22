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

// Anything VScan can get images from, e.g. the smartphone camera, an IP camera, a stored image or a lens over another source
// Sources lacking a capability emulate it where reasonable, e.g. a stream-only camera captures by taking a stream frame
interface ImageSource {

    // Takes a photo, as a camera shutter would, with a single flash if requested and available
    suspend fun capture(flashlight: Boolean): ImageData

    // Returns the latest frame of the live stream, a low-latency image for repeated analysis
    // If the stream isn't open, the source opens it and keeps it open for a while after the last call
    suspend fun getVideoFrame(): ImageData

    // Keeps the live stream running until the returned handle is closed
    fun openStream(): Closeable

    // Keeps the flashlight on until the returned handle is closed
    fun requestTorch(): Closeable
    }
