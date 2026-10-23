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

import com.rastislavkish.vscan.core.files.VScanFiles
import com.rastislavkish.vscan.core.toml.TomlException


// The global settings, in settings.toml
// The preferences hold the settings of the versions before the files, moved to the file once
class Settings(
    val preferences: SharedPreferences,
    private val files: VScanFiles,
    ) {

    @Serializable
    private data class SettingsFile(
        val useFlashlight: Boolean=false,
        val useFrontCamera: Boolean=false,
        val useSounds: Boolean=true,
        val describeSavedImages: Boolean=false,
        val markdownRendering: Boolean=false,
        val reportEveryScan: Boolean=true,
        val speechUsesAccessibilityVolume: Boolean=true,
        val stopSpeechOnProximityWave: Boolean=true,
        val defaultConfig: String=ConfigManager.BASE_CONFIG_ID,
        val shareConfig: String=ConfigManager.BASE_CONFIG_ID,
        val fileDescriptionConfig: String=ConfigManager.FILE_DESCRIPTION_CONFIG_ID,
        val shakeAction: Action?=null,
        val volumeUpPressAction: Action?=null,
        val volumeDownPressAction: Action?=null,
        )

    var useFlashlight=false
    var useFrontCamera=false
    var useSounds=true
    var describeSavedImages=false
    var markdownRendering=false
    // Whether the operations of a scan see its result as a change even when it's the same as the last one
    var reportEveryScan=true

    var speechUsesAccessibilityVolume=true
    var stopSpeechOnProximityWave=true

    var defaultConfigId: String=ConfigManager.BASE_CONFIG_ID
    var shareConfigId: String=ConfigManager.BASE_CONFIG_ID
    var fileDescriptionConfigId: String=ConfigManager.FILE_DESCRIPTION_CONFIG_ID

    var shakeAction: Action? = null
    var volumeUpPressAction: Action? = null
    var volumeDownPressAction: Action? = null

    // The error of a file that can't be read, which the app then doesn't overwrite
    private var fileError: String?=null

    fun load() {
        if (!preferences.getBoolean(MOVED_TO_FILES_KEY, false))
        moveToFiles()

        fileError=null
        val file=try {
            files.readToml(VScanFiles.SETTINGS, SettingsFile.serializer()) ?: SettingsFile()
            }
        catch (e: TomlException) {
            fileError="${VScanFiles.SETTINGS}: ${e.message}"
            files.report(fileError!!)
            SettingsFile()
            }
        apply(file)
        }
    fun save() {
        val error=fileError
        if (error!=null) {
            files.report("The changes of the settings can't be saved until the file is fixed. $error")
            return
            }

        files.writeToml(VScanFiles.SETTINGS, SettingsFile.serializer(), toFile(), FILE_HEADER)
        }

    private fun apply(file: SettingsFile) {
        useFlashlight=file.useFlashlight
        useFrontCamera=file.useFrontCamera
        useSounds=file.useSounds
        describeSavedImages=file.describeSavedImages
        markdownRendering=file.markdownRendering
        reportEveryScan=file.reportEveryScan
        speechUsesAccessibilityVolume=file.speechUsesAccessibilityVolume
        stopSpeechOnProximityWave=file.stopSpeechOnProximityWave
        defaultConfigId=file.defaultConfig
        shareConfigId=file.shareConfig
        fileDescriptionConfigId=file.fileDescriptionConfig
        shakeAction=file.shakeAction
        volumeUpPressAction=file.volumeUpPressAction
        volumeDownPressAction=file.volumeDownPressAction
        }
    private fun toFile() = SettingsFile(
        useFlashlight,
        useFrontCamera,
        useSounds,
        describeSavedImages,
        markdownRendering,
        reportEveryScan,
        speechUsesAccessibilityVolume,
        stopSpeechOnProximityWave,
        defaultConfigId,
        shareConfigId,
        fileDescriptionConfigId,
        shakeAction,
        volumeUpPressAction,
        volumeDownPressAction,
        )

    // The settings stored in the preferences become the file, the preferences staying as a backup
    private fun moveToFiles() {
        if (preferences.all.keys.any { it!=MOVED_TO_FILES_KEY }) {
            useFlashlight=preferences.getBoolean("useFlashlight", false)
            useFrontCamera=preferences.getBoolean("useFrontCamera", false)
            useSounds=preferences.getBoolean("useSounds", true)
            describeSavedImages=preferences.getBoolean("describeSavedImages", false)
            markdownRendering=preferences.getBoolean("markdownRendering", false)
            reportEveryScan=preferences.getBoolean("reportEveryScan", true)

            speechUsesAccessibilityVolume=preferences.getBoolean("speechUsesAccessibilityVolume", true)
            stopSpeechOnProximityWave=preferences.getBoolean("stopSpeechOnProximityWave", true)

            defaultConfigId=preferences.getString("defaultConfig", null) ?: ConfigManager.BASE_CONFIG_ID
            shareConfigId=preferences.getString("shareConfig", null) ?: ConfigManager.BASE_CONFIG_ID
            fileDescriptionConfigId=preferences.getString("fileDescriptionConfig", null) ?: ConfigManager.FILE_DESCRIPTION_CONFIG_ID

            shakeAction=getLegacyAction("shakeAction")
            volumeUpPressAction=getLegacyAction("volumeUpPressAction")
            volumeDownPressAction=getLegacyAction("volumeDownPressAction")

            save()
            }

        preferences.edit()
        .putBoolean(MOVED_TO_FILES_KEY, true)
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

    // The actions were stored with their class names as types
    private fun getLegacyAction(key: String): Action? {
        val serialized=preferences.getString(key, null) ?: return null
        val renamed=legacyActionTypes.entries.fold(serialized) { text, (className, type) ->
            text.replace("\"com.rastislavkish.vscan.core.$className\"", "\"$type\"")
            }

        return try {
            Json { ignoreUnknownKeys=true }.decodeFromString<Action?>(renamed)
            }
        catch (e: Exception) {
            null
            }
        }

    companion object {

        private const val MOVED_TO_FILES_KEY="movedToFiles"
        private const val FILE_HEADER="The VScan settings\nSaving the settings in VScan rewrites this file, leaving out comments"

        private val legacyActionTypes=mapOf(
            "ScanWithActiveConfigAction" to "scan_with_active_config",
            "ScanWithConfigAction" to "scan_with_config",
            "ConsultConfigAction" to "consult_config",
            "AskAction" to "ask",
            "SetSystemPromptAction" to "set_system_prompt",
            "SetUserPromptAction" to "set_user_prompt",
            "StopSpeechAction" to "stop_speech",
            "RunCommandAction" to "run_command",
            )

        private var instance: Settings?=null

        fun getInstance(context: Context): Settings {
            if (instance==null) {
                StringIdMigration.ensure(context)
                val preferences=context.getSharedPreferences("VScanSettings", Context.MODE_PRIVATE)
                instance=Settings(preferences, VScanFiles.getInstance(context))
                instance?.load()
                }

            return instance!!
            }
        }
    }
