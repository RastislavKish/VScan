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
package com.rastislavkish.vscan.core

import org.junit.Assert.*
import org.junit.Test

class ProviderTest {

    private val openAiUrl="https://api.openai.com/v1"

    private fun provider(baseUrl: String, models: Map<String, String> = mapOf()) = Provider(
        name="Test",
        baseUrl=baseUrl,
        apiKey="",
        models=models,
        )

    @Test
    fun findsPresetByBaseUrl() {
        assertEquals("OpenAI", provider(openAiUrl).preset?.name)
        assertNull(provider("http://localhost:8080/v1").preset)
        }

    @Test
    fun getModelIdResolvesUserMappingThenPresetThenVerbatim() {
        val provider=provider(openAiUrl, mapOf("vscan-gpt-4o" to "my-gpt-4o"))

        assertEquals("my-gpt-4o", provider.getModelId("vscan-gpt-4o"))
        assertEquals("gpt-4o-mini", provider.getModelId("vscan-gpt-4o-mini"))
        assertEquals("custom-model", provider.getModelId("custom-model"))
        assertEquals("vscan-unknown", provider.getModelId("vscan-unknown"))
        }

    @Test
    fun extendedModelsOverlayUserMappingsOnPreset() {
        val provider=provider(openAiUrl, mapOf("vscan-gpt-4o" to "my-gpt-4o", "custom" to "custom"))
        val extended=provider.getExtendedModels()

        assertEquals("my-gpt-4o", extended["vscan-gpt-4o"])
        assertEquals("gpt-4o-mini", extended["vscan-gpt-4o-mini"])
        assertEquals("custom", extended["custom"])
        }

    @Test
    fun baseModelsDropEntriesIdenticalToPreset() {
        val provider=provider(openAiUrl)
        val extended=mapOf(
            "vscan-gpt-4o" to "gpt-4o",
            "vscan-gpt-4o-mini" to "my-mini",
            "custom" to "custom",
            )

        assertEquals(mapOf("vscan-gpt-4o-mini" to "my-mini", "custom" to "custom"), provider.getBaseModels(extended))
        }

    @Test
    fun baseModelsRoundTripExtendedModels() {
        val models=mapOf("vscan-gpt-4o" to "my-gpt-4o", "custom" to "custom")
        val provider=provider(openAiUrl, models)

        assertEquals(models, provider.getBaseModels(provider.getExtendedModels()))
        }

    @Test
    fun withoutPresetBaseModelsAreUnchanged() {
        val models=mapOf("vscan-gpt-4o" to "gpt-4o")

        assertEquals(models, provider("http://localhost:8080/v1").getBaseModels(models))
        }

    }
