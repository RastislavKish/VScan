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
package com.rastislavkish.vscan.core.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock

import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import kotlinx.coroutines.runBlocking

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

import com.rastislavkish.vscan.ui.mainactivity.MainActivity

// Exercises the real camera, so it needs a device with a back camera, and the app in the foreground
@RunWith(AndroidJUnit4::class)
class PhoneCameraInstrumentedTest {

    // Unlike @Test(timeout), covers @Before too, and reports the stack of a stuck test
    @get:Rule
    val timeout: Timeout=Timeout.seconds(60)

    private lateinit var camera: PhoneCamera

    @Before
    fun setUp() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val packageName=instrumentation.targetContext.packageName

        // Granting it from the test would need UiAutomation, which suspends accessibility services like TalkBack while connected
        val granted=ContextCompat.checkSelfPermission(instrumentation.targetContext, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        assumeTrue("Grant the camera permission first: adb shell pm grant $packageName android.permission.CAMERA", granted)

        // The camera is bound to the process lifecycle, which needs a started activity
        // Started directly rather than by ActivityScenario, whose helper activities would live in the test APK's process
        val intent=Intent(instrumentation.targetContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.targetContext.startActivity(intent)
        waitForForeground()

        // Created on the main thread, as it observes the process lifecycle
        instrumentation.runOnMainSync {
            camera=PhoneCamera.getInstance(instrumentation.targetContext)
            }
        }

    private fun waitForForeground() {
        val deadline=SystemClock.elapsedRealtime()+20000
        val lifecycle=ProcessLifecycleOwner.get().lifecycle

        while (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (SystemClock.elapsedRealtime()>deadline)
            fail("VScan didn't come to the foreground, is the phone unlocked?")

            Thread.sleep(100)
            }
        }

    @Test
    fun capturesFullResolutionPhoto() = runBlocking {
        val image=camera.capture(false)

        assertTrue(maxOf(image.width, image.height)>=2000)
        }

    @Test
    fun deliversVideoFramesWithAndWithoutOpenStream() = runBlocking {
        val single=camera.getVideoFrame()
        assertTrue(single.width>0 && single.height>0)

        camera.openStream().use {
            val first=camera.getVideoFrame()
            val second=camera.getVideoFrame()

            assertTrue(maxOf(first.width, first.height)<=1920)
            assertNotSame(first, second)
            }
        }

    @Test
    fun capturesWhileStreamingAndWithTorch() = runBlocking {
        camera.openStream().use {
            camera.requestTorch().use {
                camera.getVideoFrame()
                val photo=camera.capture(true)

                assertTrue(maxOf(photo.width, photo.height)>=2000)
                }
            }
        }

    }
