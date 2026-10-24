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
package com.rastislavkish.vscan.core.files

import android.content.Context

import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.ProvidersManager
import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.process.GlobalMemory
import com.rastislavkish.vscan.core.process.ProcessManager

// Brings the changes of the linked folder into the app
object FileSync {

    // Does the file work on the calling thread, and returns the changed paths
    fun pull(context: Context): Set<String> = VScanFiles.getInstance(context).pull()

    // Reloads what the changes touch, on the main thread
    // The foreground process keeps its copy of its config
    fun apply(context: Context, changed: Set<String>) {
        if (changed.any { it==VScanFiles.CONFIGS || it.startsWith("${VScanFiles.CONFIGS}/") })
        ConfigManager.getInstance(context).load()
        if (VScanFiles.PROVIDERS in changed || VScanFiles.SECRETS in changed)
        ProvidersManager.getInstance(context).load()
        if (VScanFiles.SETTINGS in changed)
        Settings.getInstance(context).load()
        if (changed.any { it.startsWith("${GlobalMemory.MEMORY}/") })
        ProcessManager.getInstance(context).memory.load()
        // Sounds are reloaded when they're played next, by their modification times
        }

    // For the reload command
    fun reload(context: Context): String {
        if (VScanFiles.getInstance(context).linked==null)
        return "No linked folder"

        val changed=pull(context)
        apply(context, changed)

        return when (changed.size) {
            0 -> "Nothing changed"
            1 -> "Reloaded 1 file"
            else -> "Reloaded ${changed.size} files"
            }
        }
    }
