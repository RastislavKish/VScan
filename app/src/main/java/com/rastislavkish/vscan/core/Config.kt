/*
* Copyright (C) 2024 Rastislav Kish
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

import kotlinx.serialization.*

import com.rastislavkish.vscan.core.config.Analyzer
import com.rastislavkish.vscan.core.config.ConfigLens
import com.rastislavkish.vscan.core.config.Lifetime
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ToastOperation
import com.rastislavkish.vscan.core.openai.SystemMessage

// A program, run as processes: what to call it with, how to analyze the input, what to do with the output, and for how long
// The defaults make up the built-in base config
// The nested parts are copied with the generated copy()
@Serializable
data class Config(
    val id: String=ConfigManager.BASE_CONFIG_ID,
    val name: String="Base",
    // The name to call it by from the command prompt
    val command: String?=null,
    val description: String="",
    // The image parameter is implicit, see allParameters
    val parameters: List<Parameter> = listOf(),
    val lens: ConfigLens=ConfigLens(),
    val analyzer: Analyzer=LlmAnalyzer(),
    val operations: List<Operation> = listOf(ToastOperation()),
    val lifetime: Lifetime=Lifetime(),
    ) {

    // The declared parameters, with the image parameter first unless the config declares it itself
    val allParameters: List<Parameter>
    get() {
        if (parameters.any { it.name==Parameter.IMAGE_PARAMETER_NAME })
        return parameters

        return listOf(Parameter.defaultImageParameter)+parameters
        }

    // The LLM analyzer, the only analyzer type so far, which the scanning UI and the editor work with
    val llm: LlmAnalyzer
    get() = analyzer as LlmAnalyzer

    val systemPromptOrNull: SystemMessage?
    get() = if (!llm.systemPrompt.isEmpty()) SystemMessage(llm.systemPrompt) else null

    fun withLlm(transform: (LlmAnalyzer) -> LlmAnalyzer): Config = copy(analyzer=transform(llm))
    }
