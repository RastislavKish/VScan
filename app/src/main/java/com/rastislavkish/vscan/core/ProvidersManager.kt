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

import android.content.Context
import android.content.SharedPreferences

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

class ProvidersManager(
    val preferences: SharedPreferences
    ) {

    private var defaultProvider: Int?=null
    private var providers=mutableMapOf<Int, Provider>()
    private var modelProviderMappings=mutableMapOf<String, Int?>()

    fun addProvider(provider: Provider): Provider {
        val id=getFreeId()
        val newProvider=provider.withId(id)
        providers[id]=newProvider

        save()

        return newProvider
        }
    fun getProvider(id: Int): Provider? {
        return providers.get(id)
        }
    fun getAllProviders(): List<Provider> {
        return providers.values.toList()
        }
    fun getDefaultProvider(): Provider? {
        val defaultProviderId=defaultProvider ?: return null

        return getProvider(defaultProviderId)
        }
    fun updateProvider(provider: Provider) {
        providers[provider.id]=provider
        save()
        }
    fun setDefaultProvider(provider: Int?) {
        defaultProvider=provider
        save()
        }
    fun deleteProvider(provider: Int) {
        providers.remove(provider)

        for (model in modelProviderMappings.keys) {
            if (modelProviderMappings[model]==provider)
            modelProviderMappings[model]=null
            }

        save()
        }

    fun getProviderForModel(model: String): Provider? {
        val provider=modelProviderMappings.get(model)

        if (provider==null) {
            if (modelProviderMappings.containsKey(model))
            return null
            else
            return getDefaultProvider()
            }

        return getProvider(provider)
        }
    fun getAllModelProviderMappings(): List<Pair<String, Provider?>> {
        val result=mutableListOf<Pair<String, Provider?>>()

        for (key in modelProviderMappings.keys) {
            val providerId=modelProviderMappings[key]
            val provider=if (providerId!=null)
            getProvider(providerId)
            else
            null

            result.add(Pair(key, provider))
            }

        return result
        }
    fun mapModelToProvider(model: String, provider: Int?) {
        modelProviderMappings[model]=provider
        save()
        }
    fun deleteModelProviderMapping(model: String) {
        modelProviderMappings.remove(model)
        save()
        }

    fun load() {
        val serializedProviders=preferences.getString("providers", "") ?: ""

        if (!serializedProviders.isEmpty())
        providers=Json.decodeFromString(serializedProviders)

        val serializedDefaultProvider=preferences.getInt("defaultProvider", -1)
        defaultProvider=if (serializedDefaultProvider!=-1)
        serializedDefaultProvider
        else
        null

        val serializedModelProviderMappings=preferences.getString("modelProviderMappings", "") ?: ""

        if (!serializedModelProviderMappings.isEmpty())
        modelProviderMappings=Json.decodeFromString(serializedModelProviderMappings)

        cleanRedundantProviderModels()
        }
    fun save() {
        preferences.edit()
        .putString("providers", Json.encodeToString(providers))
        .putInt("defaultProvider", defaultProvider ?: -1)
        .putString("modelProviderMappings", Json.encodeToString(modelProviderMappings))
        .commit()
        }

    private fun cleanRedundantProviderModels() {
        // This is a cleanup for purposes of backward compatibility
        // Previously, providers would be preload with model definitions if a preset was chosen and these presets get saved as the user configuration
        // Now the presets are separated from the config, and the preconfigured mappings should be removed

        var fixesDone=false

        for ((id, provider) in providers) {
            if (provider.preset==null)
            continue

            val preset=provider.preset!!

            var needsCleaning=false

            for ((vscanId, modelId) in provider.models) {
                if (vscanId.startsWith("vscan-") && preset.models.containsKey(vscanId) && preset.models[vscanId]==modelId) {
                    needsCleaning=true
                    break
                    }

                }

            if (!needsCleaning)
            continue

            val models=mutableMapOf<String, String>()

            for ((vscanId, modelId) in provider.models) {
                if (vscanId.startsWith("vscan-") && preset.models.containsKey(vscanId) && preset.models[vscanId]==modelId)
                continue

                models[vscanId]=modelId
                }

            val newProvider=provider.withModels(models)

            providers[id]=newProvider

            fixesDone=true
            }

        if (fixesDone)
        save()
        }

    private fun getFreeId(): Int {
        var maxUsedId=-1
        for (provider in providers.values) {
            if (provider.id>maxUsedId)
            maxUsedId=provider.id
            }
        return maxUsedId+1
        }

    companion object {

        private var instance: ProvidersManager?=null

        fun getInstance(context: Context): ProvidersManager {
            if (instance==null) {
                val preferences=context.getSharedPreferences("VScanProviders", Context.MODE_PRIVATE)
                instance=ProvidersManager(preferences)
                instance?.load()
                }

            return instance!!
            }
        }
    }
