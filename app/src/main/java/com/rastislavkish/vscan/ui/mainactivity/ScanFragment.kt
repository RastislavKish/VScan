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
import android.view.MotionEvent
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
import com.google.android.material.bottomnavigation.BottomNavigationView

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

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

import com.rastislavkish.rtk.TouchWrapper
import com.rastislavkish.rtk.GestureEventArgs

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.UsedCamera
import com.rastislavkish.vscan.core.LLM
import com.rastislavkish.vscan.core.STT
import com.rastislavkish.vscan.core.Resources
import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.openai.*

import com.rastislavkish.vscan.core.Action
import com.rastislavkish.vscan.core.ScanWithActiveConfigAction
import com.rastislavkish.vscan.core.ScanWithConfigAction
import com.rastislavkish.vscan.core.ConsultConfigAction
import com.rastislavkish.vscan.core.AskAction
import com.rastislavkish.vscan.core.SetSystemPromptAction
import com.rastislavkish.vscan.core.SetUserPromptAction

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
    private lateinit var configManager: ConfigManager
    private lateinit var orientationEventListener: OrientationEventListener
    private lateinit var touchWrapper: TouchWrapper

    private var camera: Camera?=null
    private var cameraProvider: ProcessCameraProvider?=null
    private val imageCapture: ImageCapture
    private val highResImageCapture: ImageCapture

    private var configUsedByCamera: Config?=null

    private lateinit var scanButton: Button
    private lateinit var askButton: Button
    private lateinit var systemPromptButton: Button
    private lateinit var userPromptButton: Button
    private lateinit var multipurposeInput: EditText
    private var multipurposeInputPurpose=MultipurposeInputPurpose.MESSAGE

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
        EventBus.getDefault().register(this)
        return inflater.inflate(R.layout.fragment_scan, container, false)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        job=Job()
        adapter=TabAdapter.getInstance(context!!)
        resources=Resources.getInstance(context!!)
        settings=Settings.getInstance(context!!)
        configManager=ConfigManager.getInstance(context!!)
        orientationEventListener=object : OrientationEventListener(context!!) {

            override fun onOrientationChanged(orientation: Int) {
                onOrientationChange(orientation)
                }
            }
        touchWrapper=TouchWrapper()
        touchWrapper.addSwipeLeftListener(this::onSwipeLeft)
        touchWrapper.addSwipeRightListener(this::onSwipeRight)
        touchWrapper.addSwipeUpListener(this::onSwipeUp)
        touchWrapper.addTapListener(this::onTap)

        scanButton=view.findViewById(R.id.scanButton)
        scanButton.setOnClickListener(this::scanButtonClick)
        scanButton.setOnTouchListener(this::onTouch)

        askButton=view.findViewById(R.id.askButton)
        askButton.setOnClickListener(this::askButtonClick)
        askButton.setOnLongClickListener(this::askButtonLongClick)
        systemPromptButton=view.findViewById(R.id.systemPromptButton)
        systemPromptButton.setOnClickListener(this::systemPromptButtonClick)
        systemPromptButton.setOnLongClickListener(this::systemPromptButtonLongClick)
        userPromptButton=view.findViewById(R.id.userPromptButton)
        userPromptButton.setOnClickListener(this::userPromptButtonClick)
        userPromptButton.setOnLongClickListener(this::userPromptButtonLongClick)

        askSTT=STT(context!!)
        systemPromptSTT=STT(context!!)
        userPromptSTT=STT(context!!)

        val saveButton: Button=view.findViewById(R.id.saveButton)
        saveButton.setOnClickListener(this::saveButtonClick)
        val resetConfigButton: Button=view.findViewById(R.id.resetConfigButton)
        resetConfigButton.setOnClickListener(this::resetConfigButtonClick)

        multipurposeInput=view.findViewById(R.id.multipurposeInput)
        multipurposeInput.setOnEditorActionListener(this::onMultipurposeInputEditorAction)

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

        checkShareBox()
        }
    override fun onPause() {
        orientationEventListener.disable()
        resetMultipurposeInput()
        super.onPause()
        }
    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
        }

    @Subscribe
    fun onDeviceInputEvent(event: DeviceInputEvent) {
        when (event.kind) {
            DeviceInputEventKind.VOLUME_UP_PRESS -> {
                performAction(settings.volumeUpPressAction ?: return)
                }
            DeviceInputEventKind.VOLUME_DOWN_PRESS -> {
                performAction(settings.volumeDownPressAction ?: return)
                }
            DeviceInputEventKind.SHAKE -> {
                performAction(settings.shakeAction ?: return)
                }
            }
        }

    fun scanButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            scanWithConfig(adapter, adapter.activeConfig)
            }}
        }

    fun askButtonClick(v: View) {
        setMultipurposeInputPurpose(MultipurposeInputPurpose.MESSAGE, true)
        }
    fun askButtonLongClick(v: View): Boolean {
        launch {
            val enquiry=askSTT.recognize() ?: return@launch

            adapter.mutex.withLock {
                sendMessage(adapter, enquiry)
                }
            }

        return true
        }
    fun systemPromptButtonClick(v: View) {
        setMultipurposeInputPurpose(MultipurposeInputPurpose.SYSTEM_PROMPT, true)
        }
    fun systemPromptButtonLongClick(v: View): Boolean {
        launch {
            val enquiry=systemPromptSTT.recognize() ?: return@launch

            if (!enquiry.isEmpty())
            adapter.mutex.withLock {
                setSystemPrompt(adapter, enquiry, true)
                }
            }

        return true
        }
    fun userPromptButtonClick(v: View) {
        setMultipurposeInputPurpose(MultipurposeInputPurpose.USER_PROMPT, true)
        }
    fun userPromptButtonLongClick(v: View): Boolean {
        launch {
            val enquiry=userPromptSTT.recognize() ?: return@launch

            if (!enquiry.isEmpty())
            adapter.mutex.withLock {
                setUserPrompt(adapter, enquiry, true)
                }
            }

        return true
        }
    fun saveButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            val image=adapter.lastTakenImage ?: return@launch
            val timestamp=adapter.lastTakenImageTimestamp ?: return@launch

            val fileDescriptionConfig=settings.getFileDescriptionConfig(configManager)
            val connection=Conversation(settings.apiKey, fileDescriptionConfig.model.identifier, fileDescriptionConfig.systemPromptOrNull)

            val encodedImage=Base64.getEncoder().encodeToString(image)
            connection.addMessage(ImageMessage(
                fileDescriptionConfig.userPrompt,
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
            }}
        }
    fun resetConfigButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            adapter.resetActiveConfig()
            adapter.resetConversation()
            adapter.lastTakenImage=null
            adapter.lastTakenImageTimestamp=null

            toast("Reset to ${adapter.activeConfig.name}")
            }}
        }

    fun onMultipurposeInputEditorAction(v: View, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId==EditorInfo.IME_ACTION_DONE) {
            confirmMultipurposeInput()

            return true
            }

        return false
        }

    fun onSwipeLeft(args: GestureEventArgs) {
        val navBar: BottomNavigationView=activity!!.findViewById(R.id.bottomNavigationView)
        navBar.setSelectedItemId(R.id.optionsFragment)
        }
    fun onSwipeRight(args: GestureEventArgs) {
        val navBar: BottomNavigationView=activity!!.findViewById(R.id.bottomNavigationView)
        navBar.setSelectedItemId(R.id.configListFragment)
        }
    fun onSwipeUp(args: GestureEventArgs) {
        val navBar: BottomNavigationView=activity!!.findViewById(R.id.bottomNavigationView)
        navBar.setSelectedItemId(R.id.conversationFragment)
        }
    fun onTap(args: GestureEventArgs) {
        scanButtonClick(scanButton)
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
    fun extractImageFromProxy(imageProxy: ImageProxy): ByteArray? {
        val mediaImage=imageProxy.image
        if (mediaImage!=null) {
            if (mediaImage.format==ImageFormat.JPEG) {
                val buffer=mediaImage.planes[0].buffer //ByteBuffer
                val bytes=ByteArray(buffer.remaining())
                buffer.get(bytes)

                imageProxy.close()
                return bytes
                }
            else {
                toast("Error: The camera returned an unsupported image type")
                }
            }
        imageProxy.close()
        return null
        }

    fun onTouch(v: View, event: MotionEvent): Boolean {
        touchWrapper.update(event)

        return true
        }

    fun setMultipurposeInputPurpose(purpose: MultipurposeInputPurpose, focus: Boolean=false) {
        if (!multipurposeInput.text.isBlank())
        multipurposeInput.text.clear()
        multipurposeInputPurpose=purpose

        multipurposeInput.hint=when (purpose) {
            MultipurposeInputPurpose.MESSAGE -> "Message"
            MultipurposeInputPurpose.SYSTEM_PROMPT -> "System prompt"
            MultipurposeInputPurpose.USER_PROMPT -> "User prompt"
            }

        if (focus)
        multipurposeInput.requestFocus()
        }
    fun resetMultipurposeInput() {
        setMultipurposeInputPurpose(MultipurposeInputPurpose.MESSAGE)
        }
    fun confirmMultipurposeInput() {
        val text=multipurposeInput.text.toString()
        val purpose=multipurposeInputPurpose

        resetMultipurposeInput()

        if (text.isBlank()) {
            return
            }

        launch { adapter.mutex.withLock() {
            when (purpose) {
                MultipurposeInputPurpose.MESSAGE -> {
                    sendMessage(adapter, text)
                    }
                MultipurposeInputPurpose.SYSTEM_PROMPT -> {
                    setSystemPrompt(adapter, text)
                    }
                MultipurposeInputPurpose.USER_PROMPT -> {
                    setUserPrompt(adapter, text)
                    }
                }
            }}
        }

    fun performAction(action: Action) {
        when (action) {
            is ScanWithActiveConfigAction -> {
                scanButtonClick(scanButton)
                }
            is ScanWithConfigAction -> {
                val config=configManager.getConfig(action.config)
                ?: configManager.getBaseConfig()

                launch { adapter.mutex.withLock {
                    scanWithConfig(adapter, config)
                    }}
                }
            is ConsultConfigAction -> {
                val config=configManager.getConfig(action.config)
                ?: configManager.getBaseConfig()

                launch { adapter.mutex.withLock {
                    consultConfig(adapter, config)
                    }}
                }
            is AskAction -> {
                askButtonClick(askButton)
                }
            is SetSystemPromptAction -> {
                systemPromptButtonClick(systemPromptButton)
                }
            is SetUserPromptAction -> {
                userPromptButtonClick(userPromptButton)
                }
            }
        }

    suspend fun bindCamera(adapter: TabAdapter) {
        cameraProvider?.unbindAll()

        val cameraSelector=when (adapter.activeConfig.camera) {
            UsedCamera.BACK_CAMERA -> CameraSelector.DEFAULT_BACK_CAMERA
            UsedCamera.FRONT_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
            }

        if (!adapter.activeConfig.highRes)
        camera=cameraProvider?.bindToLifecycle(activity!!, cameraSelector, imageCapture)
        else
        camera=cameraProvider?.bindToLifecycle(activity!!, cameraSelector, highResImageCapture)

        configUsedByCamera=adapter.activeConfig
        }
    fun takePicture(adapter: TabAdapter, callback: (ByteArray) -> Unit) {
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
                    val image=extractImageFromProxy(imageProxy)
                    callback(image ?: return)
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
                    val image=extractImageFromProxy(imageProxy)
                    callback(image ?: return)
                    }

                override fun onError(error: ImageCaptureException) {
                    toast("Error capturing the image")
                    }
                },
            )
        }
    fun takePictureToAdapter(adapter: TabAdapter, callback: suspend (TabAdapter) -> Unit) {
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
                    val image=extractImageFromProxy(imageProxy)
                    val timestamp=LocalDateTime.now()

                    launch { adapter.mutex.withLock {
                        adapter.lastTakenImage=image
                        adapter.lastTakenImageTimestamp=timestamp

                        callback(adapter)
                        }}
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
                    val image=extractImageFromProxy(imageProxy)
                    val timestamp=LocalDateTime.now()

                    launch { adapter.mutex.withLock {
                        adapter.lastTakenImage=image
                        adapter.lastTakenImageTimestamp=timestamp

                        callback(adapter)
                        }}
                    }

                override fun onError(error: ImageCaptureException) {
                    toast("Error capturing the image")
                    }
                },
            )
        }
    fun scanWithConfig(adapter: TabAdapter, config: Config) {
        takePictureToAdapter(adapter, callback@{adapter ->
            if (settings.useSounds)
            resources.shutterSound.play()

            toast(adapter.consultConfig(config) ?: return@callback)
            })
        }
    suspend fun consultConfig(adapter: TabAdapter, config: Config) {
        toast(adapter.consultConfig(config) ?: return)
        }
    suspend fun sendMessage(adapter: TabAdapter, message: String) {
        adapter.conversation.addMessage(TextMessage(message))
        val response=adapter.conversation.generateResponse()
        toast(response)
        }
    fun setSystemPrompt(adapter: TabAdapter, message: String, includePromptInConfirmation: Boolean=false) {
        adapter.activeConfig=adapter.activeConfig.withSystemPrompt(message)

        if (!includePromptInConfirmation)
        toast("System prompt set")
        else
        toast("${message} set as system prompt")
        }
    fun setUserPrompt(adapter: TabAdapter, message: String, includePromptInConfirmation: Boolean=false) {
        adapter.activeConfig=adapter.activeConfig.withUserPrompt(message)

        if (!includePromptInConfirmation)
        toast("User prompt set")
        else
        toast("${message} set as user prompt")
        }
    fun checkShareBox() {
        val shareBox=ShareBox.getInstance(context!!)
        val image=shareBox.popImage() ?: return
        val timestamp=LocalDateTime.now()

        launch { adapter.mutex.withLock {
            adapter.lastTakenImage=image
            adapter.lastTakenImageTimestamp=timestamp

            val config=settings.getShareConfig(configManager)

            adapter.conversation=Conversation(settings.apiKey, config.model.identifier, config.systemPromptOrNull)

            val encodedImage=Base64.getEncoder().encodeToString(image)
            adapter.conversation.addMessage(ImageMessage(
                config.userPrompt,
                LocalImage(encodedImage),
                ))
            val response=adapter.conversation.generateResponse()
            toast(response)
            }}
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
