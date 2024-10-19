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

package com.rastislavkish.vscan.ui.settingsactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.Settings

import com.rastislavkish.vscan.ui.configselectionactivity.ConfigSelectionActivity
import com.rastislavkish.vscan.ui.configselectionactivity.ConfigSelectionActivityOutput

class SettingsActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var settings: Settings

    private lateinit var flashlightSwitch: Switch
    private lateinit var defaultConfigSelector: TextView
    private lateinit var shareConfigSelector: TextView
    private lateinit var fileDescriptionConfigSelector: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var applyKeyButton: Button

    private var lastActivatedConfigSelector: View?=null
    private lateinit var configSelectionActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configManager=ConfigManager.getInstance(this)
        settings=Settings.getInstance(this)

        flashlightSwitch=findViewById(R.id.flashlightSwitch)
        defaultConfigSelector=findViewById(R.id.defaultConfigSelector)
        shareConfigSelector=findViewById(R.id.shareConfigSelector)
        fileDescriptionConfigSelector=findViewById(R.id.fileDescriptionConfigSelector)
        apiKeyInput=findViewById(R.id.apiKeyInput)
        apiKeyInput.setOnEditorActionListener(this::onApiKeyInputEditorAction)
        applyKeyButton=findViewById(R.id.applyKeyButton)

        configSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::configSelectionActivityResult)
        }

    override fun onResume() {
        flashlightSwitch.setChecked(settings.useFlashlight)

        refreshSelectors()

        super.onResume()
        }
    override fun onPause() {
        settings.useFlashlight=flashlightSwitch.isChecked()

        settings.save()

        super.onPause()
        }

    fun onDefaultConfigSelectorClick(v: View) {
        lastActivatedConfigSelector=defaultConfigSelector
        startConfigSelectionActivity()
        }
    fun onShareConfigSelectorClick(v: View) {
        lastActivatedConfigSelector=shareConfigSelector
        startConfigSelectionActivity()
        }
    fun onFileDescriptionConfigSelectorClick(v: View) {
        lastActivatedConfigSelector=fileDescriptionConfigSelector
        startConfigSelectionActivity()
        }

    fun onApiKeyInputEditorAction(v: View, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId==EditorInfo.IME_ACTION_DONE) {
            applyKeyButton.performClick()
            return true
            }

        return false
        }

    fun onApplyKeyButtonClick(v: View) {
        settings.apiKey=apiKeyInput.text.toString()
        settings.save()
        apiKeyInput.text.clear()
        toast("API key applied")
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    private fun refreshSelectors() {
        val defaultConfig=settings.getDefaultConfig(configManager)
        val shareConfig=settings.getShareConfig(configManager)
        val fileDescriptionConfig=settings.getFileDescriptionConfig(configManager)

        defaultConfigSelector.text=defaultConfig.name
        shareConfigSelector.text=shareConfig.name
        fileDescriptionConfigSelector.text=fileDescriptionConfig.name
        }
    private fun startConfigSelectionActivity() {
        val intent=Intent(this, ConfigSelectionActivity::class.java)
        configSelectionActivityLauncher.launch(intent)
        }
    private fun configSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ConfigSelectionActivityOutput.fromIntent(result.data, "SettingsActivity")

            if (!output.configIds.isEmpty()) {
                val id=output.configIds[0]

                when (lastActivatedConfigSelector) {
                    defaultConfigSelector -> {
                        settings.defaultConfigId=id
                        }
                    shareConfigSelector -> {
                        settings.shareConfigId=id
                        }
                    fileDescriptionConfigSelector -> {
                        settings.fileDescriptionConfigId=id
                        }
                    }

                refreshSelectors()
                }
            }
        }
    }
