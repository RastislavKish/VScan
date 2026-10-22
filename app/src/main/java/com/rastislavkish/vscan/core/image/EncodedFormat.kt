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

enum class EncodedFormat(
    val mimeType: String,
    val extension: String,
    // Whether vision LLM providers accept the format, otherwise images are re-encoded to JPEG before sending
    val sendable: Boolean,
    ) {

    JPEG("image/jpeg", "jpg", true),
    PNG("image/png", "png", true),
    WEBP("image/webp", "webp", true),
    GIF("image/gif", "gif", true),
    HEIF("image/heif", "heic", false),
    UNKNOWN("application/octet-stream", "bin", false);

    companion object {

        fun detect(bytes: ByteArray): EncodedFormat {
            fun matches(offset: Int, vararg signature: Int): Boolean {
                if (bytes.size<offset+signature.size)
                return false

                return signature.indices.all { bytes[offset+it]==signature[it].toByte() }
                }

            return when {
                matches(0, 0xFF, 0xD8, 0xFF) -> JPEG
                matches(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
                matches(0, 0x52, 0x49, 0x46, 0x46) && matches(8, 0x57, 0x45, 0x42, 0x50) -> WEBP
                matches(0, 0x47, 0x49, 0x46, 0x38) -> GIF
                // ISO base media "ftyp" box with a HEIF brand
                matches(4, 0x66, 0x74, 0x79, 0x70) && isHeifBrand(bytes) -> HEIF
                else -> UNKNOWN
                }
            }

        private fun isHeifBrand(bytes: ByteArray): Boolean {
            if (bytes.size<12)
            return false

            val brand=String(bytes, 8, 4, Charsets.US_ASCII)
            return brand in setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif")
            }
        }
    }
