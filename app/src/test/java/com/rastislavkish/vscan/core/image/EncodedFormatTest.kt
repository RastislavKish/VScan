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

import org.junit.Assert.*
import org.junit.Test

class EncodedFormatTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun detectsFormatsBySignature() {
        assertEquals(EncodedFormat.JPEG, EncodedFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE1)))
        assertEquals(EncodedFormat.PNG, EncodedFormat.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals(EncodedFormat.WEBP, EncodedFormat.detect(ascii("RIFF")+bytes(0, 0, 0, 0)+ascii("WEBPVP8 ")))
        assertEquals(EncodedFormat.GIF, EncodedFormat.detect(ascii("GIF89a")))
        assertEquals(EncodedFormat.HEIF, EncodedFormat.detect(bytes(0, 0, 0, 0x18)+ascii("ftypheic")))
        }

    @Test
    fun unknownOrTruncatedDataIsUnknown() {
        assertEquals(EncodedFormat.UNKNOWN, EncodedFormat.detect(ascii("BM")))
        assertEquals(EncodedFormat.UNKNOWN, EncodedFormat.detect(bytes(0xFF, 0xD8)))
        assertEquals(EncodedFormat.UNKNOWN, EncodedFormat.detect(ByteArray(0)))
        // A non-HEIF ISO media file, e.g. an MP4 video
        assertEquals(EncodedFormat.UNKNOWN, EncodedFormat.detect(bytes(0, 0, 0, 0x18)+ascii("ftypisom")))
        }

    @Test
    fun onlyProviderSupportedFormatsAreSendable() {
        assertEquals(
            setOf(EncodedFormat.JPEG, EncodedFormat.PNG, EncodedFormat.WEBP, EncodedFormat.GIF),
            EncodedFormat.entries.filter { it.sendable }.toSet(),
            )
        }

    }
