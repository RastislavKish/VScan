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
package com.rastislavkish.vscan.core.config

import kotlinx.serialization.*

import com.rastislavkish.vscan.core.ReasoningEffort

// The computation of a config: turns the input into an output the operations can process, and decides whether there's anything new to report
// Each analyzer type has its own way of detecting changes
@Serializable
sealed class Analyzer

@Serializable
@SerialName("llm")
data class LlmAnalyzer(
    val model: String="vscan-gpt-4o",
    // Templates
    val systemPrompt: String="",
    val userPrompt: String="What's in the image?",
    val maxCompletionTokens: Int=300,
    val reasoningEffort: ReasoningEffort?=null,
    // Appends the image parameter to the user prompt, unless the prompt places some image itself
    val appendImage: Boolean=true,
    val changeDetection: LlmChangeDetection=LlmChangeDetection(),
    ): Analyzer()

@Serializable
enum class LlmChangeDetectionMode {
    // The model signals whether there's a change with the change marker, so the prompt has to instruct it to
    @SerialName("llm_driven") LLM_DRIVEN,
    // The app compares the output with the last reported one, so the prompt has to make the model answer consistently
    @SerialName("app_driven") APP_DRIVEN,
    }

@Serializable
enum class ChangeMarkerMeaning {
    // An output matching the marker means nothing changed
    @SerialName("no_change") NO_CHANGE,
    // Only an output matching the marker is a change, and becomes the reported output as it is
    @SerialName("change") CHANGE,
    }

@Serializable
enum class MarkerMatch {
    @SerialName("exact") EXACT,
    // * matches any text and ? any single character
    @SerialName("wildcard") WILDCARD,
    @SerialName("regex") REGEX,
    }

@Serializable
data class LlmChangeDetection(
    val mode: LlmChangeDetectionMode=LlmChangeDetectionMode.APP_DRIVEN,
    val changeMarker: String="Unchanged",
    val changeMarkerMeaning: ChangeMarkerMeaning=ChangeMarkerMeaning.NO_CHANGE,
    val changeMarkerMatch: MarkerMatch=MarkerMatch.EXACT,
    )
