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

import android.content.Context
import android.graphics.ImageFormat
import android.util.Size
import android.view.OrientationEventListener

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import com.rastislavkish.vscan.core.UsedCamera
import com.rastislavkish.vscan.core.image.ImageData

data class CaptureSettings(
    val camera: UsedCamera,
    val highRes: Boolean,
    val flashlight: Boolean,
    )

class CaptureException(message: String): Exception(message)

// The app-wide owner of the smartphone camera
// The camera is bound to the process lifecycle, so it is available while the app is in the foreground, independently of any particular screen
class PhoneCamera private constructor(context: Context) {

    private val context=context.applicationContext
    private val lifecycleOwner: LifecycleOwner=ProcessLifecycleOwner.get()

    // Guards the provider and the binding, and serializes captures
    private val mutex=Mutex()

    private var cameraProvider: ProcessCameraProvider?=null
    private var boundCamera: UsedCamera?=null
    private var boundHighRes: Boolean?=null

    private val imageCapture: ImageCapture
    private val highResImageCapture: ImageCapture

    private val orientationEventListener: OrientationEventListener
    private var lastRotationValue=-1

    init {
        val resolutionSelector=ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy(Size(512, 512), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .build()

        val highResResolutionSelector=ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy(Size(1024, 1024), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .build()

        imageCapture=ImageCapture.Builder()
        .setResolutionSelector(resolutionSelector)
        .setFlashMode(ImageCapture.FLASH_MODE_ON)
        .build()

        highResImageCapture=ImageCapture.Builder()
        .setResolutionSelector(highResResolutionSelector)
        .setFlashMode(ImageCapture.FLASH_MODE_ON)
        .build()

        orientationEventListener=object : OrientationEventListener(this.context) {

            override fun onOrientationChanged(orientation: Int) {
                onOrientationChange(orientation)
                }
            }

        // Track the device orientation only while the app is in the foreground, like the camera itself
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onStart(owner: LifecycleOwner) {
                orientationEventListener.enable()
                }
            override fun onStop(owner: LifecycleOwner) {
                orientationEventListener.disable()
                }
            })
        }

    // Binds the camera ahead of time, so the first capture with these settings doesn't wait for the camera to open
    suspend fun prepare(camera: UsedCamera, highRes: Boolean) {
        mutex.withLock {
            ensureBound(camera, highRes)
            }
        }

    suspend fun capture(settings: CaptureSettings): ImageData {
        return mutex.withLock {
            ensureBound(settings.camera, settings.highRes)

            val useCase=if (settings.highRes) highResImageCapture else imageCapture
            useCase.setFlashMode(if (settings.flashlight) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)

            takePicture(useCase)
            }
        }

    private suspend fun ensureBound(camera: UsedCamera, highRes: Boolean) = withContext(Dispatchers.Main) {
        if (camera==boundCamera && highRes==boundHighRes)
        return@withContext

        val provider=getCameraProvider()
        provider.unbindAll()

        val cameraSelector=when (camera) {
            UsedCamera.BACK_CAMERA -> CameraSelector.DEFAULT_BACK_CAMERA
            UsedCamera.FRONT_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
            }

        try {
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, if (highRes) highResImageCapture else imageCapture)
            }
        catch (e: Exception) {
            boundCamera=null
            boundHighRes=null
            throw CaptureException("Error opening the camera")
            }

        boundCamera=camera
        boundHighRes=highRes
        }

    private suspend fun getCameraProvider(): ProcessCameraProvider {
        cameraProvider?.let { return it }

        val provider=suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
            val future=ProcessCameraProvider.getInstance(context)
            future.addListener(Runnable {
                try {
                    continuation.resume(future.get())
                    }
                catch (e: Exception) {
                    continuation.resumeWithException(CaptureException("Error opening the camera"))
                    }
                }, ContextCompat.getMainExecutor(context))
            }

        cameraProvider=provider
        return provider
        }

    private suspend fun takePicture(useCase: ImageCapture): ImageData = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            useCase.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
                            continuation.resume(extractImage(imageProxy))
                            }
                        catch (e: CaptureException) {
                            continuation.resumeWithException(e)
                            }
                        }

                    override fun onError(error: ImageCaptureException) {
                        continuation.resumeWithException(CaptureException("Error capturing the image"))
                        }
                    },
                )
            }
        }

    private fun extractImage(imageProxy: ImageProxy): ImageData {
        try {
            val mediaImage=imageProxy.image
            if (mediaImage==null || mediaImage.format!=ImageFormat.JPEG)
            throw CaptureException("Error: The camera returned an unsupported image type")

            val buffer=mediaImage.planes[0].buffer
            val bytes=ByteArray(buffer.remaining())
            buffer.get(bytes)

            return ImageData.fromEncoded(bytes)
            }
        finally {
            imageProxy.close()
            }
        }

    private fun onOrientationChange(orientation: Int) {
        if (orientation==OrientationEventListener.ORIENTATION_UNKNOWN)
        return

        val rotation=UseCase.snapToSurfaceRotation(orientation)

        if (rotation!=lastRotationValue) {
            imageCapture.setTargetRotation(rotation)
            highResImageCapture.setTargetRotation(rotation)

            lastRotationValue=rotation
            }
        }

    companion object {

        private var instance: PhoneCamera?=null

        fun getInstance(context: Context): PhoneCamera {
            if (instance==null) {
                instance=PhoneCamera(context)
                }

            return instance!!
            }
        }
    }
