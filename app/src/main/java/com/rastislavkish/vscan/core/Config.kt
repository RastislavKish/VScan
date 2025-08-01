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

import com.rastislavkish.vscan.core.openai.SystemMessage

@Serializable
data class Config(
    val id: Int=-1,
    val name: String="Base",
    val systemPrompt: String="",
    val userPrompt: String="What's in the image?",
    val highRes: Boolean=false,
    val flashlightMode: FlashlightMode=FlashlightMode.DEFAULT,
    val camera: UsedCamera=UsedCamera.BACK_CAMERA,
    val model: String="vscan-gpt-4o",
    ) {

    val systemPromptOrNull: SystemMessage?
    get() = if (!systemPrompt.isEmpty()) SystemMessage(systemPrompt) else null

    fun withId(id: Int): Config = Config(
        id,
        name,
        systemPrompt,
        userPrompt,
        highRes,
        flashlightMode,
        camera,
        model,
        )
    fun withName(name: String): Config = Config(
        id,
        name,
        systemPrompt,
        userPrompt,
        highRes,
        flashlightMode,
        camera,
        model,
        )
    fun withSystemPrompt(systemPrompt: String): Config = Config(
        id,
        name,
        systemPrompt,
        userPrompt,
        highRes,
        flashlightMode,
        camera,
        model,
        )
    fun withUserPrompt(userPrompt: String): Config = Config(
        id,
        name,
        systemPrompt,
        userPrompt,
        highRes,
        flashlightMode,
        camera,
        model,
        )

    fun withHighRes(highRes: Boolean): Config = Config(
        id,
        name,
        systemPrompt,
        userPrompt,
        highRes,
        flashlightMode,
        camera,
        model,
        )
    fun withFlashlightMode(flashlightMode: FlashlightMode): Config = Config(
        id,
        name,
        systemPrompt,
        userPrompt,
        highRes,
        flashlightMode,
        camera,
        model,
        )

    fun withCamera(camera: UsedCamera): Config = Config(
        id,
        name,
        systemPrompt,
        userPrompt,
        highRes,
        flashlightMode,
        camera,
        model,
        )
    fun withModel(model: String): Config = Config(
        id,
        name,
        systemPrompt,
        userPrompt,
        highRes,
        flashlightMode,
        camera,
        model,
        )

    }
