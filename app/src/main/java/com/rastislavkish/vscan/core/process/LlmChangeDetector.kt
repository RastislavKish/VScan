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

import com.rastislavkish.vscan.core.config.ChangeMarkerMeaning
import com.rastislavkish.vscan.core.config.LlmChangeDetection
import com.rastislavkish.vscan.core.config.LlmChangeDetectionMode
import com.rastislavkish.vscan.core.config.MarkerMatch

// Decides whether an LLM output is a change, outputs being trimmed
class LlmChangeDetector(
    val detection: LlmChangeDetection,
    ) {

    // Compiled up front, so an invalid pattern fails the process start rather than every iteration
    private val marker: Regex?=when (detection.mode) {
        LlmChangeDetectionMode.APP_DRIVEN -> null
        LlmChangeDetectionMode.LLM_DRIVEN -> compileMarker()
        }

    fun isChange(output: String, lastOutput: String?): Boolean {
        if (marker==null)
        return output!=lastOutput?.trim()

        val matches=marker.matches(output)
        return when (detection.changeMarkerMeaning) {
            ChangeMarkerMeaning.NO_CHANGE -> !matches
            ChangeMarkerMeaning.CHANGE -> matches
            }
        }

    private fun compileMarker(): Regex {
        val marker=detection.changeMarker.trim()

        val pattern=when (detection.changeMarkerMatch) {
            MarkerMatch.EXACT -> Regex.escape(marker)
            MarkerMatch.WILDCARD -> marker.split("*").joinToString(".*") { piece ->
                piece.split("?").joinToString(".") { if (it.isEmpty()) "" else Regex.escape(it) }
                }
            MarkerMatch.REGEX -> marker
            }

        try {
            // Outputs may span several lines
            return Regex(pattern, RegexOption.DOT_MATCHES_ALL)
            }
        catch (e: Exception) {
            throw ProcessException("Invalid change marker \"$marker\": ${e.message}")
            }
        }
    }
