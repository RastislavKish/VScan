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
    ) {

    companion object {

        val presets=listOf<ProviderParams>(
            ProviderParams("OpenAI",
                "https://api.openai.com/v1",
                ),
            ProviderParams("Anthropic",
                "https://api.anthropic.com/v1",
                ),
            ProviderParams("TogetherAI",
                "https://api.together.xyz/v1",
                ),
            ProviderParams("OpenRouter",
                "https://openrouter.ai/api/v1",
                ),
            ProviderParams("NanoGPT",
                "https://nano-gpt.com/api/v1",
                ),
            )

        }
    }
