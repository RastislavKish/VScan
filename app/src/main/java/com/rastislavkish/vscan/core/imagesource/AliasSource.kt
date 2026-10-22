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

// Refers to another source, resolved anew for every request, so retargeting the alias affects its users immediately
// Stream and torch handles stay with the source that was current when they were requested
class AliasSource(
    private val resolve: () -> ImageSource,
    ): ImageSource {

    override suspend fun capture(flashlight: Boolean): ImageData = resolve().capture(flashlight)
    override suspend fun getVideoFrame(): ImageData = resolve().getVideoFrame()

    override fun openStream(): Closeable = resolve().openStream()
    override fun requestTorch(): Closeable = resolve().requestTorch()
    }
