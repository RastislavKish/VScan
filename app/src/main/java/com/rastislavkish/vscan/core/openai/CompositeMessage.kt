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

import com.rastislavkish.vscan.core.openai.requests.Message as Msg

// A user message of texts and images in any order, as rendered from a prompt template
class CompositeMessage(
    val parts: List<Part>,
    ): Message(
        "user",
        parts.filterIsInstance<Part.Text>().joinToString("") { it.text },
        parts.filterIsInstance<Part.Image>().map { it.image },
        ) {

    sealed class Part {

        data class Text(val text: String): Part()
        data class Image(val image: com.rastislavkish.vscan.core.openai.Image): Part()
        }

    // Edited text goes before the images
    override fun withText(text: String): Message = CompositeMessage(
        listOf(Part.Text(text))+parts.filterIsInstance<Part.Image>(),
        )

    override fun render(): Msg {
        // Some providers reject empty text parts
        val content=parts
        .filter { it !is Part.Text || it.text.isNotBlank() }
        .map { part ->
            when (part) {
                is Part.Text -> com.rastislavkish.vscan.core.openai.requests.Content("text", part.text, null)
                is Part.Image -> part.image.render()
                }
            }

        return Msg(role, content)
        }
    }
