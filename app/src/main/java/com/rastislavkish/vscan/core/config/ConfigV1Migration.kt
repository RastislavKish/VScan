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
import kotlinx.serialization.json.Json

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.ReasoningEffort
import com.rastislavkish.vscan.core.template.Template

// Converts the flat configs of versions up to 0.2.x into the current shape
// Their behavior stays the same: a single-shot LLM analysis of the image, its result shown as a toast
object ConfigV1Migration {

    // The defaults are the ones of the old Config, since the old versions didn't store default values
    @Serializable
    private data class LegacyConfig(
        val id: String=ConfigManager.BASE_CONFIG_ID,
        val name: String="Base",
        val systemPrompt: String="",
        val userPrompt: String="What's in the image?",
        val flashlightMode: String="DEFAULT",
        val model: String="vscan-gpt-4o",
        val maxCompletionTokens: Int=300,
        val reasoningEffort: String?=null,
        )

    // Removed fields like highRes or camera remain in the old data
    private val json=Json { ignoreUnknownKeys=true }

    fun migrate(serializedList: String): List<Config> {
        val legacyConfigs: List<LegacyConfig> = json.decodeFromString(fixModelNames(serializedList))
        return legacyConfigs.map(::migrate)
        }

    private fun migrate(legacy: LegacyConfig): Config {
        return Config(
            id=legacy.id,
            name=legacy.name,
            analyzer=LlmAnalyzer(
                model=legacy.model,
                // The prompts become templates, which mustn't change their meaning
                systemPrompt=Template.escape(legacy.systemPrompt),
                userPrompt=Template.escape(legacy.userPrompt),
                maxCompletionTokens=legacy.maxCompletionTokens,
                reasoningEffort=legacy.reasoningEffort?.let { name -> ReasoningEffort.entries.firstOrNull { it.name==name } },
                ),
            operations=listOf(ToastOperation()),
            lifetime=Lifetime(
                flashlight=FlashlightMode.entries.firstOrNull { it.name==legacy.flashlightMode } ?: FlashlightMode.DEFAULT,
                ),
            )
        }

    // Even older versions stored the models as enum names
    private fun fixModelNames(serializedList: String): String {
        return serializedList
        .replace("\"model\":\"GPT_4O\"", "\"model\":\"vscan-gpt-4o\"")
        .replace("\"model\":\"GPT_4O_MINI\"", "\"model\":\"vscan-gpt-4o-mini\"")
        }
    }
