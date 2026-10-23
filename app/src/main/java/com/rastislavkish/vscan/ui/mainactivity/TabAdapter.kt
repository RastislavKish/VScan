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

import com.rastislavkish.vscan.core.ProvidersManager
import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.process.LastImage
import com.rastislavkish.vscan.core.process.Process
import com.rastislavkish.vscan.core.process.ProcessException
import com.rastislavkish.vscan.core.process.ProcessManager
import com.rastislavkish.vscan.core.Settings

// The state the tabs share, which is the foreground process'
// Everything in this class is supposed to be used only while holding its mutex property
class TabAdapter(context: Context) {

    private val settings=Settings.getInstance(context)
    private val providersManager=ProvidersManager.getInstance(context)
    private val configManager=ConfigManager.getInstance(context)
    private val processManager=ProcessManager.getInstance(context)

    // The process the tabs work with, the commands fg and bg may replace it
    val foreground: Process
    get() = processManager.foreground ?: loadDefaultConfig()

    // The foreground process' copy of its config, saved only by the Update button
    var activeConfig: Config
    get() = foreground.config
    set(value) {
        processManager.updateForeground(value)
        }

    // The conversation of the last iteration, which the user can continue
    var conversation: Conversation
    get() {
        val process=foreground
        return process.lastConversation ?: newConversation(process.config).also { process.lastConversation=it }
        }
    set(value) {
        foreground.lastConversation=value
        }

    val lastImage: LastImage
    get() = processManager.lastImage

    // A config picked to be loaded, which waits for its arguments
    var configAwaitingArguments: Config?=null

    val mutex: Mutex=Mutex()
    get

    // Puts a new process of the config in the foreground, discarding the current one with its unsaved changes
    // Throws ProcessException when the config can't be started with the arguments, e.g. a required one is missing
    fun loadConfig(config: Config, given: Map<String, String> = mapOf()) {
        processManager.setForeground(config, given)
        }
    // Restarts the foreground process with its saved config
    fun resetActiveConfig() {
        val process=foreground
        val config=configManager.getConfig(process.config.id) ?: Config()

        try {
            loadConfig(config, process.given)
            }
        catch (e: ProcessException) {
            loadConfig(Config())
            }
        }
    fun resetConversation() {
        foreground.lastConversation=null
        }

    private fun loadDefaultConfig(): Process {
        try {
            return processManager.setForeground(settings.getDefaultConfig(configManager))
            }
        catch (e: ProcessException) {
            // E.g. a default config with required parameters
            return processManager.setForeground(Config())
            }
        }

    private fun newConversation(config: Config): Conversation {
        return Conversation(
            providersManager,
            config.llm.model,
            config.llm.maxCompletionTokens,
            config.llm.reasoningEffort,
            config.systemPromptOrNull,
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
