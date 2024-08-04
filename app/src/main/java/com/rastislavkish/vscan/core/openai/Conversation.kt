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

import com.rastislavkish.vscan.core.openai.requests.Message as Msg
import com.rastislavkish.vscan.core.openai.requests.Request as OpenaiRequest

class Conversation(
    val apiKey: String,
    val model: String,
    val systemMessage: SystemMessage?,
    ) {

    var totalUsedInputTokens=0
    get private set

    var totalUsedOutputTokens=0
    get private set

    val totalPrice: Double
    get() = 0.005*(totalUsedInputTokens/1000)+0.015*(totalUsedOutputTokens/1000)

    val messages=mutableListOf<Message>()

    init {
        if (systemMessage!=null) {
            messages.add(systemMessage)
            }
        }

    fun addMessage(message: Message) {
        messages.add(message)
        }
    fun reset() {
        messages.clear()
        if (systemMessage!=null)
        messages.add(systemMessage)
        }

    suspend fun generateResponse(): String {
        val messages=mutableListOf<Msg>()
        for (message in this.messages) {
            messages.add(message.render())
            }
        val bodyObject=OpenaiRequest(model, messages, 300)

        val format=Json { explicitNulls=false } //In order to make the null entries in Content disappear during serialization

        val client=HttpClient(CIO) {
            engine {
                requestTimeout=300000
                }
            }
        val response: HttpResponse=client.post("https://api.openai.com/v1/chat/completions") {
            header("Content-Type", "application/json")
            bearerAuth(apiKey)
            setBody(format.encodeToString(bodyObject))
            }

        val responseText: String=response.body()

        val json: JsonObject=Json.decodeFromString(responseText)
        if (json.containsKey("error")) {
            val errorNode=(json.get("error") ?: return "Error: Unknown error") as JsonObject
            val message=(errorNode.get("message") ?: return "Error: Unknown error") as JsonPrimitive

            return message.content
            }

        if (json.containsKey("usage")) {
            val usageNode=(json.get("usage") ?: return "Error: Unable to extract the usage noe") as JsonObject
            val promptTokens=(usageNode.get("prompt_tokens") ?: return "Error: Unable to extract used prompt tokens") as JsonPrimitive
            val completionTokens=(usageNode.get("completion_tokens") ?: return "Error: Unable to extract used completion tokens") as JsonPrimitive

            totalUsedInputTokens=promptTokens.content.toInt()
            totalUsedOutputTokens=completionTokens.content.toInt()
            }

        if (json.containsKey("choices")) {
            val choicesArray=json.get("choices")!! as JsonArray
            val choiceNode=(choicesArray.getOrNull(0) ?: return "Error: Model returned no response") as JsonObject
            val messageNode=(choiceNode.get("message") ?: return "Error: Unable to extract the response message") as JsonObject
            val content=(messageNode.get("content") ?: return "Error: Unable to extract the response message content") as JsonPrimitive

            val responseMessage=GptResponse(content.content)
            addMessage(responseMessage)

            return responseMessage.text
            }

        return "Error: Unknown error"
        }

    }
