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
package com.rastislavkish.vscan.core.toml

import java.io.File

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonPrimitive

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.config.*
import com.rastislavkish.vscan.core.imagesource.Zoom
import com.rastislavkish.vscan.core.process.Process
import com.rastislavkish.vscan.core.speech.SpeechPriority

class TomlTest {

    @Serializable
    data class Mapping(
        val baseUrl: String="",
        val models: Map<String, String> = mapOf(),
        val apiKey: String?=null,
        )

    private val fullConfig=Config(
        id="bus-line-reader",
        name="Bus line reader",
        command="blr",
        description="Reports the line numbers of buses",
        parameters=listOf(
            Parameter(Parameter.IMAGE_PARAMETER_NAME, ParameterType.IMAGE_SOURCE, short="i", default="\$glasses"),
            Parameter("object", ParameterType.STRING, positional=true, rest=true, default="keys"),
            ),
        lens=ConfigLens(listOf(Zoom(2f, centerY=0.4f)), LensTarget.ALL),
        analyzer=LlmAnalyzer(
            systemPrompt="Answer briefly.\nNo comments.\n",
            userPrompt="Read the line of the bus.\n{% if lastOutput %}Last time: {{ lastOutput }}{% endif %}",
            reasoningEffort=com.rastislavkish.vscan.core.ReasoningEffort.LOW,
            appendImage=false,
            changeDetection=LlmChangeDetection(LlmChangeDetectionMode.LLM_DRIVEN, "CHANGE: *", ChangeMarkerMeaning.CHANGE, MarkerMatch.WILDCARD),
            ),
        operations=listOf(
            SayOperation("changed", "Bus {{ lastOutput }}", SpeechPriority.HIGH),
            PlayOperation(sound="beep", repeat=0, volume=NumberValue(value=0.8), pan=NumberValue(extract=Extraction.REGEX, pattern="x=(\\d+)"), instance=SoundInstance.NEW, stopOthers=true),
            VibrateOperation(pattern=listOf(0, 100, 50, 100), repeat=2),
            ToastOperation(text="{{ rawOutput }}"),
            ),
        lifetime=Lifetime(interval=2.0, capture=CaptureMode.PHOTO, iterationCondition="iteration<10", maxIterations=5, maxDuration=60.0, maxTokens=1000),
        )

    private fun <T> roundTrip(serializer: KSerializer<T>, value: T): T = Toml.decode(serializer, Toml.encode(serializer, value))

    @Test
    fun roundTripsAllConfigParts() {
        assertEquals(fullConfig, roundTrip(Config.serializer(), fullConfig))
        assertEquals(Config(), roundTrip(Config.serializer(), Config()))
        }

    @Test
    fun writesReadableFiles() {
        val text=Toml.encode(Config.serializer(), fullConfig, "Written by VScan")

        assertTrue(text.startsWith("# Written by VScan\n\n"))
        assertTrue(text, text.contains("\n[analyzer]\ntype = \"llm\"\n"))
        // Sections in the order of the fields
        assertTrue(text, text.indexOf("[[parameters]]")<text.indexOf("[lens]"))
        assertTrue(text, text.indexOf("[[operations]]")<text.indexOf("[lifetime]"))
        assertTrue(text, text.contains("max_completion_tokens = 300\n"))
        assertTrue(text, text.contains("\n[analyzer.change_detection]\nmode = \"llm_driven\"\n"))
        // Prompts as they are
        assertTrue(text, text.contains("user_prompt = '''\nRead the line of the bus.\n{% if lastOutput %}"))
        assertTrue(text, text.contains("\n[[operations]]\ntype = \"say\"\ncondition = \"changed\"\n"))
        assertTrue(text, text.contains("pan = { extract = \"regex\", pattern = 'x=(\\d+)' }"))
        assertTrue(text, text.contains("operations = [{ type = \"zoom\", zoom = 2.0, center_x = 0.5, center_y = 0.4 }]"))
        // Null values are left out
        assertFalse(text, text.contains("expression"))
        }

    @Test
    fun keepsMapKeys() {
        val mapping=Mapping("https://example.com", mapOf("vscan-gpt-4o" to "gpt-4o", "some_model" to "x", "a.b" to "y"))
        val text=Toml.encode(Mapping.serializer(), mapping)

        assertTrue(text, text.contains("base_url = "))
        assertTrue(text, text.contains("some_model = \"x\""))
        assertTrue(text, text.contains("\"a.b\" = \"y\""))
        assertEquals(mapping, Toml.decode(Mapping.serializer(), text))
        }

    @Test
    fun stringsSurvive() {
        val texts=listOf(
            "plain",
            "with \"quotes\" and 'apostrophes'",
            "back\\slash",
            "back\\slash and 'apostrophe'",
            "multi\nline",
            "multi\nline\n",
            "ends with 'quote'\nhere'",
            "has ''' inside\nit",
            "tab\tand \r\n windows",
            "\nstarts with a newline",
            "control \u0001 character\n",
            "",
            )

        for (text in texts) {
            val mapping=Mapping(text)
            assertEquals(text, mapping, Toml.decode(Mapping.serializer(), Toml.encode(Mapping.serializer(), mapping)))
            }
        }

    @Test
    fun readsHandWrittenFiles() {
        val config=Toml.decode(Config.serializer(), """
            name = "Find"
            [lifetime]
            interval = 1            # an integer for a double
            [analyzer]
            type = "llm"
            maxCompletionTokens = 500   # Kotlin names work too
            unknown_field = true
            """.trimIndent())

        assertEquals("Find", config.name)
        assertEquals(1.0, config.lifetime.interval, 0.0)
        assertEquals(500, config.llm.maxCompletionTokens)
        assertEquals(LlmAnalyzer().userPrompt, config.llm.userPrompt)
        }

    @Test
    fun readsTheDocumentedExample() {
        val doc=File("../docs/config-v2.md").readText()
        val example=doc.substringAfter("```toml\n").substringBefore("```")
        val config=Toml.decode(Config.serializer(), example)

        assertEquals("blr", config.command)
        assertEquals(listOf("object"), config.parameters.map { it.name })
        assertTrue(config.parameters[0].rest)
        assertEquals(LensTarget.IMAGE, config.lens.appliesTo)
        assertEquals(com.rastislavkish.vscan.core.ReasoningEffort.LOW, config.llm.reasoningEffort)
        assertEquals(ChangeMarkerMeaning.NO_CHANGE, config.llm.changeDetection.changeMarkerMeaning)
        assertEquals(listOf("say", "play", "vibrate", "toast", "set"), config.operations.map { it::class.java.getAnnotation(SerialName::class.java)?.value ?: it::class.simpleName })
        assertEquals(NumberValue(extract=Extraction.JSON, path="x"), (config.operations[1] as PlayOperation).pan)
        assertEquals(2.0, config.lifetime.interval, 0.0)
        assertEquals(mapOf("announced" to JsonPrimitive(false), "mode" to JsonPrimitive("near")), config.memory)
        Process.validateMemory(config)
        }

    @Test
    fun reportsErrorsWithLines() {
        val error=assertThrows(TomlException::class.java) {
            Toml.decode(Config.serializer(), "name = \"A\"\ncommand = \n")
            }
        assertTrue(error.message, error.message!!.startsWith("Line 2: "))

        assertThrows(TomlException::class.java) {
            Toml.decode(Config.serializer(), "[lifetime]\ncapture = \"sometimes\"\n")
            }
        }
    }
