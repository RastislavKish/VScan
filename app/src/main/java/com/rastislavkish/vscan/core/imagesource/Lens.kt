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

import com.rastislavkish.vscan.core.image.ImageRect

// A rectangle in coordinates relative to the image size, 0 being the left / top edge and 1 the right / bottom one
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

// The transformation of a lens, cutting out a region of the image and optionally downscaling it
// Lenses never upscale, as that adds no information, zooming is done by cropping the full-resolution image
data class Lens(
    val region: RelativeRect=RelativeRect.FULL,
    // The maximum length of the longer side of the result, null for no downscaling
    val maxDimension: Int?=null,
    ) {

    init {
        require(maxDimension==null || maxDimension>0) { "The maximum dimension must be positive" }
        }

    companion object {

        // A lens magnifying the image zoom times around the given relative point
        // The region is shifted rather than cut where it would reach over the image edge
        fun zoom(zoom: Float, centerX: Float=0.5f, centerY: Float=0.5f, maxDimension: Int?=null): Lens {
            require(zoom>=1f) { "Zoom must be at least 1" }

            val size=1f/zoom
            val left=(centerX-size/2).coerceIn(0f, 1f-size)
            val top=(centerY-size/2).coerceIn(0f, 1f-size)

            return Lens(RelativeRect(left, top, minOf(left+size, 1f), minOf(top+size, 1f)), maxDimension)
            }
        }
    }
