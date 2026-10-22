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

data class ImageRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    ) {

    val width: Int
    get() = right-left
    val height: Int
    get() = bottom-top

    fun isEmpty(): Boolean = width<=0 || height<=0

    fun intersect(other: ImageRect): ImageRect = ImageRect(
        maxOf(left, other.left),
        maxOf(top, other.top),
        minOf(right, other.right),
        minOf(bottom, other.bottom),
        )

    companion object {

        fun ofSize(width: Int, height: Int) = ImageRect(0, 0, width, height)
        }
    }

// The EXIF orientations, describing how the stored pixels must be transformed to display the image upright
enum class Orientation(val exifValue: Int) {

    NORMAL(1),
    FLIP_HORIZONTAL(2),
    ROTATE_180(3),
    FLIP_VERTICAL(4),
    TRANSPOSE(5),
    ROTATE_90(6),
    TRANSVERSE(7),
    ROTATE_270(8);

    val swapsDimensions: Boolean
    get() = this==TRANSPOSE || this==ROTATE_90 || this==TRANSVERSE || this==ROTATE_270

    fun displayWidth(rawWidth: Int, rawHeight: Int): Int = if (swapsDimensions) rawHeight else rawWidth
    fun displayHeight(rawWidth: Int, rawHeight: Int): Int = if (swapsDimensions) rawWidth else rawHeight

    // Maps a rectangle in the upright image to the corresponding rectangle of the stored pixels
    fun toRaw(rect: ImageRect, rawWidth: Int, rawHeight: Int): ImageRect {
        val (x1, y1)=pointToRaw(rect.left, rect.top, rawWidth, rawHeight)
        val (x2, y2)=pointToRaw(rect.right, rect.bottom, rawWidth, rawHeight)

        return ImageRect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
        }

    private fun pointToRaw(x: Int, y: Int, w: Int, h: Int): Pair<Int, Int> = when (this) {
        NORMAL -> Pair(x, y)
        FLIP_HORIZONTAL -> Pair(w-x, y)
        ROTATE_180 -> Pair(w-x, h-y)
        FLIP_VERTICAL -> Pair(x, h-y)
        TRANSPOSE -> Pair(y, x)
        ROTATE_90 -> Pair(y, h-x)
        TRANSVERSE -> Pair(w-y, h-x)
        ROTATE_270 -> Pair(w-y, x)
        }

    companion object {

        fun fromExif(value: Int): Orientation = entries.find { it.exifValue==value } ?: NORMAL
        }
    }
