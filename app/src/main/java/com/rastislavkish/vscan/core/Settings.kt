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

class Settings(
    val preferences: SharedPreferences
    ) {

    var apiKey=""
    var useFlashlight=true
    var useSounds=true

    var defaultConfigId: Int=-1
    var shareConfigId: Int=-1
    var fileDescriptionConfigId: Int=-3

    fun load() {
        apiKey=preferences.getString("apiKey", "") ?: ""
        useFlashlight=preferences.getBoolean("useFlashlight", true)
        useSounds=preferences.getBoolean("useSounds", true)

        defaultConfigId=preferences.getInt("defaultConfigId", -1)
        shareConfigId=preferences.getInt("shareConfigId", -1)
        fileDescriptionConfigId=preferences.getInt("fileDescriptionConfigId", -3)
        }
    fun save() {
        preferences.edit()
        .putString("apiKey", apiKey)
        .putBoolean("useFlashlight", useFlashlight)
        .putBoolean("useSounds", useSounds)
        .putInt("defaultConfigId", defaultConfigId)
        .putInt("shareConfigId", shareConfigId)
        .putInt("fileDescriptionConfigId", fileDescriptionConfigId)
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

    companion object {

        private var instance: Settings?=null

        fun getInstance(context: Context): Settings {
            if (instance==null) {
                val preferences=context.getSharedPreferences("VScanSettings", Context.MODE_PRIVATE)
                instance=Settings(preferences)
                instance?.load()
                }

            return instance!!
            }
        }
    }
