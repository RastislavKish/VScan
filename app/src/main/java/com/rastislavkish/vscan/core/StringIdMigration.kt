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
package com.rastislavkish.vscan.core

import android.content.Context
import android.content.SharedPreferences

import kotlinx.serialization.json.*

// Migrates the integer ids of configs and providers used up to version 0.2.x to string ids
// Replaced values are backed up under "<key>_v1" keys
// Each store is rewritten in a single commit, and the id mappings are derived deterministically from the legacy data, so an interrupted migration can be safely rerun
class StringIdMigration(
    val configPreferences: SharedPreferences,
    val settingsPreferences: SharedPreferences,
    val providersPreferences: SharedPreferences,
    val sorterPreferences: SharedPreferences,
    ) {

    fun run() {
        val configIds=legacyConfigIds()

        migrateSettings(configIds)
        migrateSorter(configIds)
        migrateProviders()
        // The config mapping is derived from the legacy configs, so they must be rewritten last
        migrateConfigs(configIds)
        }

    // Configs

    private fun legacyConfigList(): List<JsonObject>? {
        val serialized=configPreferences.getString("configurations", null) ?: return null
        if (serialized.isEmpty())
        return null

        val list=Json.parseToJsonElement(serialized).jsonArray.map { it.jsonObject }

        // Ids equal to the default are omitted from the serialized form, so only explicit numeric ids tell the formats apart
        if (list.none { isLegacyId(it["id"]) })
        return null

        return list
        }
    private fun legacyConfigIds(): Map<Int, String> {
        val result=mutableMapOf(
            -1 to ConfigManager.BASE_CONFIG_ID,
            -3 to ConfigManager.FILE_DESCRIPTION_CONFIG_ID,
            )
        val taken=result.values.toMutableSet()

        for (config in legacyConfigList() ?: listOf()) {
            val legacyId=config["id"]?.jsonPrimitive?.int ?: -1
            if (legacyId in result)
            continue

            val name=config["name"]?.jsonPrimitive?.content ?: "Base"
            val id=Ids.generate(name, "config", taken)
            taken.add(id)
            result[legacyId]=id
            }

        return result
        }
    private fun migrateConfigs(configIds: Map<Int, String>) {
        val list=legacyConfigList() ?: return

        val migrated=JsonArray(list.map { config ->
            val legacyId=config["id"]?.jsonPrimitive?.int ?: -1
            JsonObject(config+("id" to JsonPrimitive(configIds[legacyId]!!)))
            })

        val editor=configPreferences.edit()
        backup(configPreferences, editor, "configurations")
        editor.putString("configurations", migrated.toString())
        editor.commit()
        }

    // Settings

    private fun migrateSettings(configIds: Map<Int, String>) {
        val editor=settingsPreferences.edit()
        var changed=false

        // Unknown ids are dropped, so the setting falls back to its default, just like a missing config did before
        for ((legacyKey, key) in legacySettingsConfigKeys) {
            if (!settingsPreferences.contains(legacyKey))
            continue

            val legacyId=settingsPreferences.getInt(legacyKey, -1)
            editor.putInt("${legacyKey}_v1", legacyId)
            editor.remove(legacyKey)

            val id=configIds[legacyId]
            if (id!=null)
            editor.putString(key, id)

            changed=true
            }

        for (key in actionKeys) {
            val serialized=settingsPreferences.getString(key, null) ?: continue
            val migrated=migrateAction(serialized, configIds) ?: continue

            backup(settingsPreferences, editor, key)
            editor.putString(key, migrated)
            changed=true
            }

        if (changed)
        editor.commit()
        }
    private fun migrateAction(serialized: String, configIds: Map<Int, String>): String? {
        val action=Json.parseToJsonElement(serialized) as? JsonObject ?: return null
        val config=action["config"]

        if (!isLegacyId(config))
        return null

        // A missing config falls back to the base config, which is also the default of the field
        val id=configIds[config!!.jsonPrimitive.int]
        val migrated=if (id!=null)
        JsonObject(action+("config" to JsonPrimitive(id)))
        else
        JsonObject(action-"config")

        return migrated.toString()
        }

    // Config sorter

    private fun migrateSorter(configIds: Map<Int, String>) {
        val serialized=sorterPreferences.getString("selectionData", null) ?: return
        if (serialized.isEmpty())
        return

        val selectionData=Json.parseToJsonElement(serialized).jsonObject

        if (selectionData.keys.none { isLegacyKey(it) })
        return

        val migrated=mutableMapOf<String, JsonElement>()
        for ((key, value) in selectionData) {
            val id=if (isLegacyKey(key)) configIds[key.toInt()] else key
            if (id!=null)
            migrated[id]=value
            }

        val editor=sorterPreferences.edit()
        backup(sorterPreferences, editor, "selectionData")
        editor.putString("selectionData", JsonObject(migrated).toString())
        editor.commit()
        }

    // Providers

    private fun migrateProviders() {
        val serializedProviders=providersPreferences.getString("providers", null) ?: ""
        val providers=if (!serializedProviders.isEmpty())
        Json.parseToJsonElement(serializedProviders).jsonObject
        else
        JsonObject(mapOf())

        val serializedMappings=providersPreferences.getString("modelProviderMappings", null) ?: ""
        val mappings=if (!serializedMappings.isEmpty())
        Json.parseToJsonElement(serializedMappings).jsonObject
        else
        JsonObject(mapOf())

        val hasLegacyDefault=providersPreferences.contains("defaultProvider")
        val hasLegacyProviders=providers.keys.any { isLegacyKey(it) }
        val hasLegacyMappings=mappings.values.any { isLegacyId(it) }

        if (!hasLegacyDefault && !hasLegacyProviders && !hasLegacyMappings)
        return

        val providerIds=mutableMapOf<Int, String>()
        val taken=providers.keys.filter { !isLegacyKey(it) }.toMutableSet()
        for ((key, provider) in providers) {
            if (!isLegacyKey(key))
            continue

            val id=Ids.generate(provider.jsonObject["name"]?.jsonPrimitive?.content ?: "", "provider", taken)
            taken.add(id)
            providerIds[key.toInt()]=id
            }

        val editor=providersPreferences.edit()

        if (hasLegacyProviders) {
            val migrated=providers.entries.associate { (key, provider) ->
                val id=if (isLegacyKey(key)) providerIds[key.toInt()]!! else key
                id to JsonObject(provider.jsonObject+("id" to JsonPrimitive(id)))
                }

            backup(providersPreferences, editor, "providers")
            editor.putString("providers", JsonObject(migrated).toString())
            }

        if (hasLegacyDefault) {
            val legacyDefault=providersPreferences.getInt("defaultProvider", -1)
            editor.putInt("defaultProvider_v1", legacyDefault)
            editor.remove("defaultProvider")

            val id=providerIds[legacyDefault]
            if (id!=null)
            editor.putString("defaultProviderId", id)
            }

        if (hasLegacyMappings) {
            // A mapping to a deleted provider resolved to no provider, which an explicit null preserves
            val migrated=mappings.mapValues { (_, provider) ->
                if (isLegacyId(provider))
                providerIds[provider.jsonPrimitive.int]?.let { JsonPrimitive(it) } ?: JsonNull
                else
                provider
                }

            backup(providersPreferences, editor, "modelProviderMappings")
            editor.putString("modelProviderMappings", JsonObject(migrated).toString())
            }

        editor.commit()
        }

    // Helpers

    private fun isLegacyId(element: JsonElement?): Boolean {
        val primitive=element as? JsonPrimitive ?: return false
        return primitive !is JsonNull && !primitive.isString
        }
    private fun isLegacyKey(key: String): Boolean = key.toIntOrNull()!=null

    private fun backup(preferences: SharedPreferences, editor: SharedPreferences.Editor, key: String) {
        val backupKey="${key}_v1"
        if (preferences.contains(backupKey))
        return

        editor.putString(backupKey, preferences.getString(key, null))
        }

    companion object {

        private val legacySettingsConfigKeys=listOf(
            "defaultConfigId" to "defaultConfig",
            "shareConfigId" to "shareConfig",
            "fileDescriptionConfigId" to "fileDescriptionConfig",
            )
        private val actionKeys=listOf("shakeAction", "volumeUpPressAction", "volumeDownPressAction")

        private var done=false

        @Synchronized
        fun ensure(context: Context) {
            if (done)
            return

            StringIdMigration(
                context.getSharedPreferences("VScanConfigurations", Context.MODE_PRIVATE),
                context.getSharedPreferences("VScanSettings", Context.MODE_PRIVATE),
                context.getSharedPreferences("VScanProviders", Context.MODE_PRIVATE),
                context.getSharedPreferences("VScanConfigSorter", Context.MODE_PRIVATE),
                ).run()

            done=true
            }
        }
    }
