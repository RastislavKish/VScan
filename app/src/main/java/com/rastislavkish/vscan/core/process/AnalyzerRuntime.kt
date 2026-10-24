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

import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.Usage

// A user-facing failure of a process, its start or one of its iterations
class ProcessException(message: String): Exception(message)

// What an analyzer gets for one iteration
class AnalysisInput(
    // The process variables as the iteration starts, for templates and expressions
    // In the async mode, the results of the iterations still running aren't in them
    val variables: Map<String, Any?>,
    // Takes an image from a source the way the process captures, the same source giving the same image within an iteration
    val capture: suspend (ImageSource) -> ImageData,
    )

class AnalysisResult(
    // The processable output
    val output: String,
    // Whether there's anything new to report since the given last output
    // Decided when the result is committed, the iterations before it having been committed already
    val isChange: (lastOutput: String?) -> Boolean,
    val usage: Usage=Usage(),
    // The conversation of an LLM analysis, which the foreground process lets the user continue
    val conversation: Conversation?=null,
    )

// The running form of a config's analyzer
// Failures are exceptions, ProcessException carrying a message for the user
interface AnalyzerRuntime {

    suspend fun analyze(input: AnalysisInput): AnalysisResult
    }
