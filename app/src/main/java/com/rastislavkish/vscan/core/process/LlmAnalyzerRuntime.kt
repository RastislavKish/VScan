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

import com.rastislavkish.vscan.core.ProvidersManager
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.openai.AssistantMessage
import com.rastislavkish.vscan.core.openai.CompositeMessage
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.LocalImage
import com.rastislavkish.vscan.core.openai.SystemMessage
import com.rastislavkish.vscan.core.script.ScriptEngine
import com.rastislavkish.vscan.core.template.Template
import com.rastislavkish.vscan.core.template.TemplatePart

// Creates the conversations of LLM analyses and gets their responses, replaceable in tests
interface LlmBackend {

    fun newConversation(analyzer: LlmAnalyzer, systemMessage: SystemMessage?): Conversation
    suspend fun respond(conversation: Conversation): AssistantMessage
    }

class ProviderLlmBackend(
    private val providersManager: ProvidersManager,
    ): LlmBackend {

    override fun newConversation(analyzer: LlmAnalyzer, systemMessage: SystemMessage?): Conversation = Conversation(
        providersManager,
        analyzer.model,
        analyzer.maxCompletionTokens,
        analyzer.reasoningEffort,
        systemMessage,
        )
    override suspend fun respond(conversation: Conversation): AssistantMessage = conversation.generateResponse()
    }

// Every iteration starts a new conversation from the rendered prompts
class LlmAnalyzerRuntime(
    val analyzer: LlmAnalyzer,
    private val backend: LlmBackend,
    ): AnalyzerRuntime {

    // Parsed up front, so syntax errors fail the process start
    private val systemTemplate=Template.parse(analyzer.systemPrompt)
    private val userTemplate=Template.parse(analyzer.userPrompt)
    private val changeDetector=LlmChangeDetector(analyzer.changeDetection)

    override suspend fun analyze(input: AnalysisInput): AnalysisResult {
        val scope=ScriptEngine.newScope(input.variables)

        val systemPrompt=systemTemplate.renderText(scope)
        val systemMessage=if (systemPrompt.isNotBlank()) SystemMessage(systemPrompt) else null

        val parts=mutableListOf<CompositeMessage.Part>()
        var hasImage=false
        for (part in userTemplate.render(scope)) {
            when (part) {
                is TemplatePart.Text -> parts.add(CompositeMessage.Part.Text(part.text))
                is TemplatePart.Image -> {
                    parts.add(imagePart(part.value, input))
                    hasImage=true
                    }
                }
            }

        if (!hasImage && analyzer.appendImage) {
            val image=input.variables[Parameter.IMAGE_PARAMETER_NAME]
            if (image!=null)
            parts.add(imagePart(image, input))
            }

        val conversation=backend.newConversation(analyzer, systemMessage)
        conversation.addMessage(CompositeMessage(parts))
        val response=backend.respond(conversation)

        if (response.text.isBlank()) {
            if (response.finishReason=="length")
            throw ProcessException("Reasoning exceeded the token limit")

            throw ProcessException("Received empty output")
            }

        val output=response.text.trim()
        return AnalysisResult(
            output,
            changeDetector.isChange(output, input.lastOutput),
            response.usage,
            conversation,
            )
        }

    private suspend fun imagePart(value: Any, input: AnalysisInput): CompositeMessage.Part {
        val image=when (value) {
            is ImageData -> value
            is ImageSource -> input.capture(value)
            else -> throw ProcessException("Not an image: $value")
            }

        return CompositeMessage.Part.Image(LocalImage(image))
        }
    }
