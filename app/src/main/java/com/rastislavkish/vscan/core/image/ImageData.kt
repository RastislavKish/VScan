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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Build

// The app's internal image representation
// Holds either the original encoded bytes, which are passed through untouched where possible, or a bitmap produced by a transformation
// Everything is derived lazily, and full-resolution pixels are decoded only when explicitly requested, since a 12 MP bitmap takes ~48 MB
class ImageData private constructor(
    private val original: ByteArray?,
    private val bitmap: Bitmap?,
    ) {

    val format: EncodedFormat by lazy {
        if (original!=null) EncodedFormat.detect(original) else EncodedFormat.JPEG
        }

    val orientation: Orientation by lazy {
        if (original==null)
        Orientation.NORMAL
        else
        try {
            val exif=ExifInterface(ByteArrayInputStream(original))
            Orientation.fromExif(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))
            }
        catch (e: Exception) {
            Orientation.NORMAL
            }
        }

    // Dimensions of the stored pixels, before applying the orientation
    private val rawSize: Pair<Int, Int> by lazy {
        if (bitmap!=null)
        Pair(bitmap.width, bitmap.height)
        else {
            val options=BitmapFactory.Options()
            options.inJustDecodeBounds=true
            BitmapFactory.decodeByteArray(original, 0, original!!.size, options)

            if (options.outWidth<=0 || options.outHeight<=0)
            throw ImageDecodingException("Unable to read the image dimensions")

            Pair(options.outWidth, options.outHeight)
            }
        }

    // Dimensions of the upright image
    val width: Int
    get() = orientation.displayWidth(rawSize.first, rawSize.second)
    val height: Int
    get() = orientation.displayHeight(rawSize.first, rawSize.second)

    private val jpeg: ByteArray by lazy {
        val source=bitmap ?: decode()

        val stream=ByteArrayOutputStream()
        if (!source.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream))
        throw ImageDecodingException("Unable to encode the image")

        stream.toByteArray()
        }

    // The bytes to send or store, the original ones whenever their format allows
    val encoded: ByteArray
    get() = if (original!=null && format.sendable) original else jpeg

    val encodedFormat: EncodedFormat
    get() = if (original!=null && format.sendable) format else EncodedFormat.JPEG

    val base64: String by lazy {
        Base64.getEncoder().encodeToString(encoded)
        }

    // Decodes the upright image, downscaled so that its longer side doesn't exceed maxDimension, if given
    fun decode(maxDimension: Int?=null): Bitmap {
        return decodeRegion(ImageRect.ofSize(width, height), maxDimension)
        }

    // Returns the given region of the upright image at full resolution, downscaled only if maxDimension is given
    // Only the region is decoded, so cropping a small part of a large photo stays cheap
    fun crop(rect: ImageRect, maxDimension: Int?=null): ImageData {
        val bounded=rect.intersect(ImageRect.ofSize(width, height))
        if (bounded.isEmpty())
        throw IllegalArgumentException("The crop region $rect lies outside of the ${width}x${height} image")

        return fromBitmap(decodeRegion(bounded, maxDimension))
        }

    // Returns the image downscaled so that its longer side doesn't exceed maxDimension, or this image if it already fits
    fun scaled(maxDimension: Int): ImageData {
        if (maxOf(width, height)<=maxDimension)
        return this

        return fromBitmap(decode(maxDimension))
        }

    private fun decodeRegion(rect: ImageRect, maxDimension: Int?): Bitmap {
        val (rawWidth, rawHeight)=rawSize
        val rawRect=orientation.toRaw(rect, rawWidth, rawHeight)

        val scale=if (maxDimension!=null)
        minOf(1.0f, maxDimension.toFloat()/maxOf(rect.width, rect.height))
        else
        1.0f

        val region=if (bitmap!=null)
        Bitmap.createBitmap(bitmap, rawRect.left, rawRect.top, rawRect.width, rawRect.height)
        else {
            val options=BitmapFactory.Options()
            options.inSampleSize=sampleSize(scale)

            if (rawRect==ImageRect.ofSize(rawWidth, rawHeight))
            decodeFull(options)
            else
            decodeRegionWithDecoder(rawRect, options)
            ?: decodeRegionFromFull(rawRect, options)
            }

        // The sample size only gets close to the target, the matrix finishes the scaling together with the orientation
        val targetWidth=Math.round(rawRect.width*scale).coerceAtLeast(1)
        val matrix=orientationMatrix()
        val remainingScale=targetWidth.toFloat()/region.width
        matrix.postScale(remainingScale, remainingScale)

        if (matrix.isIdentity)
        return region

        return Bitmap.createBitmap(region, 0, 0, region.width, region.height, matrix, true)
        }

    private fun decodeFull(options: BitmapFactory.Options): Bitmap {
        return BitmapFactory.decodeByteArray(original, 0, original!!.size, options)
        ?: throw ImageDecodingException("Unable to decode the image")
        }

    // Decodes just the region, returns null if the format doesn't support region decoding (e.g. GIF)
    private fun decodeRegionWithDecoder(rawRect: ImageRect, options: BitmapFactory.Options): Bitmap? {
        val decoder=try {
            newRegionDecoder(original!!)
            }
        catch (e: Exception) {
            null
            }
        ?: return null

        try {
            return decoder.decodeRegion(Rect(rawRect.left, rawRect.top, rawRect.right, rawRect.bottom), options)
            }
        finally {
            decoder.recycle()
            }
        }
    private fun decodeRegionFromFull(rawRect: ImageRect, options: BitmapFactory.Options): Bitmap {
        val full=decodeFull(options)

        // The full image is subsampled, so the region has to be scaled to its resolution
        val factor=full.width.toFloat()/rawSize.first
        val left=Math.round(rawRect.left*factor).coerceIn(0, full.width-1)
        val top=Math.round(rawRect.top*factor).coerceIn(0, full.height-1)
        val right=Math.round(rawRect.right*factor).coerceIn(left+1, full.width)
        val bottom=Math.round(rawRect.bottom*factor).coerceIn(top+1, full.height)

        return Bitmap.createBitmap(full, left, top, right-left, bottom-top)
        }

    private fun newRegionDecoder(bytes: ByteArray): BitmapRegionDecoder? {
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)
        return BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)

        @Suppress("DEPRECATION")
        return BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        }

    private fun orientationMatrix(): Matrix {
        val matrix=Matrix()

        when (orientation) {
            Orientation.NORMAL -> {}
            Orientation.FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            Orientation.ROTATE_180 -> matrix.setRotate(180f)
            Orientation.FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            Orientation.TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            Orientation.ROTATE_90 -> matrix.setRotate(90f)
            Orientation.TRANSVERSE -> { matrix.setRotate(90f); matrix.postScale(1f, -1f) }
            Orientation.ROTATE_270 -> matrix.setRotate(270f)
            }

        return matrix
        }

    companion object {

        const val JPEG_QUALITY=90

        fun fromEncoded(bytes: ByteArray): ImageData = ImageData(bytes, null)
        fun fromBitmap(bitmap: Bitmap): ImageData = ImageData(null, bitmap)

        // The largest power of two subsampling that still keeps at least the requested resolution
        internal fun sampleSize(scale: Float): Int {
            var sampleSize=1
            while (scale*sampleSize*2<=1.0f)
            sampleSize*=2

            return sampleSize
            }
        }
    }

class ImageDecodingException(message: String): Exception(message)
