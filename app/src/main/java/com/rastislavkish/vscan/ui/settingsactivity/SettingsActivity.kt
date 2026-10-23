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
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope

import android.net.Uri

import kotlinx.coroutines.*

import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.view.View

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.files.DocumentFileTree
import com.rastislavkish.vscan.core.files.FileSync
import com.rastislavkish.vscan.core.files.VScanFiles

import com.rastislavkish.vscan.ui.configselectionactivity.ConfigSelectionActivity
import com.rastislavkish.vscan.ui.configselectionactivity.ConfigSelectionActivityOutput
import com.rastislavkish.vscan.ui.actionselectionactivity.ActionSelectionActivity
import com.rastislavkish.vscan.ui.actionselectionactivity.ActionSelectionActivityOutput
import com.rastislavkish.vscan.ui.providersactivity.ProvidersActivity
import com.rastislavkish.vscan.ui.modelprovidermappingsactivity.ModelProviderMappingsActivity
import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivityInput
import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivityOutput

class SettingsActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var settings: Settings

    private lateinit var flashlightSwitch: Switch
    private lateinit var frontCameraSwitch: Switch
    private lateinit var soundsSwitch: Switch
    private lateinit var describeSavedImagesSwitch: Switch
    private lateinit var reportEveryScanSwitch: Switch
    private lateinit var speechUsesAccessibilityVolumeSwitch: Switch
    private lateinit var stopSpeechOnProximityWaveSwitch: Switch

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

    private lateinit var linkedFolderLabel: TextView
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>
    private lateinit var linkConfirmationLauncher: ActivityResultLauncher<Intent>
    // The picked folder waiting for the confirmation to use its files
    private var pickedFolder: Uri?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configManager=ConfigManager.getInstance(this)
        settings=Settings.getInstance(this)

        flashlightSwitch=findViewById(R.id.flashlightSwitch)
        frontCameraSwitch=findViewById(R.id.frontCameraSwitch)
        soundsSwitch=findViewById(R.id.soundsSwitch)
        describeSavedImagesSwitch=findViewById(R.id.describeSavedImagesSwitch)
        reportEveryScanSwitch=findViewById(R.id.reportEveryScanSwitch)
        speechUsesAccessibilityVolumeSwitch=findViewById(R.id.speechUsesAccessibilityVolumeSwitch)
        stopSpeechOnProximityWaveSwitch=findViewById(R.id.stopSpeechOnProximityWaveSwitch)

        defaultConfigSelector=findViewById(R.id.defaultConfigSelector)
        shareConfigSelector=findViewById(R.id.shareConfigSelector)
        fileDescriptionConfigSelector=findViewById(R.id.fileDescriptionConfigSelector)

        shakeActionSelector=findViewById(R.id.shakeActionSelector)
        volumeUpPressActionSelector=findViewById(R.id.volumeUpPressActionSelector)
        volumeDownPressActionSelector=findViewById(R.id.volumeDownPressActionSelector)

        configSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::configSelectionActivityResult)
        actionSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::actionSelectionActivityResult)

        linkedFolderLabel=findViewById(R.id.linkedFolderLabel)
        folderPickerLauncher=registerForActivityResult(OpenDocumentTree(), this::onFolderPicked)
        linkConfirmationLauncher=registerForActivityResult(StartActivityForResult(), this::linkConfirmationResult)
        }

    override fun onResume() {
        refreshSwitches()
        refreshSelectors()
        refreshLinkedFolder()

        super.onResume()
        }
    override fun onPause() {
        saveSwitches()

        super.onPause()
        }

    private fun refreshSwitches() {
        flashlightSwitch.setChecked(settings.useFlashlight)
        frontCameraSwitch.setChecked(settings.useFrontCamera)
        soundsSwitch.setChecked(settings.useSounds)
        describeSavedImagesSwitch.setChecked(settings.describeSavedImages)
        reportEveryScanSwitch.setChecked(settings.reportEveryScan)
        speechUsesAccessibilityVolumeSwitch.setChecked(settings.speechUsesAccessibilityVolume)
        stopSpeechOnProximityWaveSwitch.setChecked(settings.stopSpeechOnProximityWave)
        }
    private fun saveSwitches() {
        settings.useFlashlight=flashlightSwitch.isChecked()
        settings.useFrontCamera=frontCameraSwitch.isChecked()
        settings.useSounds=soundsSwitch.isChecked()
        settings.describeSavedImages=describeSavedImagesSwitch.isChecked()
        settings.reportEveryScan=reportEveryScanSwitch.isChecked()
        settings.speechUsesAccessibilityVolume=speechUsesAccessibilityVolumeSwitch.isChecked()
        settings.stopSpeechOnProximityWave=stopSpeechOnProximityWaveSwitch.isChecked()

        settings.save()
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

    fun onLinkFolderClick(v: View) {
        folderPickerLauncher.launch(null)
        }
    fun onUnlinkFolderClick(v: View) {
        if (VScanFiles.linkedFolderUri(this)==null) {
            toast("No folder is linked")
            return
            }

        VScanFiles.unlinkFolder(this)
        refreshLinkedFolder()
        toast("Unlinked, the files stay on this device")
        }

    private fun onFolderPicked(uri: Uri?) {
        if (uri==null)
        return

        lifecycleScope.launch {
            val hasFiles=try {
                withContext(Dispatchers.IO) {
                    VScanFiles.getInstance(this@SettingsActivity).hasVScanFiles(DocumentFileTree(this@SettingsActivity, uri))
                    }
                }
            catch (e: Exception) {
                toast("Can't use the folder. ${e.message}")
                return@launch
                }

            if (hasFiles) {
                pickedFolder=uri
                linkConfirmationLauncher.launch(ConfirmationActivityInput("The folder has VScan files already. Use them, replacing the configs, providers and settings on this device?").toIntent(this@SettingsActivity))
                }
            else
            link(uri, false)
            }
        }
    private fun linkConfirmationResult(result: ActivityResult) {
        val uri=pickedFolder ?: return
        pickedFolder=null

        if (result.resultCode==RESULT_OK && ConfirmationActivityOutput.fromIntent(result.data, "SettingsActivity").confirmed)
        link(uri, true)
        }
    // useItsFiles: the folder's files replace this device's, otherwise this device's files are copied there
    private fun link(uri: Uri, useItsFiles: Boolean) {
        // What's shown here is saved first, so the device's settings are complete when copied, or the folder's replace them afterwards
        saveSwitches()

        lifecycleScope.launch {
            val changed=try {
                withContext(Dispatchers.IO) { VScanFiles.linkFolder(this@SettingsActivity, uri, useItsFiles) }
                }
            catch (e: Exception) {
                toast("Can't use the folder. ${e.message}")
                return@launch
                }

            FileSync.apply(this@SettingsActivity, changed)
            refreshSwitches()
            refreshSelectors()
            refreshLinkedFolder()
            toast(if (useItsFiles) "Linked, using the folder's files" else "Linked, the files were copied to the folder")
            }
        }
    private fun refreshLinkedFolder() {
        val uri=VScanFiles.linkedFolderUri(this)
        linkedFolderLabel.text=if (uri==null)
        "No linked folder, the configs and settings are only on this device"
        else
        "Linked folder: ${DocumentFile.fromTreeUri(this, uri)?.name ?: uri.lastPathSegment}"
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
