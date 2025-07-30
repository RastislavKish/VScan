/*
* Copyright (C) 2025 Rastislav Kish
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

package com.rastislavkish.vscan.ui.providerpresetselectionactivity

import kotlinx.serialization.*

@Serializable
data class ProviderParams(
    val name: String,
    val baseUrl: String,
    val models: Map<String, String>,
    ) {

    companion object {

        val presets=listOf<ProviderParams>(
            ProviderParams("OpenAI",
                "https://api.openai.com/v1",
                mapOf(
                    "vscan-gpt-4o" to "gpt-4o",
                    "vscan-gpt-4.1" to "gpt-4.1",
                    "vscan-gpt-4o-mini" to "gpt-4o-mini",
                    ),
                ),
            ProviderParams("Anthropic",
                "https://api.anthropic.com/v1",
                mapOf(
                    "vscan-claude-4-opus" to "claude-opus-4-0",
                    "vscan-claude-4-sonnet" to "claude-sonnet-4-0",
                    "vscan-claude-3.7-sonnet" to "claude-3-7-sonnet-latest",
                    "vscan-claude-3.5-sonnet" to "claude-3-5-sonnet-latest",
                    "vscan-claude-3.5-haiku" to "claude-3-5-haiku-latest",
                    ),
                ),
            ProviderParams("TogetherAI",
                "https://api.together.xyz/v1",
                mapOf(

                    ),
                ),
            ProviderParams("OpenRouter",
                "https://openrouter.ai/api/v1",
                mapOf(
                    "vscan-gpt-4o" to "openai/gpt-4o",
                    "vscan-gpt-4.1" to "openai/gpt-4.1",
                    "vscan-gpt-4o-mini" to "openai/gpt-4o-mini",
                    "vscan-claude-4-opus" to "anthropic/claude-opus-4",
                    "vscan-claude-4-sonnet" to "anthropic/claude-sonnet-4",
                    "vscan-claude-3.7-sonnet" to "anthropic/claude-3.7-sonnet",
                    "vscan-claude-3.5-sonnet" to "anthropic/claude-3.5-sonnet",
                    "vscan-claude-3.5-haiku" to "anthropic/claude-3.5-haiku",
                    ),
                ),
            ProviderParams("NanoGPT",
                "https://nano-gpt.com/api/v1",
                mapOf(
                    "vscan-gpt-4o" to "gpt-4o",
                    "vscan-gpt-4.1" to "openai/gpt-4.1",
                    "vscan-gpt-4o-mini" to "gpt-4o-mini",
                    "vscan-claude-4-opus" to "claude-opus-4-20250514",
                    "vscan-claude-4-sonnet" to "claude-sonnet-4-20250514",
                    "vscan-claude-3.7-sonnet" to "claude-3-7-sonnet-20250219",
                    "vscan-claude-3.5-sonnet" to "claude-3-5-sonnet-20241022",
                    "vscan-claude-3.5-haiku" to "claude-3-5-haiku-20241022",
                    ),
                ),
            )

        }
    }
