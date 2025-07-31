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

package com.rastislavkish.vscan.ui.modelprovidermappingactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.View

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Provider
import com.rastislavkish.vscan.core.ProvidersManager

import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivity
import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivityInput
import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivityOutput

import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivity
import com.rastislavkish.vscan.ui.modelselectionactivity.DisplayedModels
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivityInput
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivityOutput

import com.rastislavkish.vscan.ui.providerselectionactivity.ProviderSelectionActivity
import com.rastislavkish.vscan.ui.providerselectionactivity.ProviderSelectionActivityOutput

class ModelProviderMappingActivity : AppCompatActivity() {

    private var inputModel: String?=null
    private var inputProvider: Provider?=null

    private var provider: Provider?=null

    private lateinit var providersManager: ProvidersManager

    private lateinit var modelInput: EditText
    private lateinit var providerSelector: TextView

    private lateinit var modelSelectionActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var providerSelectionActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_provider_mapping)

        val input=try {
            ModelProviderMappingActivityInput.fromIntent(intent, "ModelProviderMappingActivity")
            }
        catch (e: Exception) {
            ModelProviderMappingActivityInput()
            }

        inputModel=input.model

        providersManager=ProvidersManager.getInstance(this)
        if (input.provider!=null)
        inputProvider=providersManager.getProvider(input.provider)

        provider=inputProvider

        // To save nullchecks later in this method
        val inputModel=this.inputModel
        val inputProvider=this.inputProvider

        modelInput=findViewById(R.id.modelInput)
        providerSelector=findViewById(R.id.providerSelector)
        providerSelector.setOnClickListener(this::onProviderSelectorClick)

        if (inputModel!=null)
        modelInput.setText(inputModel)

        if (inputProvider==null) {
            val deleteButton: Button=findViewById(R.id.deleteButton)
            deleteButton.setEnabled(false)
            }

        refreshProviderSelector()

        modelSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::modelSelectionActivityResult)
        providerSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::providerSelectionActivityResult)
        }

    fun onSelectModelButtonClick(v: View) {
        startModelSelectionActivity()
        }
    fun onProviderSelectorClick(v: View) {
        startProviderSelectionActivity()
        }
    fun onResetProviderButtonClick(v: View) {
        provider=null
        refreshProviderSelector()
        }

    fun onSaveButtonClick(v: View) {
        val inputModel=this.inputModel
        val model=modelInput.text.toString()
        val provider=provider

        if (inputModel!=null) {
            if (model!=inputModel)
            providersManager.deleteModelProviderMapping(model)
            }

        providersManager.mapModelToProvider(model, provider?.id)

        finish()
        }
    fun onDeleteButtonClick(v: View) {
        val inputModel=this.inputModel ?: return
        providersManager.deleteModelProviderMapping(inputModel)
        finish()
        }

    fun refreshProviderSelector() {
        val providerName=provider?.name ?: "None"
        providerSelector.text="Provider: $providerName"
        }

    fun startModelSelectionActivity() {
        val intent=ModelSelectionActivityInput(DisplayedModels.SUPPORTED_BY_PROVIDERS)
        .toIntent(this)
        modelSelectionActivityLauncher.launch(intent)
        }
    fun modelSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ModelSelectionActivityOutput.fromIntent(result.data, "ModelProviderMappingActivity")

            modelInput.setText(output.model)
            }
        }
    fun startProviderSelectionActivity() {
        val intent=Intent(this, ProviderSelectionActivity::class.java)
        providerSelectionActivityLauncher.launch(intent)
        }
    fun providerSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ProviderSelectionActivityOutput.fromIntent(result.data, "ModelProviderMappingActivity")

            provider=providersManager.getProvider(output.provider) ?: return

            refreshProviderSelector()
            }
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
