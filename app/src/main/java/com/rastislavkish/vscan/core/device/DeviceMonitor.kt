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
package com.rastislavkish.vscan.core.device

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager

import java.io.Closeable

import kotlinx.coroutines.*

import com.rastislavkish.vscan.core.imagesource.NoOpHandle
import com.rastislavkish.vscan.core.imagesource.RefCounter

// The state of the phone, given to processes as the device variable
// The orientation sensor is listened to only while somebody holds a handle
interface DeviceMonitor {

    fun listen(): Closeable
    // Waits a moment for the first readings after starting to listen
    suspend fun awaitReadings()
    // The device variable: pitch, roll, screenLocked, battery and charging
    fun state(): Map<String, Any?>
    }

object NoDeviceMonitor: DeviceMonitor {

    override fun listen(): Closeable = NoOpHandle
    override suspend fun awaitReadings() {}
    override fun state(): Map<String, Any?> = mapOf()
    }

// The direction of the back camera, from the gravity a phone at rest measures in its own coordinates
// pitch: degrees above the horizon, -90 pointing at the ground, 0 at the horizon (held upright), 90 at the sky
// roll: degrees of turning around the camera's axis, 0 upright, 90 with the top turned right, -90 left, 180 upside down
data class Orientation(
    val pitch: Double,
    val roll: Double,
    ) {

    companion object {

        // x points right, y to the top and z out of the screen, the back camera looking along -z
        fun fromGravity(x: Double, y: Double, z: Double): Orientation? {
            val length=Math.sqrt(x*x+y*y+z*z)
            if (length==0.0)
            return null

            val pitch=Math.toDegrees(Math.asin((-z/length).coerceIn(-1.0, 1.0)))
            // 0.0-x rather than -x, so upside down is 180 rather than -180
            val roll=Math.toDegrees(Math.atan2(0.0-x, y))
            return Orientation(pitch, roll)
            }
        }
    }

class AndroidDeviceMonitor(
    context: Context,
    ): DeviceMonitor {

    private val context=context.applicationContext
    private val sensorManager=this.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    // Gravity is the accelerometer with the movements filtered out, where the phone has it
    private val gravitySensor: Sensor?=sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile private var orientation: Orientation?=null
    @Volatile private var firstReading=CompletableDeferred<Unit>()

    private val listener=object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            val values=event.values
            orientation=Orientation.fromGravity(values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
            firstReading.complete(Unit)
            }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    private val listeners=RefCounter { active ->
        if (active) {
            firstReading=CompletableDeferred()
            if (gravitySensor!=null)
            sensorManager?.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_UI)
            else
            firstReading.complete(Unit)
            }
        else {
            sensorManager?.unregisterListener(listener)
            orientation=null
            }
        }

    override fun listen(): Closeable = listeners.acquire()

    override suspend fun awaitReadings() {
        withTimeoutOrNull(FIRST_READING_TIMEOUT) { firstReading.await() }
        }

    override fun state(): Map<String, Any?> {
        val keyguardManager=context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        val powerManager=context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        val batteryManager=context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
        val orientation=if (listeners.isActive) orientation else null

        return mapOf(
            "pitch" to orientation?.pitch,
            "roll" to orientation?.roll,
            // A screen that's off counts as locked
            "screenLocked" to (keyguardManager?.isKeyguardLocked==true || powerManager?.isInteractive==false),
            "battery" to batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.toDouble(),
            "charging" to batteryManager?.isCharging,
            )
        }

    companion object {

        private const val FIRST_READING_TIMEOUT=500L
        }
    }
