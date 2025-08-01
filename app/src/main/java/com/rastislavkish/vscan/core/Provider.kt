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

import kotlinx.serialization.*

@Serializable
data class Provider(
    val id: Int=-1,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    // A map of VScan models supported by the provider, maps VScan model ID to its respective provider model ID
    val models: Map<String, String>,
    ) {

    fun getModelId(vscanId: String): String {
        if (!vscanId.startsWith("vscan-"))
        return vscanId

        val modelId=models.get(vscanId)

        return if (modelId!=null)
        modelId
        else
        vscanId
        }

    fun withId(id: Int) = Provider(
        id,
        name,
        baseUrl,
        apiKey,
        models,
        )

    }
