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

package com.rastislavkish.vscan.ui.modelselectionactivity

data class Model(
    val name: String,
    val author: String,
    val family: String,
    val size: String,
    val identifier: String,
    ) {

    companion object {

        val presets=listOf<Model>(
            Model("GPT 4O",
                "OpenAI",
                "GPT",
                "Unknown",
                "gpt-4o",
                ),
            Model("GPT 4.1",
                "OpenAI",
                "GPT",
                "Unknown",
                "gpt-4.1",
                ),
            Model("GPT 4O mini",
                "OpenAI",
                "GPT",
                "Unknown",
                "gpt-4o-mini",
                ),
            Model("Claude 4 Opus",
                "Anthropic",
                "Claude",
                "Unknown",
                "claude-opus-4-0",
                ),
            Model("Claude 4 Sonnet",
                "Anthropic",
                "Claude",
                "Unknown",
                "claude-sonnet-4-0",
                ),
            Model("Claude 3.7 Sonnet",
                "Anthropic",
                "Claude",
                "Unknown",
                "claude-3-7-sonnet-latest",
                ),
            Model("Claude 3.5 Sonnet",
                "Anthropic",
                "Claude",
                "Unknown",
                "claude-3-5-sonnet-latest",
                ),
            Model("Claude 3.5 Haiku",
                "Anthropic",
                "Claude",
                "Unknown",
                "claude-3-5-haiku-latest",
                ),
            )

        }
    }
