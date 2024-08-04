/*
* Copyright (C) 2023 Rastislav Kish
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

package com.rastislavkish.vscan.ui.scanactivity

import androidx.appcompat.app.AppCompatActivity
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Size
import java.util.Base64

import android.content.ClipboardManager
import android.content.ClipData
import android.content.ClipDescription

import android.widget.Button
import android.widget.ToggleButton
import android.widget.EditText
import android.widget.Toast
import android.view.View
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.OrientationEventListener

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.Executors

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Resources
import com.rastislavkish.vscan.core.openai.*

class ScanActivity : AppCompatActivity(), CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private lateinit var job: Job

    private lateinit var config: ScanConfig
    private lateinit var resources: Resources
    private lateinit var orientationEventListener: OrientationEventListener
    private var highRes=false

    private lateinit var conversation: Conversation
    private var conversationMutex=Mutex()

    private var camera: Camera?=null
    private var cameraProvider: ProcessCameraProvider?=null
    private val imageCapture: ImageCapture
    private val highResImageCapture: ImageCapture

    private lateinit var scanButton: Button
    private lateinit var sendButton: Button
    private lateinit var messageInput: EditText
    private lateinit var highResToggle: ToggleButton

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
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        job=Job()

        config=try {
            Json.decodeFromString<ScanConfig>(intent?.extras?.getString("config") ?: "")
            }
        catch (e: Exception) {
            finish()
            return
            }

        resources=Resources.getInstance(this)

        orientationEventListener=object : OrientationEventListener(this) {

            override fun onOrientationChanged(orientation: Int) {
                onOrientationChange(orientation)
                }
            }

        scanButton=findViewById(R.id.scanButton)
        sendButton=findViewById(R.id.sendButton)
        messageInput=findViewById(R.id.messageInput)
        messageInput.setOnEditorActionListener(this::onEditorAction)
        highResToggle=findViewById(R.id.highResToggle)
        highRes=highResToggle.isChecked()

        val cameraProviderFuture=ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider=cameraProviderFuture.get()

            if (!highRes)
            camera=cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
            else
            camera=cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, highResImageCapture)
            }, ContextCompat.getMainExecutor(this))

        val systemPrompt=if (config.systemPrompt!="") SystemMessage(config.systemPrompt) else null
        conversation=Conversation(config.apiKey, "gpt-4o", systemPrompt)
        }
    override fun onResume() {
        orientationEventListener.enable()
        super.onResume()
        }
    override fun onPause() {
        orientationEventListener.disable()
        super.onPause()
        }
    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
        }

    fun scanButtonClick(v: View) {
        if (!highRes)
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    onPicture(imageProxy)
                    }

                override fun onError(error: ImageCaptureException) {
                    toast("Error capturing the image")
                    }
                },
            )
        else
        highResImageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    onPicture(imageProxy)
                    }

                override fun onError(error: ImageCaptureException) {
                    toast("Error capturing the image")
                    }
                },
            )

        }
    fun sendButtonClick(v: View) {
        launch {
            conversationMutex.withLock {
                val message=messageInput.text.toString()
                messageInput.text.clear()

                if (message=="") return@launch

                conversation.addMessage(TextMessage(message))
                val response=withContext(Dispatchers.IO) {
                    conversation.generateResponse()
                    }

                toast(response)
                }
            }
        }
    fun copyButtonClick(v: View) {
        launch {
            conversationMutex.withLock {
                if (conversation.messages.size>0) {
                    val text=conversation.messages.last().text

                    val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

                    val clip=ClipData.newPlainText("VScan", text)

                    clipboard.setPrimaryClip(clip)

                    toast("Copied")
                    }
                else
                toast("Nothing to copy")

                }
            }
        }
    fun highResToggleClick(v: View) {
        highRes=highResToggle.isChecked()

        cameraProvider?.unbindAll()

        if (!highRes)
        camera=cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
        else
        camera=cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, highResImageCapture)
        }

    fun onEditorAction(v: View, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId==EditorInfo.IME_ACTION_DONE) {
            sendButton.performClick()
            return true
            }

        return false
        }
    var lastRotationValue=-1
    fun onOrientationChange(orientation: Int) {
        if (orientation==OrientationEventListener.ORIENTATION_UNKNOWN) {
            return
            }

        val rotation=UseCase.snapToSurfaceRotation(orientation)

        if (rotation!=lastRotationValue) {
            imageCapture.setTargetRotation(rotation)
            highResImageCapture.setTargetRotation(rotation)

            lastRotationValue=rotation
            }
        }
    fun onPicture(imageProxy: ImageProxy) {
        val mediaImage=imageProxy.image
        if (mediaImage!=null) {
            if (mediaImage.format==ImageFormat.JPEG) {
                val buffer=mediaImage.planes[0].buffer //ByteBuffer
                val bytes=ByteArray(buffer.remaining())
                buffer.get(bytes)
                val encodedImage=Base64.getEncoder().encodeToString(bytes)

                resources.shutterSound.play()

                launch {
                    conversationMutex.withLock {
                        conversation.reset()
                        val image=LocalImage(encodedImage)
                        conversation.addMessage(ImageMessage(config.userPrompt, image))
                        val response=conversation.generateResponse()
                        toast(response)
                        }
                    }
                }
            else {
                toast("Error: The camera returned an unsupported image type")
                }
            }
        imageProxy.close()
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
