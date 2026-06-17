/*
* Copyright (C) 2023 Rastislav Kish
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

package com.rastislavkish.vscan.core.openai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

import com.rastislavkish.vscan.core.ProvidersManager
import com.rastislavkish.vscan.core.ReasoningEffort

import com.rastislavkish.vscan.core.openai.requests.Message as Msg
import com.rastislavkish.vscan.core.openai.requests.Request as OpenaiRequest

class Conversation(
    val providersManager: ProvidersManager,
    var model: String,
    var maxCompletionTokens: Int,
    var reasoningEffort: ReasoningEffort?,
    val systemMessage: SystemMessage?,
    ) {

    val usage: Usage
    get() = messages
    .filterIsInstance<AssistantMessage>()
    .fold(Usage()) { acc, message -> acc+message.usage }

    val messages=mutableListOf<Message>()

    init {
        if (systemMessage!=null) {
            messages.add(systemMessage)
            }
        }

    fun addMessage(message: Message) {
        messages.add(message)
        }
    fun replaceMessage(index: Int, message: Message) {
        messages[index]=message
        }
    fun removeMessageAt(index: Int) {
        messages.removeAt(index)
        }
    fun removeMessagesFrom(index: Int) {
        while (messages.size>index)
        messages.removeAt(messages.size-1)
        }
    fun reset() {
        messages.clear()
        if (systemMessage!=null)
        messages.add(systemMessage)
        }

    fun getLastMessage(): Message? {
        if (messages.size==0)
        return null

        return messages.last()
        }

    suspend fun generateResponse(): AssistantMessage {
        val provider=providersManager.getProviderForModel(model)
        ?: throw Exception("Error: Model $model does not have an assigned provider")

        val baseUrl=provider.baseUrl
        val apiKey=provider.apiKey
        val modelId=provider.getModelId(model)

        if (modelId.startsWith("vscan-"))
        throw Exception("Error: The chosen provider does not support this model.")

        val messages=mutableListOf<Msg>()
        for (message in this.messages) {
            messages.add(message.render())
            }
        val bodyObject=OpenaiRequest(
            modelId,
            messages,
            maxCompletionTokens,
            reasoningEffortValue(),
            )

        val format=Json { explicitNulls=false } //In order to make the null entries in Content disappear during serialization

        val client=HttpClient(CIO) {
            engine {
                requestTimeout=300000
                }
            }

        val response: HttpResponse=try {
            client.post("$baseUrl/chat/completions") {
                header("Content-Type", "application/json")
                bearerAuth(apiKey)
                setBody(format.encodeToString(bodyObject))
                }
            }
        catch (e: Exception) {
            throw Exception("Error: Unable to connect to the model provider")
            }

        val responseText: String=response.body()

        val json: JsonObject=Json.decodeFromString(responseText)
        if (json.containsKey("error")) {
            val errorNode=json.get("error")!! as JsonObject
            val message=(errorNode.get("message") ?: throw Exception("Error: Unable to extract error message")) as JsonPrimitive

            throw Exception(message.content)
            }

        var usage=Usage()
        if (json.containsKey("usage")) {
            val usageNode=json.get("usage")!! as JsonObject

            val promptTokens=((usageNode.get("prompt_tokens") ?: throw Exception("Error: Unable to extract used prompt tokens")) as JsonPrimitive)
            .content.toInt()

            val reasoningTokens=if (usageNode.containsKey("reasoning_tokens"))
            (usageNode.get("reasoning_tokens")!! as JsonPrimitive).content.toInt()
            else
            0

            val completionTokens=((usageNode.get("completion_tokens") ?: throw Exception("Error: Unable to extract used completion tokens")) as JsonPrimitive)
            .content.toInt()

            val totalTokens=((usageNode.get("total_tokens") ?: throw Exception("Error: Unable to extract used total tokens")) as JsonPrimitive)
            .content.toInt()

            usage=Usage(promptTokens, reasoningTokens, completionTokens, totalTokens)
            }

        if (json.containsKey("choices")) {
            val choicesArray=json.get("choices")!! as JsonArray
            val choiceNode=(choicesArray.getOrNull(0) ?: throw Exception("Error: Model returned no response")) as JsonObject
            val messageNode=(choiceNode.get("message") ?: throw Exception("Error: Unable to extract the response message")) as JsonObject
            val content=((messageNode.get("content") ?: throw Exception("Error: Unable to extract the response message content")) as JsonPrimitive)
            .content

            val reasoning=if (messageNode.containsKey("reasoning"))
            (messageNode.get("reasoning")!! as JsonPrimitive).content
            else
            ""

            val finishReason=((choiceNode.get("finish_reason") ?: throw Exception("Error: Unable to extract the message finish reason")) as JsonPrimitive)
            .content

            val assistantMessage=AssistantMessage(content, reasoning, finishReason, usage)
            addMessage(assistantMessage)

            return assistantMessage
            }

        throw Exception("Error: Unknown error")
        }

    fun toMessageList(): List<Message> {
        return messages.toList()
        }

    private fun reasoningEffortValue(): String? {
        return when (reasoningEffort) {
            null -> null
            ReasoningEffort.NONE -> "none"
            ReasoningEffort.MINIMAL -> "minimal"
            ReasoningEffort.LOW -> "low"
            ReasoningEffort.MEDIUM -> "medium"
            ReasoningEffort.HIGH -> "high"
            ReasoningEffort.XHIGH -> "xhigh"
            }
        }
    }
