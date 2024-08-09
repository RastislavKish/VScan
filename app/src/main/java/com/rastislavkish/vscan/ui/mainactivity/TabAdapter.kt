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

package com.rastislavkish.vscan.ui.mainactivity

import android.content.Context

import kotlinx.coroutines.sync.Mutex

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.SystemMessage
import com.rastislavkish.vscan.core.Settings

class TabAdapter(context: Context) {

    private val settings=Settings.getInstance(context)
    private val configManager=ConfigManager.getInstance(context)

    var activeConfig: Config=Config()
    get set

    var conversation: Conversation=Conversation(
        settings.apiKey,
        activeConfig.model.identifier,
        if (!activeConfig.systemPrompt.isEmpty()) SystemMessage(activeConfig.systemPrompt) else null,
        )
    get set

    val mutex: Mutex=Mutex()
    get

    fun resetActiveConfig() {
        activeConfig=configManager.getConfig(activeConfig.id) ?: Config()
        }
    fun resetConversation() {
        conversation=Conversation(
            settings.apiKey,
            activeConfig.model.identifier,
            if (!activeConfig.systemPrompt.isEmpty()) SystemMessage(activeConfig.systemPrompt) else null,
            )
        }

    companion object {

        private var instance: TabAdapter?=null

        fun getInstance(context: Context): TabAdapter {
            if (instance==null) {
                instance=TabAdapter(context)
                }

            return instance!!
            }
        }
    }
