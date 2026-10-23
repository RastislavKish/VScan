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
package com.rastislavkish.vscan.core.process

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.config.ChangeMarkerMeaning
import com.rastislavkish.vscan.core.config.LlmChangeDetection
import com.rastislavkish.vscan.core.config.LlmChangeDetectionMode
import com.rastislavkish.vscan.core.config.MarkerMatch

class LlmChangeDetectorTest {

    private fun detector(marker: String, match: MarkerMatch=MarkerMatch.EXACT, meaning: ChangeMarkerMeaning=ChangeMarkerMeaning.NO_CHANGE) =
    LlmChangeDetector(LlmChangeDetection(LlmChangeDetectionMode.LLM_DRIVEN, marker, meaning, match))

    @Test
    fun appDrivenComparesWithTheLastOutput() {
        val detector=LlmChangeDetector(LlmChangeDetection())

        assertTrue(detector.isChange("42", null))
        assertFalse(detector.isChange("42", "42"))
        assertFalse(detector.isChange("42", " 42\n"))
        assertTrue(detector.isChange("43", "42"))
        }

    @Test
    fun exactMarker() {
        val detector=detector("Unchanged")

        assertFalse(detector.isChange("Unchanged", "42"))
        assertTrue(detector.isChange("Unchanged.", "42"))
        // The output doesn't need to differ
        assertTrue(detector.isChange("42", "42"))
        }

    @Test
    fun wildcardMarker() {
        val detector=detector("CHANGE: *", MarkerMatch.WILDCARD, ChangeMarkerMeaning.CHANGE)

        assertTrue(detector.isChange("CHANGE: a bus\narrived", null))
        assertFalse(detector.isChange("Nothing new", null))
        assertFalse(detector.isChange("A CHANGE: x", null))

        val single=detector("Unchanged?", MarkerMatch.WILDCARD)
        assertFalse(single.isChange("Unchanged.", null))
        assertTrue(single.isChange("Unchanged", null))
        // Regex characters are literal
        assertTrue(detector("a.b", MarkerMatch.WILDCARD).isChange("axb", null))
        }

    @Test
    fun regexMarker() {
        val detector=detector("(?i)unchanged\\.?", MarkerMatch.REGEX)

        assertFalse(detector.isChange("UNCHANGED.", null))
        assertTrue(detector.isChange("It's unchanged", null))
        }

    @Test(expected=ProcessException::class)
    fun invalidRegexFailsUpFront() {
        detector("(", MarkerMatch.REGEX)
        }
    }
