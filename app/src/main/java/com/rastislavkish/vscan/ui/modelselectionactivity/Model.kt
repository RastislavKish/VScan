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
            Model("GPT 4O mini",
                "OpenAI",
                "GPT",
                "Unknown",
                "gpt-4o-mini",
                ),
            )

        }
    }
