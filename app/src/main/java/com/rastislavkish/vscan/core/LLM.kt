/*
* Copyright (C) 2024 Rastislav Kish
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

import kotlinx.serialization.*

@Serializable
enum class LLM {
    GPT_4O,
    GPT_4O_MINI;

    val identifier: String
    get() = when (this) {
        LLM.GPT_4O -> "gpt-4o"
        LLM.GPT_4O_MINI -> "gpt-4o-mini"
        }

    }
