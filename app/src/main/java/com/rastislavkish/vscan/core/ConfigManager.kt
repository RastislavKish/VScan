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

import android.content.Context
import android.content.SharedPreferences

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import com.rastislavkish.vscan.core.config.ConfigV1Migration
import com.rastislavkish.vscan.core.config.Lifetime
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.config.ToastOperation

class ConfigManager(
    val preferences: SharedPreferences
    ): CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private var job=Job()

    private var configurations=mutableListOf(baseConfig(), fileDescriptionConfig(), findConfig())
    private val configurationsMutex=Mutex()

    fun addConfig(config: Config): Config {
        val result: Config
        runBlocking{ configurationsMutex.withLock {
            result=addConfigImplementation(config)
            }}
        return result
        }
    fun updateConfig(config: Config) {
        runBlocking{ configurationsMutex.withLock {
            updateConfigImplementation(config)
            }}
        }
    fun deleteConfig(config: Config) {
        runBlocking{ configurationsMutex.withLock {
            deleteConfigImplementation(config)
            }}
        }

    fun getConfig(id: String): Config? {
        var result: Config?=null
        runBlocking { configurationsMutex.withLock {
            for (config in configurations) {
                if (config.id==id) {
                    result=config
                    break
                    }
                }
            }}
        return result
        }
    fun getList(): List<Config> {
        val result: List<Config>
        runBlocking { configurationsMutex.withLock {
            result=configurations.toList()
            }}
        return result
        }

    fun getBaseConfig(): Config = baseConfig()
    fun getFileDescriptionConfig(): Config = fileDescriptionConfig()

    fun load() {
        runBlocking{ configurationsMutex.withLock {
            loadImplementation()
            }}
        }
    fun save() {
        runBlocking{ configurationsMutex.withLock {
            saveImplementation()
            }}
        }

    // The implementations of the public methods without locks

    private fun addConfigImplementation(config: Config): Config {
        val newConfig=config.copy(id=getFreeId(config.name))
        configurations.add(newConfig)
        saveImplementation()
        return newConfig
        }
    private fun updateConfigImplementation(config: Config) {
        for (i in 0 until configurations.size) {
            val existingConfig=configurations[i]

            if (existingConfig.id==config.id) {
                configurations[i]=config
                saveImplementation()
                return
                }
            }

        configurations.add(config)
        saveImplementation()
        }
    private fun deleteConfigImplementation(config: Config) {
        for (i in 0 until configurations.size) {
            if (configurations[i].id==config.id) {
                configurations.removeAt(i)
                saveImplementation()
                return
                }
            }
        }

    private fun loadImplementation() {
        val serializedList=preferences.getString(CONFIGS_KEY, null)

        if (serializedList!=null)
        configurations=json.decodeFromString(serializedList)
        else {
            // The old configs stay under their key, as a backup
            val legacyList=preferences.getString(LEGACY_CONFIGS_KEY, null) ?: return
            configurations=ConfigV1Migration.migrate(legacyList).toMutableList()
            }

        //In case the base config was out of date or missing
        updateConfigImplementation(baseConfig())
        updateConfigImplementation(fileDescriptionConfig())
        updateConfigImplementation(findConfig())
        }
    private fun saveImplementation() {
        preferences.edit()
        .putString(CONFIGS_KEY, json.encodeToString(configurations))
        .commit()
        }

    private fun getFreeId(name: String): String {
        val taken=configurations.map { it.id }.toSet()+builtInConfigIds
        return Ids.generate(name, "config", taken)
        }

    private fun baseConfig(): Config = Config()
    private fun fileDescriptionConfig(): Config {
        // Its output is used as the filename, so it reports nothing itself
        return Config(
            id=FILE_DESCRIPTION_CONFIG_ID,
            name="File description",
            analyzer=LlmAnalyzer(
                model="vscan-gpt-4o-mini",
                userPrompt="Generate a few word description of this image, which could serve as its filename in Pictures folder. Answer with the filename only, no comments and omit the extension.",
                ),
            operations=listOf(),
            )
        }
    private fun findConfig(): Config {
        // Looks for an object every second, reporting the zone it's in whenever that changes
        return Config(
            id=FIND_CONFIG_ID,
            name="Find",
            command="fd",
            description="Reports the horizontal zone of an object",
            parameters=listOf(
                Parameter(name="object", type=ParameterType.STRING, positional=true, rest=true, description="What to look for"),
                ),
            analyzer=LlmAnalyzer(
                model="vscan-qwen-3.8-27b",
                userPrompt="I'm looking for {{ object }}. Imagine the image is horizontally split into 5 equally sized zones - Far left, Left, Center, Right and Far right. Report either the zone in which the object is located, or None if it's not present or visible in the image.",
                ),
            operations=listOf(ToastOperation(condition="changed")),
            lifetime=Lifetime(interval=1.0),
            )
        }

    companion object {

        const val BASE_CONFIG_ID="base"
        const val FILE_DESCRIPTION_CONFIG_ID="file-description"
        const val FIND_CONFIG_ID="find"

        private const val CONFIGS_KEY="configs"
        // The flat configs of versions up to 0.2.x
        private const val LEGACY_CONFIGS_KEY="configurations"

        private val builtInConfigIds=setOf(BASE_CONFIG_ID, FILE_DESCRIPTION_CONFIG_ID, FIND_CONFIG_ID)

        // Newer versions may add fields an older build doesn't know
        // Defaults are stored too, since the base config's definition consists of them, and changing it mustn't change the user's configs
        private val json=Json {
            ignoreUnknownKeys=true
            encodeDefaults=true
            }

        fun isBuiltInConfigId(id: String): Boolean = id in builtInConfigIds

        private var instance: ConfigManager?=null

        fun getInstance(context: Context): ConfigManager {
            if (instance==null) {
                StringIdMigration.ensure(context)
                val preferences=context.getSharedPreferences("VScanConfigurations", Context.MODE_PRIVATE)
                instance=ConfigManager(preferences)
                instance?.load()
                }

            return instance!!
            }
        }
    }
