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
package com.rastislavkish.vscan.core.speech

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import com.rastislavkish.vscan.core.Settings

// The app-wide speech output, through which all spoken messages go so their priorities can be respected
// Can be called from any thread
class SpeechSystem private constructor(context: Context) {

    private val context=context.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private val settings=Settings.getInstance(context)

    private val engine: SpeechEngine=AndroidSpeechEngine(
        this.context,
        { settings.speechUsesAccessibilityVolume },
        { utteranceId -> scheduler.onUtteranceDone(utteranceId) },
        { Toast.makeText(this.context, "Text to speech is unavailable", Toast.LENGTH_LONG).show() },
        )
    private val scheduler: SpeechScheduler=SpeechScheduler(engine, this::onActiveChange)

    private val sensorManager=this.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val proximitySensor: Sensor?=sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val waveDetector=WaveDetector()
    private var listeningToProximity=false

    private val proximityListener=object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            val sensor=proximitySensor ?: return
            // Most sensors are binary, reporting either 0 or their maximum range
            val near=event.values[0]<minOf(sensor.maximumRange, 5f)

            if (waveDetector.onReading(near, event.timestamp/1_000_000))
            scheduler.stop()
            }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    fun say(text: String, priority: SpeechPriority=SpeechPriority.NORMAL) {
        runOnMain { scheduler.say(text, priority) }
        }

    // The user's request for silence, critical messages are still finished
    fun stop() {
        runOnMain { scheduler.stop() }
        }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper()==Looper.getMainLooper())
        block()
        else
        handler.post(block)
        }

    // The proximity sensor is only listened to while there's something to stop
    private fun onActiveChange(active: Boolean) {
        if (active && settings.stopSpeechOnProximityWave)
        startListeningToProximity()
        else
        stopListeningToProximity()
        }
    private fun startListeningToProximity() {
        if (listeningToProximity || proximitySensor==null)
        return

        waveDetector.reset()
        sensorManager?.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
        listeningToProximity=true
        }
    private fun stopListeningToProximity() {
        if (!listeningToProximity)
        return

        sensorManager?.unregisterListener(proximityListener)
        listeningToProximity=false
        }

    companion object {

        private var instance: SpeechSystem?=null

        @Synchronized
        fun getInstance(context: Context): SpeechSystem {
            if (instance==null) {
                instance=SpeechSystem(context)
                }

            return instance!!
            }
        }
    }
