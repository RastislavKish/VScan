/*
* Copyright (C) 2023 Rastislav Kish
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


class Settings(
    val preferences: SharedPreferences
    ) {

    var useFlashlight=false
    var useFrontCamera=false
    var useSounds=true
    var describeSavedImages=false
    var markdownRendering=false

    var speakResults=false
    var speechUsesAccessibilityVolume=true
    var stopSpeechOnProximityWave=true

    var defaultConfigId: String=ConfigManager.BASE_CONFIG_ID
    var shareConfigId: String=ConfigManager.BASE_CONFIG_ID
    var fileDescriptionConfigId: String=ConfigManager.FILE_DESCRIPTION_CONFIG_ID

    var shakeAction: Action? = null
    var volumeUpPressAction: Action? = null
    var volumeDownPressAction: Action? = null

    fun load() {
        useFlashlight=preferences.getBoolean("useFlashlight", false)
        useFrontCamera=preferences.getBoolean("useFrontCamera", false)
        useSounds=preferences.getBoolean("useSounds", true)
        describeSavedImages=preferences.getBoolean("describeSavedImages", false)
        markdownRendering=preferences.getBoolean("markdownRendering", false)

        speakResults=preferences.getBoolean("speakResults", false)
        speechUsesAccessibilityVolume=preferences.getBoolean("speechUsesAccessibilityVolume", true)
        stopSpeechOnProximityWave=preferences.getBoolean("stopSpeechOnProximityWave", true)

        defaultConfigId=preferences.getString("defaultConfig", null) ?: ConfigManager.BASE_CONFIG_ID
        shareConfigId=preferences.getString("shareConfig", null) ?: ConfigManager.BASE_CONFIG_ID
        fileDescriptionConfigId=preferences.getString("fileDescriptionConfig", null) ?: ConfigManager.FILE_DESCRIPTION_CONFIG_ID

        shakeAction=getAction("shakeAction")
        volumeUpPressAction=getAction("volumeUpPressAction")
        volumeDownPressAction=getAction("volumeDownPressAction")
        }
    fun save() {
        preferences.edit()
        .putBoolean("useFlashlight", useFlashlight)
        .putBoolean("useFrontCamera", useFrontCamera)
        .putBoolean("useSounds", useSounds)
        .putBoolean("describeSavedImages", describeSavedImages)
        .putBoolean("markdownRendering", markdownRendering)
        .putBoolean("speakResults", speakResults)
        .putBoolean("speechUsesAccessibilityVolume", speechUsesAccessibilityVolume)
        .putBoolean("stopSpeechOnProximityWave", stopSpeechOnProximityWave)
        .putString("defaultConfig", defaultConfigId)
        .putString("shareConfig", shareConfigId)
        .putString("fileDescriptionConfig", fileDescriptionConfigId)
        .putString("shakeAction", Json.encodeToString(shakeAction))
        .putString("volumeUpPressAction", Json.encodeToString(volumeUpPressAction))
        .putString("volumeDownPressAction", Json.encodeToString(volumeDownPressAction))
        .commit()
        }

    fun getDefaultConfig(configManager: ConfigManager): Config {
        val result=configManager.getConfig(defaultConfigId)

        if (result!=null) {
            return result!!
            }

        return configManager.getBaseConfig()
        }
    fun getShareConfig(configManager: ConfigManager): Config {
        val result=configManager.getConfig(shareConfigId)

        if (result!=null) {
            return result!!
            }

        return configManager.getBaseConfig()
        }
    fun getFileDescriptionConfig(configManager: ConfigManager): Config {
        val result=configManager.getConfig(fileDescriptionConfigId)

        if (result!=null) {
            return result!!
            }

        return configManager.getFileDescriptionConfig()
        }

    private fun getAction(key: String): Action? {
        val serialized=preferences.getString(key, null) ?: return null
        val action: Action = Json.decodeFromString(serialized) ?: return null

        return action
        }

    companion object {

        private var instance: Settings?=null

        fun getInstance(context: Context): Settings {
            if (instance==null) {
                StringIdMigration.ensure(context)
                val preferences=context.getSharedPreferences("VScanSettings", Context.MODE_PRIVATE)
                instance=Settings(preferences)
                instance?.load()
                }

            return instance!!
            }
        }
    }
