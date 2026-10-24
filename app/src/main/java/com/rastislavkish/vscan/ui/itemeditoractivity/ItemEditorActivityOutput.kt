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
package com.rastislavkish.vscan.ui.itemeditoractivity

import android.content.Intent

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

// The edited item in its JSON form, null when the user deleted it
@Serializable
data class ItemEditorActivityOutput(
    val kind: ItemKind,
    val index: Int,
    val item: String?,
    ) {

    fun toIntent(): Intent {
        val resultIntent=Intent()
        resultIntent.putExtra("result", Json.encodeToString(this))
        return resultIntent
        }

    companion object {

        fun fromIntent(intent: Intent?, caller: String): ItemEditorActivityOutput {
            val json=intent?.extras?.getCharSequence("result")?.toString()
            ?: throw Exception("$caller did not receive a json in the received intent on key \"result\".")

            try {
                return Json.decodeFromString<ItemEditorActivityOutput>(json)
                }
            catch (e: Exception) {
                throw Exception("Object received by $caller is invalid.")
                }
            }
        }
    }
