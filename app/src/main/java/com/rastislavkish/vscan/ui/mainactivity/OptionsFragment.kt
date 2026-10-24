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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.json.JsonPrimitive

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.ReasoningEffort
import com.rastislavkish.vscan.core.config.CaptureMode
import com.rastislavkish.vscan.core.config.ChangeMarkerMeaning
import com.rastislavkish.vscan.core.config.IterationMode
import com.rastislavkish.vscan.core.config.LensTarget
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.config.LlmChangeDetection
import com.rastislavkish.vscan.core.config.LlmChangeDetectionMode
import com.rastislavkish.vscan.core.config.MarkerMatch
import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.config.PlayOperation
import com.rastislavkish.vscan.core.config.SayOperation
import com.rastislavkish.vscan.core.config.SetOperation
import com.rastislavkish.vscan.core.config.ToastOperation
import com.rastislavkish.vscan.core.config.VibrateOperation
import com.rastislavkish.vscan.core.config.Lifetime
import com.rastislavkish.vscan.core.imagesource.Crop
import com.rastislavkish.vscan.core.imagesource.Downscale
import com.rastislavkish.vscan.core.imagesource.LensOperation
import com.rastislavkish.vscan.core.imagesource.RelativeRect
import com.rastislavkish.vscan.core.imagesource.Zoom
import com.rastislavkish.vscan.core.process.ProcessManager

import com.rastislavkish.vscan.ui.form.Descriptions
import com.rastislavkish.vscan.ui.form.Form
import com.rastislavkish.vscan.ui.itemeditoractivity.ItemEditorActivityInput
import com.rastislavkish.vscan.ui.itemeditoractivity.ItemEditorActivityOutput
import com.rastislavkish.vscan.ui.itemeditoractivity.ItemForm
import com.rastislavkish.vscan.ui.itemeditoractivity.ItemKind
import com.rastislavkish.vscan.ui.itemeditoractivity.MemoryVariable
import com.rastislavkish.vscan.ui.textinputactivity.TextInputActivity
import com.rastislavkish.vscan.ui.textinputactivity.TextInputActivityInput
import com.rastislavkish.vscan.ui.textinputactivity.TextInputActivityOutput
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivity
import com.rastislavkish.vscan.ui.modelselectionactivity.DisplayedModels
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivityInput
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivityOutput
import com.rastislavkish.vscan.ui.settingsactivity.SettingsActivity

// The editor of the foreground process' config, with a heading per section: identity, analyzer, output operations and lifetime, in the order of the config file
// The form works with its own copy of the config, every edit applied to the copy at once and to the process' config under the adapter's mutex
class OptionsFragment: Fragment(), CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private lateinit var job: Job

    private lateinit var adapter: TabAdapter
    private lateinit var processManager: ProcessManager

    private lateinit var form: Form
    private var config=Config()

    private lateinit var updateButton: Button
    private lateinit var createButton: Button
    private lateinit var deleteButton: Button

    private lateinit var textInputActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var modelSelectionActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var itemEditorActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
        ): View? {
        return inflater.inflate(R.layout.fragment_options, container, false)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        job=Job()

        adapter=TabAdapter.getInstance(context!!)
        processManager=ProcessManager.getInstance(context!!)

        form=Form(context!!, view.findViewById<LinearLayout>(R.id.formContainer))
        buildForm()

        updateButton=view.findViewById(R.id.updateButton)
        updateButton.setOnClickListener(this::onUpdateButtonClick)
        createButton=view.findViewById(R.id.createButton)
        createButton.setOnClickListener(this::onCreateButtonClick)
        deleteButton=view.findViewById(R.id.deleteButton)
        deleteButton.setOnClickListener(this::onDeleteButtonClick)

        val settingsButton: Button=view.findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener(this::onSettingsButtonClick)

        textInputActivityLauncher=registerForActivityResult(StartActivityForResult(), this::onTextInputActivityResult)
        modelSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::onModelSelectionActivityResult)
        itemEditorActivityLauncher=registerForActivityResult(StartActivityForResult(), this::onItemEditorActivityResult)

        runBlocking { adapter.mutex.withLock {
            config=adapter.activeConfig
            }}
        updateUI()
        }

    override fun onResume() {
        launch {
            adapter.mutex.withLock {
                config=adapter.activeConfig
                }
            updateUI()
            }

        super.onResume()
        }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
        }

    private fun buildForm() {
        val llm={ config.llm }
        val detection={ config.llm.changeDetection }
        val lifetime={ config.lifetime }

        // Shown only when there's a problem, e.g. a template syntax error
        val problem=form.label { processManager.problem(config)?.let { "Problem: $it" } }
        problem.accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE

        form.heading("Identity")
        form.text("Config name", { config.name }, { value -> edit { it.copy(name=value) } })
        form.optionalText("Command", { config.command }, { value -> edit { it.copy(command=value) } })
        form.text("Description", { config.description }, { value -> edit { it.copy(description=value) } }, multiLine=true)

        form.label { "Parameters" }
        form.list({ config.allParameters }, Descriptions::parameter, { index ->
            val parameter=config.allParameters[index]
            startItemEditorActivity(ItemKind.PARAMETER, "Parameter ${parameter.name}", ItemForm.encode(parameter), index)
            }) { from, to -> edit { withParameters(it, moved(it.allParameters, from, to)) } }
        form.button("Add parameter") {
            startItemEditorActivity(ItemKind.PARAMETER, "New parameter", ItemForm.encode(Parameter("", ParameterType.STRING)))
            }

        form.label { "Memory" }
        form.list({ memoryVariables(config) }, { Descriptions.memoryVariable(it.name, it.value) }, { index ->
            val variable=memoryVariables(config)[index]
            startItemEditorActivity(ItemKind.MEMORY_VARIABLE, "Memory variable ${variable.name}", ItemForm.encode(variable), index)
            })
        form.button("Add memory variable") {
            startItemEditorActivity(ItemKind.MEMORY_VARIABLE, "New memory variable", ItemForm.encode(MemoryVariable("", JsonPrimitive(""))))
            }

        form.label { "Lens" }
        form.choice("Lens applies to", listOf(
            "The image parameter" to LensTarget.IMAGE,
            "All image source parameters" to LensTarget.ALL,
            ), { config.lens.appliesTo }) { value -> edit { it.copy(lens=it.lens.copy(appliesTo=value)) } }
        form.list({ config.lens.operations }, Descriptions::lensOperation, { index ->
            val operation=config.lens.operations[index]
            startItemEditorActivity(ItemKind.LENS_OPERATION, lensOperationName(operation), ItemForm.encode(operation), index)
            }) { from, to -> edit { it.copy(lens=it.lens.copy(operations=moved(it.lens.operations, from, to))) } }
        form.button("Add lens operation") { addLensOperation() }

        form.heading("Analyzer")
        form.link({ "System prompt: ${summarizeOrNone(config.llm.systemPrompt)}" }) {
            startTextInputActivity("System prompt", config.llm.systemPrompt, "system-prompt")
            }
        form.link({ "User prompt: ${summarizeOrNone(config.llm.userPrompt)}" }) {
            startTextInputActivity("User prompt", config.llm.userPrompt, "user-prompt")
            }
        form.switch("Append the image to the user prompt, unless it places an image itself", { llm().appendImage }) { value -> editLlm { it.copy(appendImage=value) } }
        form.text("Model", { llm().model }, { value -> editLlm { it.copy(model=value) } })
        form.button("Select model") { startModelSelectionActivity() }
        form.int("Max completion tokens", { llm().maxCompletionTokens }) { value -> editLlm { it.copy(maxCompletionTokens=value) } }
        form.choice("Reasoning effort", listOf(
            "Unspecified" to null,
            "None" to ReasoningEffort.NONE,
            "Minimal" to ReasoningEffort.MINIMAL,
            "Low" to ReasoningEffort.LOW,
            "Medium" to ReasoningEffort.MEDIUM,
            "High" to ReasoningEffort.HIGH,
            "Xhigh" to ReasoningEffort.XHIGH,
            ), { llm().reasoningEffort }) { value -> editLlm { it.copy(reasoningEffort=value) } }

        form.label { "Change detection" }
        form.choice("Change detection mode", listOf(
            "App driven (the app compares the output with the last one)" to LlmChangeDetectionMode.APP_DRIVEN,
            "LLM driven (the model marks changes)" to LlmChangeDetectionMode.LLM_DRIVEN,
            ), { detection().mode }) { value -> editDetection { it.copy(mode=value) } }
        val marker=form.text("Change marker", { detection().changeMarker }, { value -> editDetection { it.copy(changeMarker=value) } })
        val markerMeaning=form.choice("The marker means", listOf(
            "No change" to ChangeMarkerMeaning.NO_CHANGE,
            "A change" to ChangeMarkerMeaning.CHANGE,
            ), { detection().changeMarkerMeaning }) { value -> editDetection { it.copy(changeMarkerMeaning=value) } }
        val markerMatch=form.choice("Marker match", listOf(
            "Exact" to MarkerMatch.EXACT,
            "Wildcard" to MarkerMatch.WILDCARD,
            "Regex" to MarkerMatch.REGEX,
            ), { detection().changeMarkerMatch }) { value -> editDetection { it.copy(changeMarkerMatch=value) } }
        form.visibleWhen({ detection().mode==LlmChangeDetectionMode.LLM_DRIVEN }, marker, markerMeaning, markerMatch)

        form.heading("Output operations")
        form.list({ config.operations }, Descriptions::operation, { index ->
            val operation=config.operations[index]
            startItemEditorActivity(ItemKind.OPERATION, operationName(operation), ItemForm.encode(operation), index)
            }) { from, to -> edit { it.copy(operations=moved(it.operations, from, to)) } }
        form.button("Add operation") { addOperation() }

        form.heading("Lifetime")
        form.double("Interval in seconds, 0 runs once", { lifetime().interval }) { value -> editLifetime { it.copy(interval=value) } }
        form.choice("Iteration mode", listOf("Sync" to IterationMode.SYNC, "Async" to IterationMode.ASYNC), { lifetime().mode }) { value -> editLifetime { it.copy(mode=value) } }
        val maxInFlight=form.int("Max analyses at once", { lifetime().maxInFlight }) { value -> editLifetime { it.copy(maxInFlight=value) } }
        form.visibleWhen({ lifetime().mode==IterationMode.ASYNC }, maxInFlight)
        form.optionalText("Iteration condition", { lifetime().iterationCondition }, { value -> editLifetime { it.copy(iterationCondition=value) } })
        form.double("Analysis timeout in seconds, 0 for none", { lifetime().analysisTimeout }) { value -> editLifetime { it.copy(analysisTimeout=value) } }
        form.double("Error interval in seconds, 0 for the interval", { lifetime().errorInterval }) { value -> editLifetime { it.copy(errorInterval=value) } }
        form.double("Error backoff", { lifetime().errorBackoff }) { value -> editLifetime { it.copy(errorBackoff=value) } }
        form.double("Max error interval in seconds, 0 for none", { lifetime().maxErrorInterval }) { value -> editLifetime { it.copy(maxErrorInterval=value) } }
        form.choice("Capture", listOf("Auto" to CaptureMode.AUTO, "Photo" to CaptureMode.PHOTO, "Stream frame" to CaptureMode.FRAME), { lifetime().capture }) { value -> editLifetime { it.copy(capture=value) } }
        form.choice("Flashlight mode", listOf("Default" to FlashlightMode.DEFAULT, "On" to FlashlightMode.ON, "Off" to FlashlightMode.OFF), { lifetime().flashlight }) { value -> editLifetime { it.copy(flashlight=value) } }
        form.int("Max iterations, 0 for no limit", { lifetime().maxIterations }) { value -> editLifetime { it.copy(maxIterations=value) } }
        form.double("Max duration in seconds, 0 for no limit", { lifetime().maxDuration }) { value -> editLifetime { it.copy(maxDuration=value) } }
        form.int("Max tokens, 0 for no limit", { lifetime().maxTokens }) { value -> editLifetime { it.copy(maxTokens=value) } }

        form.label { "Debugging" }
        form.optionalText("Iteration start sound", { config.debug.iterationStartSound }, { value -> edit { it.copy(debug=it.debug.copy(iterationStartSound=value)) } })
        form.optionalText("Iteration end sound", { config.debug.iterationEndSound }, { value -> edit { it.copy(debug=it.debug.copy(iterationEndSound=value)) } })
        }

    // Applies the change to the form's copy at once, and to the process' config as soon as the adapter is free
    // The form refreshes itself after the edits of its fields, the other callers update the UI
    private fun edit(transform: (Config) -> Config) {
        config=transform(config)

        launch { adapter.mutex.withLock {
            val old=adapter.activeConfig
            val new=transform(old)
            adapter.activeConfig=new

            // The conversation continued by Ask follows the model settings
            val conversation=adapter.conversation
            if (new.llm.model!=old.llm.model)
            conversation.model=new.llm.model
            if (new.llm.maxCompletionTokens!=old.llm.maxCompletionTokens)
            conversation.maxCompletionTokens=new.llm.maxCompletionTokens
            if (new.llm.reasoningEffort!=old.llm.reasoningEffort)
            conversation.reasoningEffort=new.llm.reasoningEffort
            }}
        }
    private fun editLlm(transform: (LlmAnalyzer) -> LlmAnalyzer) {
        edit { it.withLlm(transform) }
        }
    private fun editDetection(transform: (LlmChangeDetection) -> LlmChangeDetection) {
        editLlm { it.copy(changeDetection=transform(it.changeDetection)) }
        }
    private fun editLifetime(transform: (Lifetime) -> Lifetime) {
        edit { it.copy(lifetime=transform(it.lifetime)) }
        }

    fun onUpdateButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            val originalConfig=adapter.activeConfig
            ConfigManager.getInstance(context!!).updateConfig(adapter.activeConfig)
            toast("Updated config ${originalConfig.name}")
            }}
        }
    fun onCreateButtonClick(v: View) {
        launch {
            adapter.mutex.withLock {
                val newConfig=ConfigManager.getInstance(context!!).addConfig(adapter.activeConfig)
                adapter.activeConfig=newConfig
                config=newConfig
                toast("created a new config called ${newConfig.name}")
                }
            updateUI()
            }
        }
    fun onDeleteButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            val activeConfig=adapter.activeConfig
            ConfigManager.getInstance(context!!).deleteConfig(activeConfig)
            toast("Config ${activeConfig.name} deleted")
            }}
        }

    fun onSettingsButtonClick(v: View) {
        val intent=Intent(context!!, SettingsActivity::class.java)
        startActivity(intent)
        }

    fun toast(text: String) {
        Toast.makeText(activity!!, text, Toast.LENGTH_LONG).show()
        }

    private fun updateUI() {
        form.refresh()

        updateButton.setClickable(!ConfigManager.isBuiltInConfigId(config.id))
        deleteButton.setClickable(!ConfigManager.isBuiltInConfigId(config.id))
        }

    private fun summarizeOrNone(text: String): String = if (!text.isEmpty()) Descriptions.summarize(text, 300) else "None"

    private fun addOperation() {
        val operations=listOf<Operation>(
            ToastOperation(),
            SayOperation(),
            PlayOperation(sound="beep"),
            VibrateOperation(),
            SetOperation(name=memoryVariables(config).firstOrNull()?.name ?: "", value=""),
            )
        chooseNew("Add operation", operations.map(::operationName)) { index ->
            val operation=operations[index]
            startItemEditorActivity(ItemKind.OPERATION, "New ${operationName(operation).lowercase()}", ItemForm.encode(operation))
            }
        }
    private fun addLensOperation() {
        val operations=listOf<LensOperation>(
            Crop(RelativeRect.FULL),
            Zoom(2f),
            Downscale(1024),
            )
        chooseNew("Add lens operation", operations.map(::lensOperationName)) { index ->
            val operation=operations[index]
            startItemEditorActivity(ItemKind.LENS_OPERATION, "New ${lensOperationName(operation).lowercase()}", ItemForm.encode(operation))
            }
        }
    private fun chooseNew(title: String, names: List<String>, onChoice: (Int) -> Unit) {
        AlertDialog.Builder(context!!)
        .setTitle(title)
        .setItems(names.toTypedArray()) { _, index -> onChoice(index) }
        .setNegativeButton("Cancel", null)
        .show()
        }

    private fun operationName(operation: Operation): String {
        return when (operation) {
            is ToastOperation -> "Toast operation"
            is SayOperation -> "Say operation"
            is PlayOperation -> "Play operation"
            is VibrateOperation -> "Vibrate operation"
            is SetOperation -> "Set operation"
            }
        }
    private fun lensOperationName(operation: LensOperation): String {
        return when (operation) {
            is Crop -> "Crop"
            is Zoom -> "Zoom"
            is Downscale -> "Downscale"
            }
        }

    private fun startTextInputActivity(title: String, text: String, context: String) {
        val intent=TextInputActivityInput(title, text, context)
        .toIntent(activity!!)
        textInputActivityLauncher.launch(intent)
        }
    private fun onTextInputActivityResult(result: ActivityResult) {
        if (result.resultCode==androidx.appcompat.app.AppCompatActivity.RESULT_OK) {
            val output=TextInputActivityOutput.fromIntent(result.data, "OptionsFragment")

            when (output.context) {
                "system-prompt" -> editLlm { it.copy(systemPrompt=output.text) }
                "user-prompt" -> editLlm { it.copy(userPrompt=output.text) }
                else -> toast("Error: Received text input for unknown field ${output.context}")
                }
            updateUI()
            }
        }
    private fun startModelSelectionActivity() {
        val intent=ModelSelectionActivityInput(DisplayedModels.READY_TO_USE)
        .toIntent(activity!!)
        modelSelectionActivityLauncher.launch(intent)
        }
    private fun onModelSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==androidx.appcompat.app.AppCompatActivity.RESULT_OK) {
            val output=ModelSelectionActivityOutput.fromIntent(result.data, "OptionsFragment")

            if (!output.model.isEmpty()) {
                val model=output.model
                editLlm { it.copy(model=model) }
                updateUI()
                }
            }
        }
    private fun startItemEditorActivity(kind: ItemKind, title: String, item: String, index: Int=-1) {
        val intent=ItemEditorActivityInput(kind, title, item, index)
        .toIntent(activity!!)
        itemEditorActivityLauncher.launch(intent)
        }
    private fun onItemEditorActivityResult(result: ActivityResult) {
        if (result.resultCode==androidx.appcompat.app.AppCompatActivity.RESULT_OK) {
            val output=ItemEditorActivityOutput.fromIntent(result.data, "OptionsFragment")
            val json=output.item
            val index=output.index

            when (output.kind) {
                ItemKind.PARAMETER -> edit { withParameters(it, replaced(it.allParameters, index, json?.let { ItemForm.decodeParameter(it) })) }
                ItemKind.MEMORY_VARIABLE -> edit { config ->
                    val variables=replaced(memoryVariables(config), index, json?.let { ItemForm.decodeMemoryVariable(it) })
                    config.copy(memory=variables.associate { it.name to it.value })
                    }
                ItemKind.OPERATION -> edit { it.copy(operations=replaced(it.operations, index, json?.let { ItemForm.decodeOperation(it) })) }
                ItemKind.LENS_OPERATION -> edit { it.copy(lens=it.lens.copy(operations=replaced(it.lens.operations, index, json?.let { ItemForm.decodeLensOperation(it) }))) }
                }
            updateUI()
            }
        }

    companion object {

        // The list with the item at the index replaced, removed when null, or added for index -1
        fun <T> replaced(list: List<T>, index: Int, item: T?): List<T> {
            val result=list.toMutableList()
            if (index<0) {
                if (item!=null)
                result.add(item)
                }
            else if (index<result.size) {
                if (item!=null)
                result[index]=item
                else
                result.removeAt(index)
                }
            return result
            }

        fun <T> moved(list: List<T>, from: Int, to: Int): List<T> {
            val result=list.toMutableList()
            result.add(to, result.removeAt(from))
            return result
            }

        // The list shows all parameters, the implicit image one included, which is declared only when it differs from the default
        fun withParameters(config: Config, parameters: List<Parameter>): Config {
            return config.copy(parameters=parameters.filter { it!=Parameter.defaultImageParameter })
            }

        fun memoryVariables(config: Config): List<MemoryVariable> = config.memory.map { (name, value) -> MemoryVariable(name, value) }
        }
    }
