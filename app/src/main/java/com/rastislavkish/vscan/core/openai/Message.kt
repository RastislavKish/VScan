/*
* Copyright (C) 2023 Rastislav Kish
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
import com.rastislavkish.vscan.core.openai.requests.Content

abstract class Message(
    val role: String,
    val text: String,
    val attachments: List<Attachment>
    ) {

    open fun render(): Msg {
        val content=mutableListOf<Content>(
            Content(
                "text",
                text,
                null,
                ),
            )

        for (attachment in attachments) {
            content.add(attachment.render())
            }

        return Msg(
            role,
            content.toList(),
            )
        }
    }
