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

import kotlinx.serialization.*

// The serial names are the types in settings.toml
@Serializable
sealed class Action {

    abstract fun toString(configManager: ConfigManager): String
    }

@Serializable
@SerialName("scan_with_active_config")
class ScanWithActiveConfigAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Scan with active config"
    }

@Serializable
@SerialName("scan_with_config")
class ScanWithConfigAction(val config: String=ConfigManager.BASE_CONFIG_ID): Action() {

    override fun toString(configManager: ConfigManager): String {
        val configName=configManager.getConfig(config)?.name
        ?: configManager.getBaseConfig().name

        return "Scan with $configName"
        }
    }

@Serializable
@SerialName("consult_config")
class ConsultConfigAction(val config: String=ConfigManager.BASE_CONFIG_ID): Action() {

    override fun toString(configManager: ConfigManager): String {
        val configName=configManager.getConfig(config)?.name
        ?: configManager.getBaseConfig().name

        return "Consult $configName"
        }
    }

@Serializable
@SerialName("ask")
class AskAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Ask"
    }

@Serializable
@SerialName("set_system_prompt")
class SetSystemPromptAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Set system prompt"
    }

@Serializable
@SerialName("set_user_prompt")
class SetUserPromptAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Set user prompt"
    }

@Serializable
@SerialName("stop_speech")
class StopSpeechAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Stop speech"
    }

// Runs a command prompt line, e.g. starting a config in the background
@Serializable
@SerialName("run_command")
class RunCommandAction(val command: String=""): Action() {

    override fun toString(configManager: ConfigManager): String
    = "Run $command"
    }
