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

import kotlinx.serialization.*

import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.image.ImageRect

// A rectangle in coordinates relative to the image size, 0 being the left / top edge and 1 the right / bottom one
@Serializable
data class RelativeRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    ) {

    init {
        require(left>=0f && top>=0f && right<=1f && bottom<=1f) { "$this exceeds the image" }
        require(left<right && top<bottom) { "$this is empty" }
        }

    val isFull: Boolean
    get() = left==0f && top==0f && right==1f && bottom==1f

    fun toPixels(width: Int, height: Int): ImageRect {
        val pixelLeft=Math.round(left*width)
        val pixelTop=Math.round(top*height)

        // Never round a non-empty region down to nothing
        return ImageRect(
            pixelLeft,
            pixelTop,
            maxOf(Math.round(right*width), pixelLeft+1),
            maxOf(Math.round(bottom*height), pixelTop+1),
            )
        }

    companion object {

        val FULL=RelativeRect(0f, 0f, 1f, 1f)
        }
    }

// A single image transformation of a lens
// Operations returning their input unchanged keep the original encoded image, which then passes through without re-encoding
// Geometric operations are cheap on encoded images, decoding only the needed region or a subsampled image, so they're best placed first
@Serializable
sealed class LensOperation {

    abstract fun apply(image: ImageData): ImageData
    }

// Cuts out a region at full resolution
@Serializable
@SerialName("crop")
data class Crop(
    val region: RelativeRect,
    ): LensOperation() {

    override fun apply(image: ImageData): ImageData {
        if (region.isFull)
        return image

        return image.crop(region.toPixels(image.width, image.height))
        }
    }

// Magnifies the image zoom times around the given relative point, by cropping, so without inventing any detail
// The region is shifted rather than cut where it would reach over the image edge
@Serializable
@SerialName("zoom")
data class Zoom(
    val zoom: Float,
    val centerX: Float=0.5f,
    val centerY: Float=0.5f,
    ): LensOperation() {

    init {
        require(zoom>=1f) { "Zoom must be at least 1" }
        }

    val region: RelativeRect
    get() {
        val size=1f/zoom
        val left=(centerX-size/2).coerceIn(0f, 1f-size)
        val top=(centerY-size/2).coerceIn(0f, 1f-size)

        return RelativeRect(left, top, minOf(left+size, 1f), minOf(top+size, 1f))
        }

    override fun apply(image: ImageData): ImageData = Crop(region).apply(image)
    }

// Shrinks the image so its longer side doesn't exceed maxDimension, never enlarging it
@Serializable
@SerialName("downscale")
data class Downscale(
    val maxDimension: Int,
    ): LensOperation() {

    init {
        require(maxDimension>0) { "The maximum dimension must be positive" }
        }

    override fun apply(image: ImageData): ImageData = image.scaled(maxDimension)
    }

// A pipeline of image transformations, applied in order
@Serializable
data class Lens(
    val operations: List<LensOperation> = listOf(),
    ) {

    fun apply(image: ImageData): ImageData {
        return operations.fold(image) { current, operation -> operation.apply(current) }
        }
    }
