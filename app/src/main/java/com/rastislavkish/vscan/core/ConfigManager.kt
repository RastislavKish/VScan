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
import com.rastislavkish.vscan.core.files.BrokenFile
import com.rastislavkish.vscan.core.files.VScanFiles
import com.rastislavkish.vscan.core.toml.TomlException
import com.rastislavkish.vscan.core.config.Lifetime
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.config.ToastOperation

// A config file that can't be read
data class BrokenConfig(
    val id: String,
    val error: String,
    )

// The built-in configs, defined in code, and the user's ones, each in its folder configs/<id>/config.toml
// The preferences hold the configs of the versions before the files, moved to the files once
class ConfigManager(
    val preferences: SharedPreferences,
    private val files: VScanFiles,
    ): CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private var job=Job()

    private var configurations=builtInConfigs().toMutableList()
    // Never overwritten by the app, and their ids stay taken
    private var brokenConfigs=listOf<BrokenConfig>()
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

    fun getBrokenConfigs(): List<BrokenConfig> {
        val result: List<BrokenConfig>
        runBlocking { configurationsMutex.withLock {
            result=brokenConfigs
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

    // The implementations of the public methods without locks

    private fun addConfigImplementation(config: Config): Config {
        val newConfig=config.copy(id=getFreeId(config.name))
        configurations.add(newConfig)
        write(newConfig)
        return newConfig
        }
    private fun updateConfigImplementation(config: Config) {
        // The built-in configs come from the code
        if (isBuiltInConfigId(config.id))
        return

        val index=configurations.indexOfFirst { it.id==config.id }
        if (index>=0)
        configurations[index]=config
        else
        configurations.add(config)
        write(config)
        }
    private fun deleteConfigImplementation(config: Config) {
        if (isBuiltInConfigId(config.id))
        return

        configurations.removeAll { it.id==config.id }
        // With its assets
        files.delete("${VScanFiles.CONFIGS}/${config.id}")
        }

    private fun loadImplementation() {
        if (!preferences.getBoolean(MOVED_TO_FILES_KEY, false))
        moveToFiles()

        val loaded=mutableListOf<Config>()
        val broken=mutableListOf<BrokenConfig>()

        val folders=(files.list(VScanFiles.CONFIGS) ?: listOf())
        .filter { it.isDirectory }
        .map { it.name }
        .sorted()

        for (id in folders) {
            val path=configPath(id)
            try {
                val config=files.readToml(path, Config.serializer()) ?: continue

                if (isBuiltInConfigId(id))
                broken.add(BrokenConfig(id, "$id is the id of a built-in config"))
                else
                loaded.add(config.copy(id=id))
                }
            catch (e: TomlException) {
                broken.add(BrokenConfig(id, e.message ?: "Invalid file"))
                }
            }

        configurations=(builtInConfigs()+loaded).toMutableList()

        // Each problem once
        val known=brokenConfigs.toSet()
        for (config in broken) {
            if (config !in known)
            files.reportBrokenFile(BrokenFile(configPath(config.id), config.error, "The config is left out until the file is fixed. VScan doesn't overwrite the file."))
            }
        brokenConfigs=broken
        }

    // The configs stored in the preferences become files, the preferences staying as a backup
    private fun moveToFiles() {
        val serializedList=preferences.getString(CONFIGS_KEY, null)
        val legacyList=preferences.getString(LEGACY_CONFIGS_KEY, null)

        val stored: List<Config> = when {
            serializedList!=null -> json.decodeFromString(serializedList)
            legacyList!=null -> ConfigV1Migration.migrate(legacyList)
            else -> listOf()
            }

        for (config in stored) {
            if (!isBuiltInConfigId(config.id))
            write(config)
            }

        preferences.edit()
        .putBoolean(MOVED_TO_FILES_KEY, true)
        .commit()
        }

    private fun write(config: Config) {
        files.writeToml(configPath(config.id), Config.serializer(), config, FILE_HEADER, setOf("id"))
        }

    private fun configPath(id: String): String = "${VScanFiles.CONFIGS}/$id/$CONFIG_FILE"

    private fun getFreeId(name: String): String {
        val taken=configurations.map { it.id }.toSet()+brokenConfigs.map { it.id }+builtInConfigIds
        return Ids.generate(name, "config", taken)
        }

    private fun builtInConfigs(): List<Config> = listOf(baseConfig(), fileDescriptionConfig(), findConfig())

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
                maxCompletionTokens=5000,
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

        private const val CONFIG_FILE="config.toml"
        private const val FILE_HEADER="A VScan config, its id is the name of its folder\nSaving the config in VScan rewrites this file, leaving out comments"

        // The configs before the files
        private const val CONFIGS_KEY="configs"
        // The flat configs of versions up to 0.2.x
        private const val LEGACY_CONFIGS_KEY="configurations"
        private const val MOVED_TO_FILES_KEY="movedToFiles"

        private val builtInConfigIds=setOf(BASE_CONFIG_ID, FILE_DESCRIPTION_CONFIG_ID, FIND_CONFIG_ID)

        // For the configs stored in the preferences
        private val json=Json {
            ignoreUnknownKeys=true
            }

        fun isBuiltInConfigId(id: String): Boolean = id in builtInConfigIds

        private var instance: ConfigManager?=null

        fun getInstance(context: Context): ConfigManager {
            if (instance==null) {
                StringIdMigration.ensure(context)
                val preferences=context.getSharedPreferences("VScanConfigurations", Context.MODE_PRIVATE)
                instance=ConfigManager(preferences, VScanFiles.getInstance(context))
                instance?.load()
                }

            return instance!!
            }
        }
    }
