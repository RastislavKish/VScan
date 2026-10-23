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
import android.content.pm.PackageManager

import androidx.core.content.ContextCompat

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import kotlinx.coroutines.runBlocking

import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import com.rastislavkish.vscan.ui.mainactivity.MainActivity

// Exercises the real camera, so it needs a device with a back camera, and the app in the foreground
@RunWith(AndroidJUnit4::class)
class PhoneCameraInstrumentedTest {

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var camera: PhoneCamera

    @Before
    fun setUp() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val packageName=instrumentation.targetContext.packageName

        // Granting it from the test would need UiAutomation, which suspends accessibility services like TalkBack while connected
        val granted=ContextCompat.checkSelfPermission(instrumentation.targetContext, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        assumeTrue("Grant the camera permission first: adb shell pm grant $packageName android.permission.CAMERA", granted)

        // The camera is bound to the process lifecycle, which needs a started activity
        scenario=ActivityScenario.launch(MainActivity::class.java)
        camera=PhoneCamera.getInstance(instrumentation.targetContext)
        }

    @After
    fun tearDown() {
        if (::scenario.isInitialized)
        scenario.close()
        }

    @Test(timeout=60000)
    fun capturesFullResolutionPhoto() = runBlocking {
        val image=camera.capture(false)

        assertTrue(maxOf(image.width, image.height)>=2000)
        }

    @Test(timeout=60000)
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

    @Test(timeout=60000)
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
