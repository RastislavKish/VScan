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

// The kinds of config list items the editor edits
@Serializable
enum class ItemKind {
    PARAMETER,
    MEMORY_VARIABLE,
    OPERATION,
    LENS_OPERATION,
    }

// The item is in its JSON form, the index is the caller's, -1 for a new item
@Serializable
data class ItemEditorActivityInput(
    val kind: ItemKind,
    val title: String,
    val item: String,
    val index: Int=-1,
    ) {

    fun toIntent(launchingActivity: androidx.fragment.app.FragmentActivity): Intent {
        val intent=Intent(launchingActivity, ItemEditorActivity::class.java)
        .putExtra("input", Json.encodeToString(this))
        return intent
        }

    companion object {

        fun fromIntent(intent: Intent?, caller: String): ItemEditorActivityInput {
            val json=intent?.extras?.getCharSequence("input")?.toString()
            ?: throw Exception("$caller did not receive a json in the received intent on key \"input\".")

            try {
                return Json.decodeFromString<ItemEditorActivityInput>(json)
                }
            catch (e: Exception) {
                throw Exception("Object received by $caller is invalid.")
                }
            }
        }
    }
