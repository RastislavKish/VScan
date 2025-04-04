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

import java.time.LocalDateTime
import java.util.Base64

import android.content.Context

import kotlinx.coroutines.sync.Mutex

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.SystemMessage
import com.rastislavkish.vscan.core.openai.LocalImage
import com.rastislavkish.vscan.core.openai.ImageMessage
import com.rastislavkish.vscan.core.Settings

// Everything in this class is supposed dto be used only while holding its mutex property
class TabAdapter(context: Context) {

    private val settings=Settings.getInstance(context)
    private val configManager=ConfigManager.getInstance(context)

    var activeConfig: Config=settings.getDefaultConfig(configManager)
    get set

    var conversation: Conversation=Conversation(
        settings.apiBaseUrl,
        settings.apiKey,
        activeConfig.model.identifier,
        if (!activeConfig.systemPrompt.isEmpty()) SystemMessage(activeConfig.systemPrompt) else null,
        )
    get set

    var lastTakenImage: ByteArray?=null
    get set

    var lastTakenImageTimestamp: LocalDateTime?=null
    get set

    val mutex: Mutex=Mutex()
    get

    fun resetActiveConfig() {
        activeConfig=configManager.getConfig(activeConfig.id) ?: Config()
        }
    fun resetConversation() {
        conversation=Conversation(
            settings.apiBaseUrl,
            settings.apiKey,
            activeConfig.model.identifier,
            if (!activeConfig.systemPrompt.isEmpty()) SystemMessage(activeConfig.systemPrompt) else null,
            )
        }

    suspend fun consultConfig(config: Config): String? {
        val image=lastTakenImage ?: return null

        conversation=Conversation(settings.apiBaseUrl, settings.apiKey, config.model.identifier, config.systemPromptOrNull)

        val encodedImage=Base64.getEncoder().encodeToString(image)
        conversation.addMessage(ImageMessage(
            config.userPrompt,
            LocalImage(encodedImage),
            ))
        val response=conversation.generateResponse()

        return response
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
