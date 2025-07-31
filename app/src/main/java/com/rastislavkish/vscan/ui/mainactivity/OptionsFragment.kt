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

import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast

import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.TextController
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.UsedCamera

import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivity
import com.rastislavkish.vscan.ui.modelselectionactivity.DisplayedModels
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivityInput
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivityOutput
import com.rastislavkish.vscan.ui.settingsactivity.SettingsActivity

class OptionsFragment: Fragment(), CoroutineScope {

    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main+job

    private lateinit var job: Job

    private lateinit var adapter: TabAdapter

    private lateinit var systemPromptInput: EditText
    private lateinit var userPromptInput: EditText

    private lateinit var highResSwitch: Switch
    private lateinit var flashlightModeSpinner: Spinner

    private lateinit var cameraSpinner: Spinner
    private lateinit var modelInput: EditText
    private lateinit var selectModelButton: Button

    private lateinit var nameInput: EditText

    private lateinit var updateButton: Button
    private lateinit var createButton: Button
    private lateinit var deleteButton: Button

    private lateinit var modelSelectionActivityLauncher: ActivityResultLauncher<Intent>

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

        systemPromptInput=view.findViewById(R.id.systemPromptInput)
        TextController(systemPromptInput).setTextChangeListener(this::onSystemPromptInputTextChange)
        userPromptInput=view.findViewById(R.id.userPromptInput)
        TextController(userPromptInput).setTextChangeListener(this::onUserPromptInputTextChange)

        highResSwitch=view.findViewById(R.id.highResSwitch)
        highResSwitch.setOnCheckedChangeListener(this::onHighResSwitchCheckedChange)

        flashlightModeSpinner=view.findViewById(R.id.flashlightModeSpinner)
        val flashlightModeSpinnerAdapter=ArrayAdapter<String>(context!!, android.R.layout.simple_spinner_item, flashlightModeSpinnerOptions)
        flashlightModeSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        flashlightModeSpinner.setAdapter(flashlightModeSpinnerAdapter)
        flashlightModeSpinner.setOnItemSelectedListener(object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                onFlashlightModeSpinnerItemSelected(v ?: return, position)
                }
            override fun onNothingSelected(parent: AdapterView<*>) {}
            })

        cameraSpinner=view.findViewById(R.id.cameraSpinner)
        val cameraSpinnerAdapter=ArrayAdapter<String>(context!!, android.R.layout.simple_spinner_item, cameraSpinnerOptions)
        cameraSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        cameraSpinner.setAdapter(cameraSpinnerAdapter)
        cameraSpinner.setOnItemSelectedListener(object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                onCameraSpinnerItemSelected(v ?: return, position)
                }
            override fun onNothingSelected(parent: AdapterView<*>) {}
            })

        modelInput=view.findViewById(R.id.modelInput)
        TextController(modelInput).setTextChangeListener(this::onModelInputTextChange)
        selectModelButton=view.findViewById(R.id.selectModelButton)
        selectModelButton.setOnClickListener(this::onSelectModelButtonClick)

        nameInput=view.findViewById(R.id.nameInput)
        TextController(nameInput).setTextChangeListener(this::onNameInputTextChange)

        updateButton=view.findViewById(R.id.updateButton)
        updateButton.setOnClickListener(this::onUpdateButtonClick)
        createButton=view.findViewById(R.id.createButton)
        createButton.setOnClickListener(this::onCreateButtonClick)
        deleteButton=view.findViewById(R.id.deleteButton)
        deleteButton.setOnClickListener(this::onDeleteButtonClick)

        val settingsButton: Button=view.findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener(this::onSettingsButtonClick)

        modelSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::onModelSelectionActivityResult)
        }

    override fun onResume() {
        launch { adapter.mutex.withLock {
            val uiConfig=getUIConfig(adapter)

            val activeConfig=adapter.activeConfig

            if (uiConfig.systemPrompt!=activeConfig.systemPrompt)
            systemPromptInput.setText(activeConfig.systemPrompt)

            if (uiConfig.userPrompt!=activeConfig.userPrompt)
            userPromptInput.setText(activeConfig.userPrompt)

            if (uiConfig.highRes!=activeConfig.highRes)
            highResSwitch.setChecked(activeConfig.highRes)

            if (uiConfig.flashlightMode!=activeConfig.flashlightMode)
            setSelectedFlashlightMode(activeConfig.flashlightMode)

            if (uiConfig.camera!=activeConfig.camera)
            setSelectedCamera(activeConfig.camera)

            if (uiConfig.model!=activeConfig.model)
            modelInput.setText(activeConfig.model)

            if (uiConfig.name!=activeConfig.name)
            nameInput.setText(activeConfig.name)

            updateButton.setClickable(activeConfig.id>=0)
            deleteButton.setClickable(activeConfig.id>=0)
            }}

        super.onResume()
        }

    fun onSystemPromptInputTextChange(text: String) {
        launch { adapter.mutex.withLock {
            val activeConfig=adapter.activeConfig
            val systemPrompt=systemPromptInput.text.toString()
            if (systemPrompt!=activeConfig.systemPrompt)
            adapter.activeConfig=activeConfig.withSystemPrompt(systemPrompt)
            }}
        }
    fun onUserPromptInputTextChange(text: String) {
        launch { adapter.mutex.withLock {
            val activeConfig=adapter.activeConfig
            val userPrompt=userPromptInput.text.toString()
            if (userPrompt!=activeConfig.userPrompt)
            adapter.activeConfig=activeConfig.withUserPrompt(userPrompt)
            }}
        }

    fun onHighResSwitchCheckedChange(v: View, checked: Boolean) {
        launch { adapter.mutex.withLock {
            val activeConfig=adapter.activeConfig
            if (checked!=activeConfig.highRes)
            adapter.activeConfig=activeConfig.withHighRes(checked)
            }}
        }
    fun onFlashlightModeSpinnerItemSelected(v: View, position: Int) {
        launch { adapter.mutex.withLock {
            val activeConfig=adapter.activeConfig
            val flashlightMode=getSelectedFlashlightMode()
            if (flashlightMode!=activeConfig.flashlightMode)
            adapter.activeConfig=activeConfig.withFlashlightMode(flashlightMode)
            }}
        }

    fun onCameraSpinnerItemSelected(v: View, position: Int) {
        launch { adapter.mutex.withLock {
            val activeConfig=adapter.activeConfig
            val camera=getSelectedCamera()
            if (camera!=activeConfig.camera)
            adapter.activeConfig=activeConfig.withCamera(camera)
            }}
        }
    fun onModelInputTextChange(text: String) {
        launch { adapter.mutex.withLock {
            val activeConfig=adapter.activeConfig
            val model=modelInput.text.toString()

            if (model!=activeConfig.model)
            adapter.activeConfig=activeConfig.withModel(model)
            }}
        }
    fun onSelectModelButtonClick(v: View) {
        startModelSelectionActivity()
        }

    fun onNameInputTextChange(text: String) {
        launch { adapter.mutex.withLock {
            val activeConfig=adapter.activeConfig
            val name=nameInput.text.toString()
            if (name!=activeConfig.name)
            adapter.activeConfig=activeConfig.withName(name)
            }}
        }

    fun onUpdateButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            val originalConfig=adapter.activeConfig
            ConfigManager.getInstance(context!!).updateConfig(adapter.activeConfig)
            toast("Updated config ${originalConfig.name}")
            }}
        }
    fun onCreateButtonClick(v: View) {
        launch { adapter.mutex.withLock {
            val newConfig=ConfigManager.getInstance(context!!).addConfig(adapter.activeConfig)
            adapter.activeConfig=newConfig
            toast("created a new config called ${newConfig.name}")
            }}
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

    fun getUIConfig(adapter: TabAdapter): Config {
        return Config(
            adapter.activeConfig.id,
            nameInput.toString(),
            systemPromptInput.toString(),
            userPromptInput.toString(),
            highResSwitch.isChecked(),
            getSelectedFlashlightMode(),
            getSelectedCamera(),
            modelInput.text.toString(),
            )
        }

    val flashlightModeSpinnerOptions=arrayOf("Default", "On", "Off")
    fun getSelectedFlashlightMode(): FlashlightMode {
        return when (flashlightModeSpinner.selectedItemPosition) {
            0 -> FlashlightMode.DEFAULT
            1 -> FlashlightMode.ON
            2 -> FlashlightMode.OFF
            else -> throw Exception("Unknown flashlight mode ${flashlightModeSpinner.selectedItem}")
            }
        }
    fun setSelectedFlashlightMode(flashlightMode: FlashlightMode) {
        flashlightModeSpinner.setSelection(when (flashlightMode) {
            FlashlightMode.DEFAULT -> 0
            FlashlightMode.ON -> 1
            FlashlightMode.OFF -> 2
            })
        }

    val cameraSpinnerOptions=arrayOf("Back camera", "Front camera")
    fun getSelectedCamera(): UsedCamera {
        return when (cameraSpinner.selectedItemPosition) {
            0 -> UsedCamera.BACK_CAMERA
            1 -> UsedCamera.FRONT_CAMERA
            else -> throw Exception("Unknown camera ${cameraSpinner.selectedItem}")
            }
        }
    fun setSelectedCamera(camera: UsedCamera) {
        cameraSpinner.setSelection(when (camera) {
            UsedCamera.BACK_CAMERA -> 0
            UsedCamera.FRONT_CAMERA -> 1
            })
        }

    private fun startModelSelectionActivity() {
        val intent=ModelSelectionActivityInput(DisplayedModels.READY_TO_USE)
        .toIntent(activity!!)
        modelSelectionActivityLauncher.launch(intent)
        }
    private fun onModelSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==androidx.appcompat.app.AppCompatActivity.RESULT_OK) {
            val output=ModelSelectionActivityOutput.fromIntent(result.data, "SettingsActivity")

            if (!output.model.isEmpty()) {
                val model=output.model

                modelInput.setText(model)
                }
            }
        }
    }
