/*
* Copyright (C) 2025 Rastislav Kish
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

package com.rastislavkish.vscan.core

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

// Source: https://stackoverflow.com/questions/44022062/converting-yuv-420-888-to-jpeg-and-saving-file-results-distorted-image

class ImageConverter {

    companion object {

        fun yuv420ToJpeg(image: Image): ByteArray {
            return nv21ToJpeg(yuv420ToNv21(image), image.width, image.height, 100)
            }

        fun nv21ToJpeg(nv21: ByteArray, width: Int, height: Int, quality: Int): ByteArray {
            val out=ByteArrayOutputStream()
            val yuv=YuvImage(nv21, ImageFormat.NV21, width, height, null)
            yuv.compressToJpeg(Rect(0, 0, width, height), quality, out)
            return out.toByteArray()
            }

        fun yuv420ToNv21(image: Image): ByteArray {
            val crop: Rect=image.getCropRect()
            val format=image.format
            val width=crop.width()
            val height=crop.height()
            val planes=image.getPlanes()
            val data=ByteArray(width*height*ImageFormat.getBitsPerPixel(format)/8)
            val rowData=ByteArray(planes[0].getRowStride())

            var channelOffset = 0
            var outputStride = 1
            for (i in 0 until planes.size) {
                when (i) {
                    0 -> {
                        channelOffset=0
                        outputStride=1
                        }
                    1 -> {
                        channelOffset=width*height+1
                        outputStride=2
                        }
                    2 -> {
                        channelOffset=width*height
                        outputStride=2
                        }
                    }

                val buffer: ByteBuffer=planes[i].getBuffer()
                val rowStride=planes[i].getRowStride()
                val pixelStride=planes[i].getPixelStride();

                val shift=if (i==0)
                0
                else
                1

                val w=width shr shift
                val h=height shr shift
                buffer.position(rowStride*(crop.top shr shift)+pixelStride*(crop.left shr shift))
                for (row in 0 until h) {
                    val length: Int

                    if (pixelStride==1 && outputStride==1) {
                        length = w
                        buffer.get(data, channelOffset, length)
                        channelOffset+=length
                        }
                    else {
                        length=(w-1)*pixelStride+1
                        buffer.get(rowData, 0, length)
                        for (col in 0 until w) {
                            data[channelOffset]=rowData[col*pixelStride]
                            channelOffset+=outputStride
                            }
                        }

                    if (row<h-1) {
                        buffer.position(buffer.position()+rowStride-length)
                        }
                    }
                }
            return data
            }

        }
    }
