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

class ConfigManager(
    val preferences: SharedPreferences
    ): CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private var job=Job()

    private var configurations=mutableListOf(Config())
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

    fun getConfig(id: Int): Config? {
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
        val newConfig=config.withId(getFreeId())
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
                return
                }
            }
        }

    private fun loadImplementation() {
        val serializedList=preferences.getString("configurations", "") ?: ""

        if (serializedList.isEmpty())
        return

        configurations=Json.decodeFromString(serializedList)

        //In case the base config was out of date or missing
        updateConfigImplementation(Config())
        }
    private fun saveImplementation() {
        preferences.edit()
        .putString("configurations", Json.encodeToString(configurations))
        .commit()
        }

    private fun getFreeId(): Int {
        var maxUsedId=-1
        for (config in configurations) {
            if (config.id>maxUsedId)
            maxUsedId=config.id
            }
        return maxUsedId+1
        }

    companion object {

        private var instance: ConfigManager?=null

        fun getInstance(context: Context): ConfigManager {
            if (instance==null) {
                val preferences=context.getSharedPreferences("VScanConfigurations", Context.MODE_PRIVATE)
                instance=ConfigManager(preferences)
                instance?.load()
                }

            return instance!!
            }
        }
    }
