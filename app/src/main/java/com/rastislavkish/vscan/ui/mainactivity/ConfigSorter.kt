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
import android.content.SharedPreferences

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import com.rastislavkish.vscan.core.Config

class ConfigSorter(preferences: SharedPreferences) {

    private var selectionData: MutableMap<Int, Long> = mutableMapOf()
    private val preferences=preferences

    fun load() {
        val serializedSelectionData=preferences.getString("selectionData", "") ?: ""
        if (serializedSelectionData.isNotEmpty()) {
            selectionData=Json.decodeFromString(serializedSelectionData)
            }
        }
    fun save() {
        preferences.edit()
        .putString("selectionData", Json.encodeToString(selectionData))
        .commit()
        }

    fun markSelection(id: Int) {
        selectionData[id]=System.currentTimeMillis()
        save()
        }
    fun sortConfigList(input: List<Config>): List<Config> {
        val list=input.toMutableList()
        list.sortByDescending({
            selectionData.get(it.id) ?: 0L
            })

        return list
        }

    companion object {

        private var instance: ConfigSorter?=null

        fun getInstance(context: Context): ConfigSorter {
            if (instance==null) {
                val preferences=context.getSharedPreferences("VScanConfigSorter", Context.MODE_PRIVATE)
                instance=ConfigSorter(preferences)
                instance?.load()
                }

            return instance!!
            }
        }
    }
