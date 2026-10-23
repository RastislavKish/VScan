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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import androidx.fragment.app.Fragment
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import android.content.ClipboardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentValues
import android.content.Intent
import android.content.Context

import android.net.Uri

import android.provider.MediaStore

import android.widget.Button
import android.widget.ToggleButton
import android.widget.Toast
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.google.android.material.bottomnavigation.BottomNavigationView

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

import com.rastislavkish.rtk.TouchWrapper
import com.rastislavkish.rtk.GestureEventArgs

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.ProvidersManager
import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.STT
import com.rastislavkish.vscan.core.Resources
import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.Timing
import com.rastislavkish.vscan.core.camera.CaptureException
import com.rastislavkish.vscan.core.files.FileSync
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSources
import com.rastislavkish.vscan.core.process.CommandInterpreter
import com.rastislavkish.vscan.core.process.CommandLine
import com.rastislavkish.vscan.core.process.ProcessException
import com.rastislavkish.vscan.core.process.ProcessManager
import com.rastislavkish.vscan.core.speech.SpeechSystem
import com.rastislavkish.vscan.core.openai.*

import com.rastislavkish.vscan.core.Action
import com.rastislavkish.vscan.core.ScanWithActiveConfigAction
import com.rastislavkish.vscan.core.ScanWithConfigAction
import com.rastislavkish.vscan.core.ConsultConfigAction
import com.rastislavkish.vscan.core.AskAction
import com.rastislavkish.vscan.core.SetSystemPromptAction
import com.rastislavkish.vscan.core.SetUserPromptAction
import com.rastislavkish.vscan.core.StopSpeechAction
import com.rastislavkish.vscan.core.RunCommandAction

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
    private lateinit var providersManager: ProvidersManager
    private lateinit var configManager: ConfigManager
    private lateinit var imageSources: ImageSources
    private lateinit var speechSystem: SpeechSystem
    private lateinit var processManager: ProcessManager
    private lateinit var commandInterpreter: CommandInterpreter
    private lateinit var touchWrapper: TouchWrapper

    private lateinit var scanButton: Button
    private lateinit var askButton: Button
    private lateinit var systemPromptButton: Button
    private lateinit var userPromptButton: Button
    private lateinit var multipurposeInputLayout: TextInputLayout
    private lateinit var multipurposeInput: TextInputEditText
    private var multipurposeInputPurpose=MultipurposeInputPurpose.COMMAND
    // The config the arguments are for
    private var argumentsConfig: Config?=null
    private var scanButtonActionIds=listOf<Int>()

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
        providersManager=ProvidersManager.getInstance(context!!)
        configManager=ConfigManager.getInstance(context!!)
        imageSources=ImageSources.getInstance(context!!)
        speechSystem=SpeechSystem.getInstance(context!!)
        processManager=ProcessManager.getInstance(context!!)
        commandInterpreter=CommandInterpreter(configManager::getList, processManager) { FileSync.reload(context!!) }
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

        multipurposeInputLayout=view.findViewById(R.id.multipurposeInputLayout)
        multipurposeInput=view.findViewById(R.id.multipurposeInput)
        multipurposeInput.setOnEditorActionListener(this::onMultipurposeInputEditorAction)

        launch {
            try {
                imageSources.phoneCamera.prepare()
                }
            catch (e: CaptureException) {
                toast(e.message ?: "Error")
                }
            }

        try {
            EventBus.getDefault().register(this)
            }
        catch (e: Exception) {

            }
        }

    override fun onResume() {
        super.onResume()

        checkShareBox()
        checkConfigAwaitingArguments()
        updateScanButtonActions()
        }
    override fun onPause() {
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

    // Runs an iteration of the foreground process, its operations reporting the result
    fun scanButtonClick(v: View) {
        launch {
            val foreground=adapter.mutex.withLock { adapter.foreground }
            val start=Timing.now()

            foreground.scan(settings.reportEveryScan) {
                if (settings.useSounds)
                launch { resources.shutterSound.play() }
                }
            Timing.logSince("Scan total", start)
            }
        }
    // The accessibility actions of the scan button, labeled by the foreground process' state
    fun updateScanButtonActions() {
        for (id in scanButtonActionIds)
        ViewCompat.removeAccessibilityAction(scanButton, id)

        val looping=adapter.foreground.isIterating
        scanButtonActionIds=listOf(
            ViewCompat.addAccessibilityAction(scanButton, if (looping) "Stop looping" else "Start looping") { _, _ ->
                toggleLooping()
                true
                },
            ViewCompat.addAccessibilityAction(scanButton, "Set arguments") { _, _ ->
                launch {
                    val config=adapter.mutex.withLock { adapter.activeConfig }
                    requestArguments(config)
                    }
                true
                },
            )
        }
    fun toggleLooping() {
        launch {
            adapter.mutex.withLock {
                val foreground=adapter.foreground
                val name=foreground.config.name

                if (foreground.isIterating) {
                    processManager.stopForeground()
                    toast("Stopped looping")
                    }
                else if (!foreground.config.lifetime.isLooping)
                toast("$name doesn't loop")
                else {
                    processManager.startForeground()
                    toast("Looping $name")
                    }
                }
            updateScanButtonActions()
            }
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
            val taken=adapter.lastImage.taken ?: return@launch
            val image=taken.image
            val timestamp=taken.timestamp



            if (settings.describeSavedImages) {
                val fileDescriptionConfig=settings.getFileDescriptionConfig(configManager)
                val conversation=Conversation(
                    providersManager,
                    fileDescriptionConfig.llm.model,
                    fileDescriptionConfig.llm.maxCompletionTokens,
                    fileDescriptionConfig.llm.reasoningEffort,
                    fileDescriptionConfig.systemPromptOrNull,
                    )

                conversation.addMessage(ImageMessage(
                    fileDescriptionConfig.llm.userPrompt,
                    LocalImage(image),
                    ))

                var errorMessage: String?=null

                var fileName=try {
                    val response=conversation.generateResponse().text
                    "$response-${timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}.${image.encodedFormat.extension}"
                    }
                catch (e: Exception) {
                    errorMessage=e.message ?: ""
                    "${timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}.${image.encodedFormat.extension}"
                    }

                if (fileName.isEmpty() && errorMessage==null) {
                    errorMessage="Reasoning exceeded the token limit"
                    fileName="${timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}.${image.encodedFormat.extension}"
                    }

                try {
                    saveToGallery(fileName, image)

                    if (errorMessage==null)
                    toast("Saved as $fileName")
                    else
                    toast("Error describing the image: $errorMessage Image saved as $fileName")
                    }
                catch (e: Exception) {
                    toast("Saving failed: ${e.message}")
                    }
                }
            else {
                val fileName="${timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}.${image.encodedFormat.extension}"
                try {
                    saveToGallery(fileName, image)
                    toast("Saved as $fileName")
                    }
                catch (e: Exception) {
                    toast("Saving failed: ${e.message}")
                    }
                }
            }}
        }
    fun resetConfigButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            adapter.resetActiveConfig()
            adapter.lastImage.clear()

            toast("Reset to ${adapter.activeConfig.name}")
            }
            updateScanButtonActions()
            }
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

    fun onTouch(v: View, event: MotionEvent): Boolean {
        touchWrapper.update(event)

        return true
        }

    fun setMultipurposeInputPurpose(purpose: MultipurposeInputPurpose, focus: Boolean=false) {
        if (!(multipurposeInput.text?.isBlank() ?: false))
        multipurposeInput.text?.clear()
        multipurposeInputPurpose=purpose

        multipurposeInputLayout.hint=when (purpose) {
            MultipurposeInputPurpose.COMMAND -> "Command"
            MultipurposeInputPurpose.MESSAGE -> "Message"
            MultipurposeInputPurpose.SYSTEM_PROMPT -> "System prompt"
            MultipurposeInputPurpose.USER_PROMPT -> "User prompt"
            MultipurposeInputPurpose.ARGUMENTS -> "Arguments for ${argumentsConfig?.name}"
            }

        if (focus) {
            multipurposeInput.requestFocus()

            val imm=context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(multipurposeInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    fun resetMultipurposeInput() {
        setMultipurposeInputPurpose(MultipurposeInputPurpose.COMMAND)
        }
    // The purpose stays until an empty input returns the field to the command line
    fun confirmMultipurposeInput() {
        val text=multipurposeInput.text?.toString() ?: return
        val purpose=multipurposeInputPurpose

        if (text.isBlank()) {
            if (purpose!=MultipurposeInputPurpose.COMMAND) {
                resetMultipurposeInput()
                toast("Command line")
                }
            return
            }

        multipurposeInput.text?.clear()

        // A leading space sends a message instead of a command
        if (purpose==MultipurposeInputPurpose.COMMAND && !text.startsWith(" ")) {
            toast(commandInterpreter.execute(text))
            // fg and bg may have changed the foreground process
            updateScanButtonActions()
            return
            }
        if (purpose==MultipurposeInputPurpose.ARGUMENTS) {
            loadWithArguments(text)
            return
            }

        launch { adapter.mutex.withLock() {
            when (purpose) {
                MultipurposeInputPurpose.COMMAND, MultipurposeInputPurpose.MESSAGE -> {
                    sendMessage(adapter, text.trim())
                    }
                MultipurposeInputPurpose.SYSTEM_PROMPT -> {
                    setSystemPrompt(adapter, text)
                    }
                MultipurposeInputPurpose.USER_PROMPT -> {
                    setUserPrompt(adapter, text)
                    }
                MultipurposeInputPurpose.ARGUMENTS -> {}
                }
            }}
        }

    // Asks for the arguments to load the config into the foreground with
    fun requestArguments(config: Config) {
        argumentsConfig=config
        setMultipurposeInputPurpose(MultipurposeInputPurpose.ARGUMENTS, true)
        }
    fun checkConfigAwaitingArguments() {
        launch {
            val config=adapter.mutex.withLock {
                val config=adapter.configAwaitingArguments
                adapter.configAwaitingArguments=null
                config
                } ?: return@launch

            requestArguments(config)
            }
        }
    // Returns to the command line once loaded, an error leaves the arguments to correct
    fun loadWithArguments(text: String) {
        val config=argumentsConfig ?: return

        launch {
            try {
                adapter.mutex.withLock {
                    adapter.loadConfig(config, CommandLine.parseArguments(text).bind(config))
                    }
                }
            catch (e: ProcessException) {
                multipurposeInput.setText(text)
                toast(e.message ?: "Error")
                return@launch
                }

            resetMultipurposeInput()
            updateScanButtonActions()
            toast("${config.name} is in the foreground")
            }
        }

    fun performAction(action: Action) {
        when (action) {
            is ScanWithActiveConfigAction -> {
                scanButtonClick(scanButton)
                }
            // These run in the background
            is ScanWithConfigAction -> {
                val config=configManager.getConfig(action.config)
                ?: configManager.getBaseConfig()

                spawn(config)
                }
            is ConsultConfigAction -> {
                val config=configManager.getConfig(action.config)
                ?: configManager.getBaseConfig()

                spawn(config, mapOf("image" to "\$lastImage"))
                }
            is RunCommandAction -> {
                toast(commandInterpreter.execute(action.command))
                updateScanButtonActions()
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
            is StopSpeechAction -> {
                speechSystem.stop()
                }
            }
        }

    fun spawn(config: Config, given: Map<String, String> = mapOf()) {
        try {
            processManager.spawn(config, given)
            }
        catch (e: ProcessException) {
            toast(e.message ?: "Error")
            }
        }
    suspend fun sendMessage(adapter: TabAdapter, message: String) {
        adapter.conversation.addMessage(TextMessage(message))

        try {
            val response=adapter.conversation.generateResponse()
            toastResponse(response)
            }
        catch (e: Exception) {
            toast(e.message ?: "Error")
            }
        }
    fun setSystemPrompt(adapter: TabAdapter, message: String, includePromptInConfirmation: Boolean=false) {
        adapter.activeConfig=adapter.activeConfig.withLlm { it.copy(systemPrompt=message) }

        if (!includePromptInConfirmation)
        toast("System prompt set")
        else
        toast("${message} set as system prompt")
        }
    fun setUserPrompt(adapter: TabAdapter, message: String, includePromptInConfirmation: Boolean=false) {
        adapter.activeConfig=adapter.activeConfig.withLlm { it.copy(userPrompt=message) }

        if (!includePromptInConfirmation)
        toast("User prompt set")
        else
        toast("${message} set as user prompt")
        }
    // A shared image becomes the last image, consulted with the share config in the background
    fun checkShareBox() {
        val shareBox=ShareBox.getInstance(context!!)
        val image=shareBox.popImage() ?: return

        adapter.lastImage.set(image)
        spawn(settings.getShareConfig(configManager), mapOf("image" to "\$lastImage"))
        }
    fun toast(text: String) {
        Toast.makeText(activity!!, text, Toast.LENGTH_LONG).show()
        }
    fun toastResponse(response: AssistantMessage) {
        if (!response.text.isEmpty())
        toast(response.text)
        else if (response.finishReason=="length")
        toast("Error: Reasoning exceeded the token limit")
        else
        toast("Error: Received empty output")
        }
    fun createImageFile(fileName: String): File {
        val storageDir: File = if (Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) else activity!!.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: throw Exception("Failed to optain the Pictures folder path")
        val imageFile=File("$storageDir/$fileName")
        imageFile.createNewFile()
        return imageFile
        }
    fun saveToGallery(fileName: String, image: ImageData) {
        val imageFile=createImageFile(fileName)

        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) {
            val intent=Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.setData(Uri.parse("file://${imageFile.absolutePath}"))
            activity!!.sendBroadcast(intent)
            }
        else {
            val contentValues=ContentValues()
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, image.encodedFormat.mimeType)
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
                os?.write(image.encoded)
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
