/*
* Copyright (C) 2025 Rastislav Kish
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

package com.rastislavkish.vscan.ui.actionselectionactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.content.Intent

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

import android.widget.TextView
import android.widget.Toast
import android.view.View

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.ConfigManager
import com.rastislavkish.vscan.core.Action
import com.rastislavkish.vscan.core.ScanWithActiveConfigAction
import com.rastislavkish.vscan.core.ScanWithConfigAction
import com.rastislavkish.vscan.core.ConsultConfigAction
import com.rastislavkish.vscan.core.AskAction
import com.rastislavkish.vscan.core.SetSystemPromptAction
import com.rastislavkish.vscan.core.SetUserPromptAction
import com.rastislavkish.vscan.core.StopSpeechAction
import com.rastislavkish.vscan.core.RunCommandAction

import com.rastislavkish.vscan.ui.configselectionactivity.ConfigSelectionActivity
import com.rastislavkish.vscan.ui.configselectionactivity.ConfigSelectionActivityOutput
import com.rastislavkish.vscan.ui.textinputactivity.TextInputActivityInput
import com.rastislavkish.vscan.ui.textinputactivity.TextInputActivityOutput

class ActionSelectionActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager

    private var scanWithConfigConfig: String=ConfigManager.BASE_CONFIG_ID

    private var consultConfigConfig: String=ConfigManager.BASE_CONFIG_ID

    private lateinit var scanWithConfigConfigSelector: TextView
    private lateinit var consultConfigConfigSelector: TextView

    private var runCommandCommand=""
    private lateinit var runCommandCommandSelector: TextView

    private var lastActivatedConfigSelector: View?=null

    private lateinit var configSelectionActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var textInputActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_selection)

        configManager=ConfigManager.getInstance(this)

        scanWithConfigConfigSelector=findViewById(R.id.scanWithConfigConfigSelector)
        consultConfigConfigSelector=findViewById(R.id.consultConfigConfigSelector)
        runCommandCommandSelector=findViewById(R.id.runCommandCommandSelector)

        refreshSelectors()

        configSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::configSelectionActivityResult)
        textInputActivityLauncher=registerForActivityResult(StartActivityForResult(), this::textInputActivityResult)
        }

    fun onScanWithActiveConfigClick(v: View) {
        returnResult(ScanWithActiveConfigAction())
        }

    fun onScanWithConfigClick(v: View) {
        returnResult(ScanWithConfigAction(scanWithConfigConfig))
        }
    fun onScanWithConfigConfigSelectorClick(v: View) {
        lastActivatedConfigSelector=scanWithConfigConfigSelector
        startConfigSelectionActivity()
        }

    fun onConsultConfigClick(v: View) {
        returnResult(ConsultConfigAction(consultConfigConfig))
        }
    fun onConsultConfigConfigSelectorClick(v: View) {
        lastActivatedConfigSelector=consultConfigConfigSelector
        startConfigSelectionActivity()
        }

    fun onRunCommandClick(v: View) {
        if (runCommandCommand.isBlank()) {
            toast("Set the command first")
            return
            }

        returnResult(RunCommandAction(runCommandCommand))
        }
    fun onRunCommandCommandSelectorClick(v: View) {
        val intent=TextInputActivityInput("Command", runCommandCommand)
        .toIntent(this)
        textInputActivityLauncher.launch(intent)
        }

    fun onAskClick(v: View) {
        returnResult(AskAction())
        }

    fun onSetSystemPromptClick(v: View) {
        returnResult(SetSystemPromptAction())
        }

    fun onSetUserPromptClick(v: View) {
        returnResult(SetUserPromptAction())
        }

    fun onStopSpeechClick(v: View) {
        returnResult(StopSpeechAction())
        }

    fun onNoneClick(v: View) {
        returnResult(null)
        }

    fun returnResult(action: Action?) {
        val result=ActionSelectionActivityOutput(action)

        val resultIntent=Intent()
        resultIntent.putExtra("result", Json.encodeToString(result))
        setResult(RESULT_OK, resultIntent)

        finish()
        }

    private fun refreshSelectors() {
        scanWithConfigConfigSelector.text=getConfigName(scanWithConfigConfig)
        consultConfigConfigSelector.text=getConfigName(consultConfigConfig)
        runCommandCommandSelector.text=if (runCommandCommand.isBlank()) "No command" else runCommandCommand
        }
    private fun getConfigName(id: String): String {
        return configManager.getConfig(id)?.name
        ?: configManager.getBaseConfig().name
        }

    private fun startConfigSelectionActivity() {
        val intent=Intent(this, ConfigSelectionActivity::class.java)
        configSelectionActivityLauncher.launch(intent)
        }
    private fun configSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ConfigSelectionActivityOutput.fromIntent(result.data, "ActionSelectionActivity")

            if (!output.configIds.isEmpty()) {
                val id=output.configIds[0]

                when (lastActivatedConfigSelector) {
                    scanWithConfigConfigSelector -> scanWithConfigConfig=id
                    consultConfigConfigSelector -> consultConfigConfig=id
                    }

                refreshSelectors()
                }
            }
        }

    private fun textInputActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=TextInputActivityOutput.fromIntent(result.data, "ActionSelectionActivity")
            runCommandCommand=output.text.trim()
            refreshSelectors()
            }
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
