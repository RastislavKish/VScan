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

    @Transient
    val preset: ProviderParams?=ProviderParams.presets.find { it.baseUrl==baseUrl }

    fun getModelId(vscanId: String): String {
        if (!vscanId.startsWith("vscan-"))
        return vscanId

        val modelId=models.get(vscanId)
        ?: preset?.models?.get(vscanId)
        ?: vscanId

        return modelId
        }

    fun getExtendedModels(): Map<String, String> {
        val extended=preset?.models?.toMutableMap()
        ?: mutableMapOf<String, String>()

        for ((vscanId, modelId) in models)
        extended[vscanId]=modelId

        return extended
        }
    fun getBaseModels(extendedModels: Map<String, String>): Map<String, String> {
        // The reverse variant of getExtendedModels

        val preset=preset ?: return extendedModels

        val base=mutableMapOf<String, String>()

        for ((vscanId, modelId) in extendedModels) {
            if (vscanId.startsWith("vscan-") && preset.models.containsKey(vscanId) && preset.models[vscanId]==modelId)
            continue

            base[vscanId]=modelId
            }

        return base
        }

    fun withId(id: Int) = Provider(
        id,
        name,
        baseUrl,
        apiKey,
        models,
        )
    fun withModels(models: Map<String, String>) = Provider(
        id,
        name,
        baseUrl,
        apiKey,
        models,
        )

    }
