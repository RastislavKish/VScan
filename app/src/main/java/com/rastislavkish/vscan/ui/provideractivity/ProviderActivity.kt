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

package com.rastislavkish.vscan.ui.provideractivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import android.view.View

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Provider
import com.rastislavkish.vscan.core.ProvidersManager
import com.rastislavkish.vscan.core.TextController

import com.rastislavkish.vscan.ui.providerpresetselectionactivity.ProviderPresetSelectionActivity
import com.rastislavkish.vscan.ui.providerpresetselectionactivity.ProviderPresetSelectionActivityOutput

import com.rastislavkish.vscan.ui.modelidactivity.ModelIdActivity
import com.rastislavkish.vscan.ui.modelidactivity.ModelIdActivityInput
import com.rastislavkish.vscan.ui.modelidactivity.ModelIdActivityOutput

import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivity
import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivityInput
import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivityOutput

class ProviderActivity : AppCompatActivity() {

    private val baseUrlRegex=Regex("^https://.+[^/]\$")

    private var inputProvider: Provider?=null

    private lateinit var providersManager: ProvidersManager

    private lateinit var nameInput: EditText
    private lateinit var baseUrlInput: EditText
    private lateinit var apiKeyInput: EditText
    private val models: MutableList<ModelToIdMapping> = mutableListOf()

    private lateinit var modelsListAdapter: ModelsListAdapter

    private lateinit var searchInput: EditText

    private lateinit var providerPresetSelectionActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var modelIdActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var confirmationActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider)

        val input=try {
            ProviderActivityInput.fromIntent(intent, "ProviderActivity")
            }
        catch (e: Exception) {
            ProviderActivityInput()
            }

        providersManager=ProvidersManager.getInstance(this)
        if (input.provider!=null)
        inputProvider=providersManager.getProvider(input.provider)

        val inputProvider=this.inputProvider // To save nullchecks later in this method

        nameInput=findViewById(R.id.nameInput)
        baseUrlInput=findViewById(R.id.baseUrlInput)
        apiKeyInput=findViewById(R.id.apiKeyInput)

        if (inputProvider!=null) {
            nameInput.setText(inputProvider.name)
            baseUrlInput.setText(inputProvider.baseUrl)
            apiKeyInput.setText(inputProvider.apiKey)

            for (entry in inputProvider.models.entries)
            models.add(ModelToIdMapping(entry.key, entry.value))
            }
        else {
            val deleteButton: Button=findViewById(R.id.deleteButton)
            deleteButton.setEnabled(false)
            }

        searchInput=findViewById(R.id.searchInput)
        TextController(searchInput).setTextChangeListener(this::onSearchInputTextChange)

        modelsListAdapter=ModelsListAdapter(models)
        modelsListAdapter.setItemClickListener(this::onModelClick)
        val modelsList: RecyclerView=findViewById(R.id.modelsList)
        modelsList.adapter=modelsListAdapter

        providerPresetSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::providerPresetSelectionActivityResult)
        modelIdActivityLauncher=registerForActivityResult(StartActivityForResult(), this::modelIdActivityResult)
        confirmationActivityLauncher=registerForActivityResult(StartActivityForResult(), this::confirmationActivityResult)
        }

    fun onSelectPresetButtonClick(v: View) {
        startProviderPresetSelectionActivity()
        }

    fun onSearchInputTextChange(text: String) {
        modelsListAdapter.setFilter(text)
        }
    fun onModelClick(mapping: ModelToIdMapping) {
        startModelIdActivity(mapping)
        }

    fun onAddModelButtonClick(v: View) {
        startModelIdActivity()
        }

    fun onSaveButtonClick(v: View) {
        val name=nameInput.text.toString()
        var baseUrl=baseUrlInput.text.toString()
        val apiKey=apiKeyInput.text.toString()
        val models: MutableMap<String, String> = mutableMapOf()

        for (mapping in this.models)
        models.put(mapping.model, mapping.id)

        if (name.isEmpty()) {
            toast("Error: Name must not be empty.")
            return
            }
        if (baseUrl.isEmpty()) {
            toast("Error: Base URL must not be empty.")
            return
            }

        if (baseUrl.endsWith("/"))
        baseUrl=baseUrl.substring(0, baseUrl.length-1)

        if (!baseUrlRegex.matches(baseUrl)) {
            toast("Error: The base URL must be a valid link.")
            return
            }

        if (apiKey.isEmpty()) {
            toast("Error: API key must not be empty.")
            return
            }

        val provider=Provider(-1, name, baseUrl, apiKey, models)

        val inputProvider=this.inputProvider

        if (inputProvider!=null) {
            providersManager.updateProvider(provider.withId(inputProvider.id))
            }
        else {
            providersManager.addProvider(provider)
            }

        finish()
        }
    fun onDeleteButtonClick(v: View) {
        val inputProviderName=inputProvider?.name ?: return

        startConfirmationActivity("Are you sure you want to delete the $inputProviderName provider?")
        }

    fun startProviderPresetSelectionActivity() {
        val intent=Intent(this, ProviderPresetSelectionActivity::class.java)
        providerPresetSelectionActivityLauncher.launch(intent)
        }
    fun providerPresetSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ProviderPresetSelectionActivityOutput.fromIntent(result.data, "ProviderActivity")

            nameInput.setText(output.providerParams.name)
            baseUrlInput.setText(output.providerParams.baseUrl)

            models.clear()
            for (entry in output.providerParams.models.entries)
            models.add(ModelToIdMapping(entry.key, entry.value))

            modelsListAdapter.refresh(models)
            }
        }
    fun startModelIdActivity(mapping: ModelToIdMapping?=null) {
        val mapping=if (mapping!=null)
        com.rastislavkish.vscan.ui.modelidactivity.ModelToIdMapping(mapping.model, mapping.id)
        else
        null

        val intent=ModelIdActivityInput(mapping)
        .toIntent(this)

        modelIdActivityLauncher.launch(intent)
        }
    fun modelIdActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ModelIdActivityOutput.fromIntent(result.data, "ProviderActivity")
            val mapping=ModelToIdMapping(output.mapping.model, output.mapping.id)

            for (i in 0 until models.size) {
                if (models[i].model==mapping.model) {
                    if (!mapping.id.isEmpty())
                    models[i]=mapping
                    else
                    models.removeAt(i)

                    modelsListAdapter.refresh(models)

                    return
                    }
                }

            models.add(mapping)

            modelsListAdapter.refresh(models)
            }
        }
    fun startConfirmationActivity(text: String, additionalData: String="") {
        val intent=ConfirmationActivityInput(text, additionalData)
        .toIntent(this)
        confirmationActivityLauncher.launch(intent)
        }
    fun confirmationActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ConfirmationActivityOutput.fromIntent(result.data, "ProviderActivity")

            if (output.confirmed) {
                providersManager.deleteProvider(inputProvider?.id ?: return)
                finish()
                }
            }
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
