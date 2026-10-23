/*
* Copyright (C) 2026 Rastislav Kish
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
package com.rastislavkish.vscan.core.process

import android.content.Context
import android.widget.Toast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.PlayOperation
import com.rastislavkish.vscan.core.config.SayOperation
import com.rastislavkish.vscan.core.config.ToastOperation
import com.rastislavkish.vscan.core.config.VibrateOperation
import com.rastislavkish.vscan.core.script.ScriptScope
import com.rastislavkish.vscan.core.speech.SpeechSystem
import com.rastislavkish.vscan.core.template.Template

class AndroidOperationExecutor(
    context: Context,
    ): OperationExecutor {

    private val context=context.applicationContext
    private val speechSystem=SpeechSystem.getInstance(this.context)

    // Operations run every iteration, so their templates are parsed once
    private val templates=object: LinkedHashMap<String, Template>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Template>): Boolean = size>TEMPLATE_CACHE_SIZE
        }

    override suspend fun execute(operation: Operation, scope: ScriptScope, process: Process) {
        when (operation) {
            is ToastOperation -> {
                val text=render(operation.text, scope)
                if (text.isNotBlank())
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    }
                }
            is SayOperation -> {
                val text=render(operation.text, scope)
                if (text.isNotBlank())
                speechSystem.say(text, operation.priority)
                }
            is PlayOperation -> throw ProcessException("Playing sounds isn't supported yet")
            is VibrateOperation -> throw ProcessException("Vibration isn't supported yet")
            }
        }

    private fun render(source: String, scope: ScriptScope): String {
        val template=synchronized(templates) {
            templates.getOrPut(source) { Template.parse(source) }
            }

        return template.renderText(scope)
        }

    companion object {

        private const val TEMPLATE_CACHE_SIZE=64
        }
    }
