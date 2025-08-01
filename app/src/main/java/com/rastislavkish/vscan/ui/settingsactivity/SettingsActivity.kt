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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.view.View

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.Settings

import com.rastislavkish.vscan.ui.configselectionactivity.ConfigSelectionActivity
import com.rastislavkish.vscan.ui.configselectionactivity.ConfigSelectionActivityOutput
import com.rastislavkish.vscan.ui.actionselectionactivity.ActionSelectionActivity
import com.rastislavkish.vscan.ui.actionselectionactivity.ActionSelectionActivityOutput
import com.rastislavkish.vscan.ui.providersactivity.ProvidersActivity
import com.rastislavkish.vscan.ui.modelprovidermappingsactivity.ModelProviderMappingsActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var settings: Settings

    private lateinit var flashlightSwitch: Switch
    private lateinit var soundsSwitch: Switch
    private lateinit var describeSavedImagesSwitch: Switch

    private lateinit var defaultConfigSelector: TextView
    private lateinit var shareConfigSelector: TextView
    private lateinit var fileDescriptionConfigSelector: TextView

    private lateinit var shakeActionSelector: TextView
    private lateinit var volumeUpPressActionSelector: TextView
    private lateinit var volumeDownPressActionSelector: TextView

    private var lastActivatedConfigSelector: View?=null
    private var lastActivatedActionSelector: View?=null
    private lateinit var configSelectionActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var actionSelectionActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configManager=ConfigManager.getInstance(this)
        settings=Settings.getInstance(this)

        flashlightSwitch=findViewById(R.id.flashlightSwitch)
        soundsSwitch=findViewById(R.id.soundsSwitch)
        describeSavedImagesSwitch=findViewById(R.id.describeSavedImagesSwitch)

        defaultConfigSelector=findViewById(R.id.defaultConfigSelector)
        shareConfigSelector=findViewById(R.id.shareConfigSelector)
        fileDescriptionConfigSelector=findViewById(R.id.fileDescriptionConfigSelector)

        shakeActionSelector=findViewById(R.id.shakeActionSelector)
        volumeUpPressActionSelector=findViewById(R.id.volumeUpPressActionSelector)
        volumeDownPressActionSelector=findViewById(R.id.volumeDownPressActionSelector)

        configSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::configSelectionActivityResult)
        actionSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::actionSelectionActivityResult)
        }

    override fun onResume() {
        flashlightSwitch.setChecked(settings.useFlashlight)
        soundsSwitch.setChecked(settings.useSounds)
        describeSavedImagesSwitch.setChecked(settings.describeSavedImages)

        refreshSelectors()

        super.onResume()
        }
    override fun onPause() {
        settings.useFlashlight=flashlightSwitch.isChecked()
        settings.useSounds=soundsSwitch.isChecked()
        settings.describeSavedImages=describeSavedImagesSwitch.isChecked()

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

    fun onShakeActionSelectorClick(v: View) {
        lastActivatedActionSelector=shakeActionSelector
        startActionSelectionActivity()
        }
    fun onVolumeUpPressActionSelectorClick(v: View) {
        lastActivatedActionSelector=volumeUpPressActionSelector
        startActionSelectionActivity()
        }
    fun onVolumeDownPressActionSelectorClick(v: View) {
        lastActivatedActionSelector=volumeDownPressActionSelector
        startActionSelectionActivity()
        }

    fun onApiProvidersLabelClick(v: View) {
        startProvidersActivity()
        }
    fun onModelProviderMappingsLabelClick(v: View) {
        startModelProviderMappingsActivity()
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

        shakeActionSelector.text=settings.shakeAction?.toString(configManager) ?: "None"
        volumeUpPressActionSelector.text=settings.volumeUpPressAction?.toString(configManager) ?: "None"
        volumeDownPressActionSelector.text=settings.volumeDownPressAction?.toString(configManager) ?: "None"
        }
    private fun startConfigSelectionActivity() {
        val intent=Intent(this, ConfigSelectionActivity::class.java)
        configSelectionActivityLauncher.launch(intent)
        }
    private fun startActionSelectionActivity() {
        val intent=Intent(this, ActionSelectionActivity::class.java)
        actionSelectionActivityLauncher.launch(intent)
        }
    private fun startProvidersActivity() {
        val intent=Intent(this, ProvidersActivity::class.java)
        startActivity(intent)
        }
    private fun startModelProviderMappingsActivity() {
        val intent=Intent(this, ModelProviderMappingsActivity::class.java)
        startActivity(intent)
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
    private fun actionSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ActionSelectionActivityOutput.fromIntent(result.data, "SettingsActivity")

            val action=output.action

            when (lastActivatedActionSelector) {
                shakeActionSelector -> {
                    settings.shakeAction=action
                    }
                volumeUpPressActionSelector -> {
                    settings.volumeUpPressAction=action
                    }
                volumeDownPressActionSelector -> {
                    settings.volumeDownPressAction=action
                    }
                }

            refreshSelectors()
            }
        }
    }
