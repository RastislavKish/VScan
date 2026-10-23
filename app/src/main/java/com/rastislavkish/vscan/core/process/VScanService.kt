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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

import com.rastislavkish.vscan.R
import com.rastislavkish.vscan.core.PermissionRequester
import com.rastislavkish.vscan.ui.mainactivity.MainActivity

// Keeps VScan running, with its processes and unsaved changes, from the start of the app until the user exits it
// Its notification tells the user the app is running, the camera type lets processes use the camera in the background and with the screen locked
class VScanService: Service() {

    private val handler=Handler(Looper.getMainLooper())
    private lateinit var processManager: ProcessManager

    override fun onCreate() {
        super.onCreate()
        processManager=ProcessManager.getInstance(this)

        val channel=NotificationChannel(CHANNEL_ID, "Running", NotificationManager.IMPORTANCE_LOW)
        channel.description="Shown while VScan is running"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        startInForeground()
        processManager.onChange={ handler.post(this::updateNotification) }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action==ACTION_EXIT)
        AppExit.exit(this)

        // Every start through startForegroundService expects it
        startInForeground()

        // Restarted by the system, it would only pretend the lost state was kept
        return START_NOT_STICKY
        }

    // Swiping VScan away from the recent apps exits it, like pressing back twice
    override fun onTaskRemoved(rootIntent: Intent?) {
        AppExit.exit(this)
        }

    override fun onDestroy() {
        processManager.onChange=null
        super.onDestroy()
        }

    override fun onBind(intent: Intent?): IBinder?=null

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.R)
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else
        startForeground(NOTIFICATION_ID, notification())
        }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }

    private fun notification(): Notification {
        val count=processManager.list().size
        val text=when (count) {
            0 -> "No processes running"
            1 -> "1 process running"
            else -> "$count processes running"
            }

        val openIntent=PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
            )
        val exitIntent=PendingIntent.getService(
            this,
            0,
            Intent(this, VScanService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("VScan is running")
        .setContentText(text)
        .setContentIntent(openIntent)
        .addAction(0, "Exit", exitIntent)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()
        }

    companion object {

        private const val TAG="VScanService"
        private const val CHANNEL_ID="running"
        private const val NOTIFICATION_ID=1
        private const val ACTION_EXIT="com.rastislavkish.vscan.EXIT"

        // A camera service may only start with the camera permission granted, and from the foreground
        fun start(context: Context) {
            if (!PermissionRequester(context).cameraPermission)
            return

            try {
                ContextCompat.startForegroundService(context, Intent(context, VScanService::class.java))
                }
            catch (e: Exception) {
                Log.e(TAG, "Couldn't start the service: ${e.message}")
                }
            }
        }
    }
