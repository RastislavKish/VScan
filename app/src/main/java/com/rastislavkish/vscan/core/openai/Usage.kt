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

package com.rastislavkish.vscan.core.openai

data class Usage(
    val promptTokens: Int=0,
    val reasoningTokens: Int=0,
    val completionTokens: Int=0,
    val totalTokens: Int=0,
    ) {

    operator fun plus(other: Usage): Usage {
        return Usage(
            promptTokens+other.promptTokens,
            reasoningTokens+other.reasoningTokens,
            completionTokens+other.completionTokens,
            totalTokens+other.totalTokens,
            )
        }
    }
