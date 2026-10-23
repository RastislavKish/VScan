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

import android.app.ActivityManager
import android.content.Context
import android.content.Intent

import com.rastislavkish.vscan.core.sound.VibrationSystem

// Exiting VScan ends everything: the processes, the service, the screens and the app process itself, so no unsaved state survives
// The user then knows it's either running, with its notification shown, or gone with all unsaved changes
object AppExit {

    fun exit(context: Context) {
        val appContext=context.applicationContext

        ProcessManager.getInstance(appContext).killAll()
        // The system vibrator would keep a looping pattern going
        VibrationSystem.getInstance(appContext).stopAll()

        appContext.stopService(Intent(appContext, VScanService::class.java))
        for (task in appContext.getSystemService(ActivityManager::class.java).appTasks)
        task.finishAndRemoveTask()

        android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
