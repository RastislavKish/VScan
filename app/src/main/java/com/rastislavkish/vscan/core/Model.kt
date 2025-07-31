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

package com.rastislavkish.vscan.core

data class Model(
    val name: String,
    val author: String,
    val family: String,
    val size: String,
    val identifier: String,
    ) {

    companion object {

        val presets=listOf<Model>(
            Model("GPT 4.1",
                "OpenAI",
                "GPT",
                "Unknown",
                "vscan-gpt-4.1",
                ),
            Model("GPT 4.1-mini",
                "OpenAI",
                "GPT",
                "Unknown",
                "vscan-gpt-4.1-mini",
                ),
            Model("GPT 4.1-nano",
                "OpenAI",
                "GPT",
                "Unknown",
                "vscan-gpt-4.1-nano",
                ),
            Model("GPT 4O",
                "OpenAI",
                "GPT",
                "Unknown",
                "vscan-gpt-4o",
                ),
            Model("GPT 4O mini",
                "OpenAI",
                "GPT",
                "Unknown",
                "vscan-gpt-4o-mini",
                ),
            Model("Claude 4 Opus",
                "Anthropic",
                "Claude",
                "Unknown",
                "vscan-claude-4-opus",
                ),
            Model("Claude 4 Sonnet",
                "Anthropic",
                "Claude",
                "Unknown",
                "vscan-claude-4-sonnet",
                ),
            Model("Claude 3.7 Sonnet",
                "Anthropic",
                "Claude",
                "Unknown",
                "vscan-claude-3.7-sonnet",
                ),
            Model("Claude 3.5 Sonnet",
                "Anthropic",
                "Claude",
                "Unknown",
                "vscan-claude-3.5-sonnet",
                ),
            Model("Claude 3.5 Haiku",
                "Anthropic",
                "Claude",
                "Unknown",
                "vscan-claude-3.5-haiku",
                ),
            Model("Gemini 2.5 pro",
                "Google",
                "Gemini",
                "Unknown",
                "vscan-gemini-2.5-pro",
                ),
            Model("Gemini 2.5 flash",
                "Google",
                "Gemini",
                "Unknown",
                "vscan-gemini-2.5-flash",
                ),
            Model("Qwen 2.5 VL 72B",
                "Alibaba",
                "Qwen",
                "72B",
                "vscan-qwen-2.5-vl-72b",
                ),
            Model("Qwen 2.5 VL 32B",
                "Alibaba",
                "Qwen",
                "32B",
                "vscan-qwen-2.5-vl-32b",
                ),
            Model("Gemma 3 27B",
                "Google",
                "Gemma",
                "27B",
                "vscan-gemma-3-27b",
                ),
            Model("Gemma 3 12B",
                "Google",
                "Gemma",
                "12B",
                "vscan-gemma-3-12b",
                ),
            Model("Gemma 3 4B",
                "Google",
                "Gemma",
                "4B",
                "vscan-gemma-3-4b",
                ),
            Model("Llama 4 Maverick",
                "Meta",
                "Llama",
                "400B A17B",
                "vscan-llama-4-maverick",
                ),
            Model("Llama 4 Scout",
                "Meta",
                "Llama",
                "109B A17B",
                "vscan-llama-4-scout",
                ),
            Model("Molmo 72B",
                "Allen AI",
                "Molmo",
                "72B",
                "vscan-molmo-72b",
                ),
            Model("Molmo 7B D",
                "Allen AI",
                "Molmo",
                "7B",
                "vscan-molmo-7b-d",
                ),
            Model("Molmo 7B O",
                "Allen AI",
                "Molmo",
                "7B",
                "vscan-molmo-7b-o",
                ),
            Model("InternVL 3 78B",
                "OpenGVLab",
                "InternVL",
                "78B",
                "vscan-internvl-3-78b",
                ),
            Model("InternVL 3 14B",
                "OpenGVLab",
                "InternVL",
                "14B",
                "vscan-internvl-3-14b",
                ),
            Model("InternVL 3 2B",
                "OpenGVLab",
                "InternVL",
                "2B",
                "vscan-internvl-3-2b",
                ),
            )

        private val idToModelMap: Map<String, Model>
        init {
            val map=mutableMapOf<String, Model>()

            for (model in presets)
            map.put(model.identifier, model)

            idToModelMap=map
            }

        fun idToName(id: String): String {
            if (!id.startsWith("vscan-"))
            return id

            val model=idToModelMap.get(id)

            return if (model!=null)
            model.name
            else
            id
            }

        fun idToKnownModel(id: String): Model? {
            if (!id.startsWith("vscan-"))
            return null

            return idToModelMap.get(id)
            }

        fun idToModel(id: String): Model
        = idToKnownModel(id) ?: Model(id,
            "unknown",
            "Unknown",
            "Unknown",
            id,
            )

        }
    }
