/*
* Copyright (C) 2024 Rastislav Kish
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

package com.rastislavkish.vscan.ui.mainactivity

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.graphics.ImageFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.util.Size
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

import android.content.ClipboardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentValues
import android.content.Intent

import android.net.Uri

import android.provider.MediaStore

import android.widget.Button
import android.widget.ToggleButton
import android.widget.EditText
import android.widget.Toast
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

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.LLM
import com.rastislavkish.vscan.core.STT
import com.rastislavkish.vscan.core.Resources
import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.openai.*

class ScanFragment: Fragment(), CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private lateinit var job: Job

    private lateinit var askSTT: STT
    private lateinit var systemPromptSTT: STT
    private lateinit var userPromptSTT: STT

    private lateinit var adapter: TabAdapter
    private lateinit var resources: Resources
    private lateinit var settings: Settings
    private lateinit var orientationEventListener: OrientationEventListener

    private var camera: Camera?=null
    private var cameraProvider: ProcessCameraProvider?=null
    private val imageCapture: ImageCapture
    private val highResImageCapture: ImageCapture

    private var lastTakenImage: ByteArray?=null;
    private var lastTakenImageTimestamp: LocalDateTime?=null;

    private var configUsedByCamera: Config?=null

    private lateinit var scanButton: Button

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
        ): View? {
        return inflater.inflate(R.layout.fragment_scan, container, false)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        job=Job()
        adapter=TabAdapter.getInstance(context!!)
        resources=Resources.getInstance(context!!)
        settings=Settings.getInstance(context!!)
        orientationEventListener=object : OrientationEventListener(context!!) {

            override fun onOrientationChanged(orientation: Int) {
                onOrientationChange(orientation)
                }
            }

        val scanButton: Button=view.findViewById(R.id.scanButton)
        scanButton.setOnClickListener(this::scanButtonClick)

        askSTT=STT(context!!)
        systemPromptSTT=STT(context!!)
        userPromptSTT=STT(context!!)

        val askButton: Button=view.findViewById(R.id.askButton)
        askButton.setOnClickListener(this::askButtonClick)
        val systemPromptButton: Button=view.findViewById(R.id.systemPromptButton)
        systemPromptButton.setOnClickListener(this::systemPromptButtonClick)
        val userPromptButton: Button=view.findViewById(R.id.userPromptButton)
        userPromptButton.setOnClickListener(this::userPromptButtonClick)
        val saveButton: Button=view.findViewById(R.id.saveButton)
        saveButton.setOnClickListener(this::saveButtonClick)
        val resetConfigButton: Button=view.findViewById(R.id.resetConfigButton)
        resetConfigButton.setOnClickListener(this::resetConfigButtonClick)

        val cameraProviderFuture=ProcessCameraProvider.getInstance(context!!)
        cameraProviderFuture.addListener(Runnable {
            launch { adapter.mutex.withLock {
                cameraProvider=cameraProviderFuture.get()

                bindCamera(adapter)
                }}
            }, ContextCompat.getMainExecutor(context!!))
        }

    override fun onResume() {
        orientationEventListener.enable()
        super.onResume()

        launch { adapter.mutex.withLock {
            if (cameraConfigurationChanged(adapter)) {
                cameraProvider?.unbindAll()

                bindCamera(adapter)
                }
            }}
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
        launch { adapter.mutex.withLock {
            val flashMode=when (shouldUseFlashlight(adapter)) {
                true -> ImageCapture.FLASH_MODE_ON
                false -> ImageCapture.FLASH_MODE_OFF
                }

            imageCapture.setFlashMode(flashMode)
            highResImageCapture.setFlashMode(flashMode)

            if (!adapter.activeConfig.highRes)
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context!!),
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
                ContextCompat.getMainExecutor(context!!),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        onPicture(imageProxy)
                        }

                    override fun onError(error: ImageCaptureException) {
                        toast("Error capturing the image")
                        }
                    },
                )
            }}
        }

    fun askButtonClick(v: View) {
        launch {
            val enquiry=askSTT.recognize()

            if (enquiry!=null) {
                adapter.mutex.withLock {
                    adapter.conversation.addMessage(TextMessage(enquiry))
                    val response=adapter.conversation.generateResponse()
                    toast(response)
                    }
                }
            }
        }
    fun systemPromptButtonClick(v: View) {
        launch {
            val enquiry=systemPromptSTT.recognize()

            if (enquiry!=null && !enquiry.isEmpty()) {
                adapter.mutex.withLock {
                    adapter.activeConfig=adapter.activeConfig.withSystemPrompt(enquiry)
                    toast("${enquiry} set as system prompt")
                    }
                }
            }
        }
    fun userPromptButtonClick(v: View) {
        launch {
            val enquiry=userPromptSTT.recognize()

            if (enquiry!=null && !enquiry.isEmpty()) {
                adapter.mutex.withLock {
                    adapter.activeConfig=adapter.activeConfig.withUserPrompt(enquiry)
                    toast("${enquiry} set as user prompt")
                    }
                }
            }
        }
    fun saveButtonClick(v: View) {
        val image=lastTakenImage ?: return
        val timestamp=lastTakenImageTimestamp ?: return

        launch {
            val connection=Conversation(settings.apiKey, LLM.GPT_4O.identifier, null)

            val encodedImage=Base64.getEncoder().encodeToString(image)
            connection.addMessage(ImageMessage(
                "Generate a few word description of this image, which could serve as its filename in Pictures folder. Answer with the filename only, no comments and omit the extension.",
                LocalImage(encodedImage),
                ))
            val response=connection.generateResponse()

            val fileName="$response-${timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}.jpg"

            try {
                saveToGallery(fileName, image)
                toast("Saved as $fileName")
                }
            catch (e: Exception) {
                toast("Saving failed: ${e.message}")
                }
            }
        }
    fun resetConfigButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            adapter.resetActiveConfig()
            adapter.resetConversation()
            lastTakenImage=null
            lastTakenImageTimestamp=null

            toast("Reset to ${adapter.activeConfig.name}")
            }}
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
                lastTakenImage=bytes
                lastTakenImageTimestamp=LocalDateTime.now()
                val encodedImage=Base64.getEncoder().encodeToString(bytes)

                resources.shutterSound.play()

                launch {
                    adapter.mutex.withLock {
                        adapter.resetConversation()
                        val image=LocalImage(encodedImage)
                        adapter.conversation.addMessage(ImageMessage(adapter.activeConfig.userPrompt, image))
                        val response=adapter.conversation.generateResponse()
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

    fun bindCamera(adapter: TabAdapter) {
        if (!adapter.activeConfig.highRes)
        camera=cameraProvider?.bindToLifecycle(activity!!, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
        else
        camera=cameraProvider?.bindToLifecycle(activity!!, CameraSelector.DEFAULT_BACK_CAMERA, highResImageCapture)

        configUsedByCamera=adapter.activeConfig
        }
    fun cameraConfigurationChanged(adapter: TabAdapter): Boolean {
        if (configUsedByCamera==null)
        return false

        return adapter.activeConfig.highRes!=configUsedByCamera!!.highRes || adapter.activeConfig.camera!=configUsedByCamera!!.camera
        }
    fun shouldUseFlashlight(adapter: TabAdapter): Boolean {
        return when (adapter.activeConfig.flashlightMode) {
            FlashlightMode.DEFAULT -> settings.useFlashlight
            FlashlightMode.ON -> true
            FlashlightMode.OFF -> false
            }
        }
    fun toast(text: String) {
        Toast.makeText(activity!!, text, Toast.LENGTH_LONG).show()
        }
    fun createImageFile(fileName: String): File {
        val storageDir: File = if (Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) else activity!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: throw Exception("Failed to optain the Pictures folder path")
        val imageFile=File("$storageDir/$fileName")
        imageFile.createNewFile()
        return imageFile
        }
    fun saveToGallery(fileName: String, image: ByteArray) {
        val imageFile=createImageFile(fileName)

        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) {
            val intent=Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.setData(Uri.parse("file://${imageFile.absolutePath}"))
            activity!!.sendBroadcast(intent)
            }
        else {
            val contentValues=ContentValues()
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/*")
            val contentUri: Uri=if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else
            MediaStore.Images.Media.INTERNAL_CONTENT_URI

            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val uri: Uri=activity!!.getContentResolver().insert(contentUri, contentValues) ?: throw Exception("Unable to obtain uri for writing")

            var os: OutputStream?=null
            try {
                os=activity!!.getContentResolver().openOutputStream(uri)
                os?.write(image)
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                activity!!.getContentResolver().update(uri, contentValues, null, null)
                }
            catch (e: Exception) {
                activity!!.getContentResolver().delete(uri, null, null)
                throw Exception("Unable to write to the output file")
                }
            finally {
                try {
                    os?.close()
                    }
                catch (e: Exception) {
                    throw Exception("Unable to close the output stream")
                    }
                }

            val mediaScanIntent=Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.setData(contentUri)
            activity!!.sendBroadcast(mediaScanIntent)
            }
        }
    }
