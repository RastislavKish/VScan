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

@Serializable
sealed class Action {

    abstract fun toString(configManager: ConfigManager): String
    }

@Serializable
class ScanWithActiveConfigAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Scan with active config"
    }

@Serializable
class ScanWithConfigAction(val config: Int=-1): Action() {

    override fun toString(configManager: ConfigManager): String {
        val configName=configManager.getConfig(config)?.name
        ?: configManager.getBaseConfig().name

        return "Scan with $configName"
        }
    }

@Serializable
class ConsultConfigAction(val config: Int=-1): Action() {

    override fun toString(configManager: ConfigManager): String {
        val configName=configManager.getConfig(config)?.name
        ?: configManager.getBaseConfig().name

        return "Consult $configName"
        }
    }

@Serializable
class AskAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Ask"
    }

@Serializable
class SetSystemPromptAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Set system prompt"
    }

@Serializable
class SetUserPromptAction: Action() {

    override fun toString(configManager: ConfigManager): String
    = "Set user prompt"
    }

