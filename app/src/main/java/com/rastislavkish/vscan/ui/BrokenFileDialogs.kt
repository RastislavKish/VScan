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

package com.rastislavkish.vscan.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog

import com.rastislavkish.vscan.core.files.BrokenFile

// Tells the user about the broken files, one dialog at a time, each dismissed only by its OK button
// The files wait for an activity in the foreground, and a dialog closed with its activity comes again in the next one
object BrokenFileDialogs {

    private val handler=Handler(Looper.getMainLooper())

    // Used only on the main thread
    private val waiting=ArrayDeque<BrokenFile>()
    private var activity: Activity?=null
    private var shownIn: Activity?=null
    private var installed=false

    @Synchronized
    fun install(application: Application) {
        if (installed)
        return
        installed=true

        application.registerActivityLifecycleCallbacks(object: Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                this@BrokenFileDialogs.activity=activity
                showNext()
                }
            override fun onActivityPaused(activity: Activity) {
                if (this@BrokenFileDialogs.activity==activity)
                this@BrokenFileDialogs.activity=null
                }
            override fun onActivityDestroyed(activity: Activity) {
                if (shownIn==activity)
                shownIn=null
                }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            })
        }

    // Callable from any thread
    fun add(file: BrokenFile) {
        handler.post {
            if (file !in waiting) {
                waiting.addLast(file)
                showNext()
                }
            }
        }

    private fun showNext() {
        val activity=activity ?: return
        if (shownIn!=null)
        return
        val file=waiting.firstOrNull() ?: return

        shownIn=activity
        AlertDialog.Builder(activity)
        .setTitle("Can't read ${file.path}")
        .setMessage("${file.error}\n\n${file.consequence}")
        .setCancelable(false)
        .setPositiveButton("OK") { _, _ ->
            waiting.removeFirst()
            shownIn=null
            showNext()
            }
        .show()
        }
    }
