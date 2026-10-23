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

import java.io.Closeable
import java.util.concurrent.Executors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.UsedCamera
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.IdleTimeoutLease
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.imagesource.RefCounter

class CaptureException(message: String): Exception(message)

// The app-wide owner of the smartphone camera, as an image source
// The camera is open while the app is in the foreground, independently of any particular screen, and in the background while something uses it
// In the background, Android allows that only with VScan's foreground service running
// The video stream and the torch are shared by reference counting, and the stream is bound only while somebody holds it open
class PhoneCamera private constructor(context: Context): ImageSource {

    private val context=context.applicationContext
    // Started while the camera should be open, changed on the main thread only
    private val lifecycleOwner=object: LifecycleOwner {
        val registry=LifecycleRegistry(this)
        override val lifecycle: Lifecycle
        get() = registry
        }
    private var appInForeground=false
    private var captures=0
    private val settings=Settings.getInstance(context)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)

    // Guards the provider and the binding, and serializes captures
    private val mutex=Mutex()

    private var cameraProvider: ProcessCameraProvider?=null
    private var camera: Camera?=null
    private var boundCamera: UsedCamera?=null
    private var boundWithStream=false

    private val imageCapture: ImageCapture
    private val imageAnalysis: ImageAnalysis
    private val analysisExecutor=Executors.newSingleThreadExecutor()

    // Requests waiting for the next stream frame, guarded by its own lock since frames arrive on the analysis thread
    private val frameRequests=mutableListOf<CompletableDeferred<ImageData>>()

    private val streamUsers=RefCounter { refresh() }
    // Keeps the stream open between getVideoFrame calls made without an open stream
    private val implicitStream=IdleTimeoutLease(streamUsers, IDLE_STREAM_TIMEOUT_MILLIS, SystemClock::elapsedRealtime)
    private val torchUsers=RefCounter { refresh() }

    private val orientationEventListener: OrientationEventListener
    private var lastRotationValue=-1

    init {
        // Full sensor resolution, downscaling is up to the consumers of the images
        val captureResolutionSelector=ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
        .build()

        imageCapture=ImageCapture.Builder()
        .setResolutionSelector(captureResolutionSelector)
        .setFlashMode(ImageCapture.FLASH_MODE_ON)
        .build()

        // Video quality, which devices support alongside full resolution captures
        val analysisResolutionSelector=ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(ResolutionStrategy(STREAM_RESOLUTION, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
        .build()

        imageAnalysis=ImageAnalysis.Builder()
        .setResolutionSelector(analysisResolutionSelector)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()

        imageAnalysis.setAnalyzer(analysisExecutor, this::onFrame)

        orientationEventListener=object : OrientationEventListener(this.context) {

            override fun onOrientationChanged(orientation: Int) {
                onOrientationChange(orientation)
                }
            }

        lifecycleOwner.registry.currentState=Lifecycle.State.CREATED

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onStart(owner: LifecycleOwner) {
                appInForeground=true
                updateLifecycle()
                }
            override fun onStop(owner: LifecycleOwner) {
                appInForeground=false
                updateLifecycle()
                }
            })

        // Track the device orientation only while the camera is open
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onStart(owner: LifecycleOwner) {
                orientationEventListener.enable()
                }
            override fun onStop(owner: LifecycleOwner) {
                orientationEventListener.disable()
                }
            })
        }

    // Binds the camera ahead of time, so the first capture doesn't wait for the camera to open
    suspend fun prepare() {
        mutex.withLock {
            ensureBound()
            }
        }

    override suspend fun capture(flashlight: Boolean): ImageData {
        return mutex.withLock {
            withContext(Dispatchers.Main) {
                captures++
                updateLifecycle()
                }

            try {
                ensureBound()

                // With the torch on, a flash would only disturb the exposure
                val flash=flashlight && !torchUsers.isActive
                imageCapture.setFlashMode(if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)

                takePicture()
                }
            finally {
                withContext(Dispatchers.Main+NonCancellable) {
                    captures--
                    updateLifecycle()
                    }
                }
            }
        }

    override suspend fun getVideoFrame(): ImageData {
        implicitStream.use()
        return awaitFrame()
        }

    override fun openStream(): Closeable = streamUsers.acquire()
    override fun requestTorch(): Closeable = torchUsers.acquire()

    // Brings the lifecycle, the binding and the torch in line with the current users and settings
    private fun refresh() {
        scope.launch {
            updateLifecycle()

            try {
                mutex.withLock {
                    ensureBound()
                    applyTorch()
                    }
                }
            catch (e: CaptureException) {
                Log.e(TAG, e.message ?: "Error refreshing the camera")
                }
            }
        }

    // The camera direction is a global setting, so changing it takes effect on the next capture
    private suspend fun ensureBound() = withContext(Dispatchers.Main) {
        val direction=if (settings.useFrontCamera) UsedCamera.FRONT_CAMERA else UsedCamera.BACK_CAMERA
        val withStream=streamUsers.isActive

        if (direction==boundCamera && withStream==boundWithStream)
        return@withContext

        Log.d(TAG, "Binding the ${direction.name.lowercase()}${if (withStream) " with stream" else ""}, lifecycle ${lifecycleOwner.lifecycle.currentState}")

        val provider=getCameraProvider()
        provider.unbindAll()
        camera=null
        boundCamera=null

        val cameraSelector=when (direction) {
            UsedCamera.BACK_CAMERA -> CameraSelector.DEFAULT_BACK_CAMERA
            UsedCamera.FRONT_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
            }

        camera=try {
            if (withStream)
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture, imageAnalysis)
            else
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
            }
        catch (e: Exception) {
            Log.d(TAG, "Binding failed: ${e.message}")
            throw CaptureException("Error opening the camera")
            }

        boundCamera=direction
        boundWithStream=withStream

        // Rebinding turns the torch off
        applyTorch()
        }

    // Opens the camera while the app is in the foreground or somebody uses it, closes it otherwise
    private fun updateLifecycle() {
        val inUse=appInForeground || captures>0 || streamUsers.isActive || torchUsers.isActive
        val state=if (inUse) Lifecycle.State.STARTED else Lifecycle.State.CREATED

        if (lifecycleOwner.registry.currentState!=state) {
            Log.d(TAG, "Camera lifecycle ${state.name.lowercase()}")
            lifecycleOwner.registry.currentState=state
            }
        }

    private fun applyTorch() {
        val camera=camera ?: return

        if (camera.cameraInfo.hasFlashUnit())
        camera.cameraControl.enableTorch(torchUsers.isActive)
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

    // Limited in time, as a capture that never completes would hold the camera for good
    private suspend fun takePicture(): ImageData = withContext(Dispatchers.Main) {
        Log.d(TAG, "Capturing")

        try {
            withTimeout(CAPTURE_TIMEOUT_MILLIS) {
                awaitPicture()
                }
            }
        catch (e: TimeoutCancellationException) {
            Log.d(TAG, "Capture timed out")
            throw CaptureException("The camera didn't deliver the image")
            }
        }

    private suspend fun awaitPicture(): ImageData {
        return suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        Log.d(TAG, "Captured")

                        try {
                            continuation.resume(extractImage(imageProxy))
                            }
                        catch (e: CaptureException) {
                            continuation.resumeWithException(e)
                            }
                        }

                    override fun onError(error: ImageCaptureException) {
                        Log.d(TAG, "Capture failed: ${error.message}")
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

    private suspend fun awaitFrame(): ImageData {
        val request=CompletableDeferred<ImageData>()
        synchronized(frameRequests) {
            frameRequests.add(request)
            }

        try {
            return withTimeout(FRAME_TIMEOUT_MILLIS) {
                request.await()
                }
            }
        catch (e: TimeoutCancellationException) {
            throw CaptureException("No video frame received from the camera")
            }
        finally {
            synchronized(frameRequests) {
                frameRequests.remove(request)
                }
            }
        }

    // Called on the analysis thread for every stream frame, frames are converted only when somebody waits for one
    private fun onFrame(imageProxy: ImageProxy) {
        try {
            // Checked per frame, as frames keep coming exactly as long as the stream is open
            implicitStream.releaseIfIdle()

            val requests=synchronized(frameRequests) {
                val requests=frameRequests.toList()
                frameRequests.clear()
                requests
                }

            if (requests.isEmpty())
            return

            try {
                val image=ImageData.fromBitmap(frameToBitmap(imageProxy))
                requests.forEach { it.complete(image) }
                }
            catch (e: Exception) {
                requests.forEach { it.completeExceptionally(CaptureException("Error converting the video frame")) }
                }
            }
        finally {
            imageProxy.close()
            }
        }

    private fun frameToBitmap(imageProxy: ImageProxy): Bitmap {
        val bitmap=imageProxy.toBitmap()
        val rotation=imageProxy.imageInfo.rotationDegrees

        if (rotation==0)
        return bitmap

        val matrix=Matrix()
        matrix.postRotate(rotation.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

    private fun onOrientationChange(orientation: Int) {
        if (orientation==OrientationEventListener.ORIENTATION_UNKNOWN)
        return

        val rotation=UseCase.snapToSurfaceRotation(orientation)

        if (rotation!=lastRotationValue) {
            imageCapture.setTargetRotation(rotation)
            imageAnalysis.setTargetRotation(rotation)

            lastRotationValue=rotation
            }
        }

    companion object {

        private const val TAG="PhoneCamera"

        // In the sensor's landscape orientation
        private val STREAM_RESOLUTION=Size(1440, 1080)
        private const val FRAME_TIMEOUT_MILLIS=5000L
        private const val CAPTURE_TIMEOUT_MILLIS=15000L
        private const val IDLE_STREAM_TIMEOUT_MILLIS=10000L

        private var instance: PhoneCamera?=null

        @Synchronized
        fun getInstance(context: Context): PhoneCamera {
            if (instance==null) {
                instance=PhoneCamera(context)
                }

            return instance!!
            }
        }
    }
