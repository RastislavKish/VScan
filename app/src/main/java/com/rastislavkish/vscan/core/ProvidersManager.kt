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

    private var providers=mutableMapOf<Int, Provider>()

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
    fun updateProvider(provider: Provider) {
        providers[provider.id]=provider
        save()
        }
    fun deleteProvider(provider: Int) {
        providers.remove(provider)
        save()
        }

    fun load() {
        val serializedProviders=preferences.getString("providers", "") ?: return

        if (serializedProviders.isEmpty())
        return

        providers=Json.decodeFromString(serializedProviders)
        }
    fun save() {
        preferences.edit()
        .putString("providers", Json.encodeToString(providers))
        .commit()
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
