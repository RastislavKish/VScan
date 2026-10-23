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

import com.rastislavkish.vscan.core.files.BrokenFile
import com.rastislavkish.vscan.core.files.VScanFiles
import com.rastislavkish.vscan.core.toml.TomlException

// The providers live in providers.toml, their API keys in secrets.toml, so the providers can be shared without them
// The preferences hold the providers of the versions before the files, moved to the files once
class ProvidersManager(
    val preferences: SharedPreferences,
    private val files: VScanFiles,
    ) {

    @Serializable
    private data class ProvidersFile(
        val defaultProvider: String?=null,
        val providers: Map<String, ProviderEntry> = mapOf(),
        // A model mapped to "" has no provider
        val modelProviders: Map<String, String> = mapOf(),
        )
    @Serializable
    private data class ProviderEntry(
        val name: String="",
        val baseUrl: String="",
        val models: Map<String, String> = mapOf(),
        )
    @Serializable
    private data class SecretsFile(
        val providers: Map<String, ProviderSecrets> = mapOf(),
        )
    @Serializable
    private data class ProviderSecrets(
        val apiKey: String="",
        )

    // The error of a file that can't be read, which the app then doesn't overwrite
    private var fileError: String?=null

    private var defaultProvider: String?=null
    private var providers=mutableMapOf<String, Provider>()
    private var modelProviderMappings=mutableMapOf<String, String?>()

    fun addProvider(provider: Provider): Provider {
        val id=getFreeId(provider.name)
        val newProvider=provider.withId(id)
        providers[id]=newProvider

        save()

        return newProvider
        }
    fun getProvider(id: String): Provider? {
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
    fun setDefaultProvider(provider: String?) {
        defaultProvider=provider
        save()
        }
    fun deleteProvider(provider: String) {
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
    fun mapModelToProvider(model: String, provider: String?) {
        modelProviderMappings[model]=provider
        save()
        }
    fun deleteModelProviderMapping(model: String) {
        modelProviderMappings.remove(model)
        save()
        }

    fun isModelSupported(model: String): Boolean {
        for (provider in providers.values)
        if (provider.supportsModel(model))
        return true

        return false
        }
    fun isModelAssigned(model: String): Boolean {
        return getProviderForModel(model)!=null
        }

    fun load() {
        if (!preferences.getBoolean(MOVED_TO_FILES_KEY, false))
        moveToFiles()

        fileError=null
        val providersFile=readFile(VScanFiles.PROVIDERS, ProvidersFile.serializer()) ?: ProvidersFile()
        val secretsFile=readFile(VScanFiles.SECRETS, SecretsFile.serializer()) ?: SecretsFile()

        providers=providersFile.providers.mapValues { (id, entry) ->
            Provider(id, entry.name, entry.baseUrl, secretsFile.providers[id]?.apiKey ?: "", entry.models)
            }.toMutableMap()
        defaultProvider=providersFile.defaultProvider
        modelProviderMappings=providersFile.modelProviders.mapValues { it.value.ifEmpty { null } }.toMutableMap()
        }
    // Null when the file doesn't exist or is broken
    private fun <T> readFile(path: String, serializer: KSerializer<T>): T? {
        try {
            return files.readToml(path, serializer)
            }
        catch (e: TomlException) {
            val error=e.message ?: "Invalid file"
            fileError="$path: $error"
            files.reportBrokenFile(BrokenFile(path, error, "VScan uses no providers or API keys from it, and doesn't save changes to the providers until the file is fixed, so the file isn't overwritten."))
            return null
            }
        }

    fun save() {
        val error=fileError
        if (error!=null) {
            files.report("The changes of the providers can't be saved until the file is fixed. $error")
            return
            }

        val providersFile=ProvidersFile(
            defaultProvider,
            providers.mapValues { (_, provider) -> ProviderEntry(provider.name, provider.baseUrl, provider.models) },
            modelProviderMappings.mapValues { it.value ?: "" },
            )
        val secretsFile=SecretsFile(providers.mapValues { ProviderSecrets(it.value.apiKey) })

        files.writeToml(VScanFiles.PROVIDERS, ProvidersFile.serializer(), providersFile, PROVIDERS_HEADER)
        files.writeToml(VScanFiles.SECRETS, SecretsFile.serializer(), secretsFile, SECRETS_HEADER)
        }

    // The providers stored in the preferences become files, the preferences staying as a backup
    private fun moveToFiles() {
        val serializedProviders=preferences.getString("providers", "") ?: ""

        if (!serializedProviders.isEmpty())
        providers=Json.decodeFromString(serializedProviders)

        defaultProvider=preferences.getString("defaultProviderId", null)

        val serializedModelProviderMappings=preferences.getString("modelProviderMappings", "") ?: ""

        if (!serializedModelProviderMappings.isEmpty())
        modelProviderMappings=Json.decodeFromString(serializedModelProviderMappings)

        cleanRedundantProviderModels()

        if (providers.isNotEmpty() || defaultProvider!=null || modelProviderMappings.isNotEmpty())
        save()

        preferences.edit()
        .putBoolean(MOVED_TO_FILES_KEY, true)
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

    private fun getFreeId(name: String): String {
        return Ids.generate(name, "provider", providers.keys)
        }

    companion object {

        private const val MOVED_TO_FILES_KEY="movedToFiles"
        private const val PROVIDERS_HEADER="The VScan providers, their API keys are in secrets.toml\nSaving the providers in VScan rewrites this file, leaving out comments"
        private const val SECRETS_HEADER="The API keys of the VScan providers, keep this file private, e.g. out of git repositories\nSaving the providers in VScan rewrites this file, leaving out comments"

        private var instance: ProvidersManager?=null

        fun getInstance(context: Context): ProvidersManager {
            if (instance==null) {
                StringIdMigration.ensure(context)
                val preferences=context.getSharedPreferences("VScanProviders", Context.MODE_PRIVATE)
                instance=ProvidersManager(preferences, VScanFiles.getInstance(context))
                instance?.load()
                }

            return instance!!
            }
        }
    }
