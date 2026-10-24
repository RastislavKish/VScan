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

import android.content.Context

import java.io.File
import java.nio.file.Files

import androidx.test.core.app.ApplicationProvider

import kotlinx.coroutines.test.runTest

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import com.rastislavkish.vscan.core.ProvidersManager
import com.rastislavkish.vscan.core.files.LocalFileTree
import com.rastislavkish.vscan.core.files.VScanFiles
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.config.LlmChangeDetection
import com.rastislavkish.vscan.core.config.LlmChangeDetectionMode
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.imagesource.StaticImageSource
import com.rastislavkish.vscan.core.openai.AssistantMessage
import com.rastislavkish.vscan.core.openai.CompositeMessage
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.LocalImage
import com.rastislavkish.vscan.core.openai.SystemMessage
import com.rastislavkish.vscan.core.openai.Usage

@RunWith(RobolectricTestRunner::class)
class LlmAnalyzerRuntimeTest {

    private class FakeBackend(
        private val response: AssistantMessage,
        ): LlmBackend {

        private val providersManager=ProvidersManager(
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("LlmAnalyzerRuntimeTest", Context.MODE_PRIVATE),
            VScanFiles(LocalFileTree(Files.createTempDirectory("VScan").toFile()), File.createTempFile("manifest", ".json")),
            )

        var conversation: Conversation?=null

        override fun newConversation(analyzer: LlmAnalyzer, systemMessage: SystemMessage?): Conversation {
            return Conversation(providersManager, analyzer.model, analyzer.maxCompletionTokens, analyzer.reasoningEffort, systemMessage)
            .also { conversation=it }
            }
        override suspend fun respond(conversation: Conversation): AssistantMessage = response
        }

    private val photo=ImageData.fromEncoded(byteArrayOf(1))
    private val frame=ImageData.fromEncoded(byteArrayOf(2))
    private val camera=StaticImageSource(photo)
    private val other=StaticImageSource(frame)

    private fun input(variables: Map<String, Any?>): AnalysisInput {
        return AnalysisInput(mapOf("image" to camera)+variables) { source: ImageSource -> source.capture(false) }
        }

    private fun parts(backend: FakeBackend): List<CompositeMessage.Part> {
        return (backend.conversation!!.messages.last() as CompositeMessage).parts
        }

    private fun imageOf(part: CompositeMessage.Part): ImageData = ((part as CompositeMessage.Part.Image).image as LocalImage).image

    @Test
    fun rendersPromptsAndAppendsTheImage() = runTest {
        val backend=FakeBackend(AssistantMessage(" Bus 42\n", "", "stop", Usage(totalTokens=7)))
        val analyzer=LlmAnalyzer(
            systemPrompt="{% if strict %}Be brief.{% endif %}",
            userPrompt="Find {{ object }}.",
            )

        val result=LlmAnalyzerRuntime(analyzer, backend).analyze(input(mapOf("object" to "keys", "strict" to true)))

        assertEquals("Bus 42", result.output)
        assertTrue(result.isChange(null))
        assertEquals(7, result.usage.totalTokens)
        assertEquals("Be brief.", backend.conversation!!.messages.first().text)

        val parts=parts(backend)
        assertEquals(CompositeMessage.Part.Text("Find keys."), parts[0])
        assertSame(photo, imageOf(parts[1]))
        assertEquals(2, parts.size)
        }

    @Test
    fun placedImagesReplaceTheAppendedOne() = runTest {
        val backend=FakeBackend(AssistantMessage("Same", "", "stop"))
        val analyzer=LlmAnalyzer(systemPrompt="", userPrompt="Compare {{ other }} with {{ image }}")

        LlmAnalyzerRuntime(analyzer, backend).analyze(input(mapOf("other" to other)))

        val parts=parts(backend)
        assertEquals(4, parts.size)
        assertSame(frame, imageOf(parts[1]))
        assertSame(photo, imageOf(parts[3]))
        assertEquals(1, backend.conversation!!.messages.size)
        }

    @Test
    fun appendingCanBeTurnedOff() = runTest {
        val backend=FakeBackend(AssistantMessage("Text", "", "stop"))
        LlmAnalyzerRuntime(LlmAnalyzer(appendImage=false), backend).analyze(input(mapOf()))

        assertEquals(listOf(CompositeMessage.Part.Text("What's in the image?")), parts(backend))
        }

    @Test
    fun detectsChanges() = runTest {
        val unchanged=FakeBackend(AssistantMessage("Unchanged", "", "stop"))
        val llmDriven=LlmAnalyzer(changeDetection=LlmChangeDetection(mode=LlmChangeDetectionMode.LLM_DRIVEN))
        assertFalse(LlmAnalyzerRuntime(llmDriven, unchanged).analyze(input(mapOf())).isChange("42"))

        val same=FakeBackend(AssistantMessage("42", "", "stop"))
        val sameResult=LlmAnalyzerRuntime(LlmAnalyzer(), same).analyze(input(mapOf()))
        assertFalse(sameResult.isChange("42"))
        assertTrue(sameResult.isChange("41"))
        }

    @Test
    fun emptyOutputsFail() = runTest {
        for ((reason, message) in listOf("length" to "Reasoning exceeded the token limit", "stop" to "Received empty output")) {
            try {
                LlmAnalyzerRuntime(LlmAnalyzer(), FakeBackend(AssistantMessage(" ", "", reason))).analyze(input(mapOf()))
                fail()
                }
            catch (e: ProcessException) {
                assertEquals(message, e.message)
                }
            }
        }
    }
