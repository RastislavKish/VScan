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

package com.rastislavkish.vscan.ui.modelidactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.view.View

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivity
import com.rastislavkish.vscan.ui.modelselectionactivity.DisplayedModels
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivityInput
import com.rastislavkish.vscan.ui.modelselectionactivity.ModelSelectionActivityOutput

import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivity
import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivityInput
import com.rastislavkish.vscan.ui.confirmationactivity.ConfirmationActivityOutput

class ModelIdActivity : AppCompatActivity() {

    private lateinit var input: ModelIdActivityInput

    private lateinit var modelInput: EditText
    private lateinit var modelIdInput: EditText

    private lateinit var modelSelectionActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var confirmationActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_id)

        val input=try {
            ModelIdActivityInput.fromIntent(intent, "ModelIdActivity")
            }
        catch (e: Exception) {
            ModelIdActivityInput()
            }

        modelInput=findViewById(R.id.modelInput)
        modelIdInput=findViewById(R.id.modelIdInput)

        val mapping=input.mapping
        if (mapping!=null) {
            modelInput.setText(mapping.model)
            modelIdInput.setText(mapping.id)
            }
        else {
            val deleteButton: Button=findViewById(R.id.deleteButton)
            deleteButton.setEnabled(false)
            }

        modelSelectionActivityLauncher=registerForActivityResult(StartActivityForResult(), this::modelSelectionActivityResult)
        confirmationActivityLauncher=registerForActivityResult(StartActivityForResult(), this::confirmationActivityResult)
        }

    fun onSelectModelClick(v: View) {
        startModelSelectionActivity()
        }

    fun onSaveButtonClick(v: View) {
        val model=modelInput.text.toString()
        val modelId=modelIdInput.text.toString()

        if (model.isEmpty()) {
            toast("Error: Model must not be empty.")
            return
            }
        if (modelId.isEmpty()) {
            toast("Error: Model ID must not be empty.")
            return
            }

        val result=ModelIdActivityOutput(ModelToIdMapping(model, modelId))
        .toIntent()

        setResult(RESULT_OK, result)

        finish()
        }
    fun onDeleteButtonClick(v: View) {
        val model = input.mapping?.model ?: return

        startConfirmationActivity("Are you sure you want to delete the $model model id definition for this provider?")
        }

    fun startModelSelectionActivity() {
        val intent=ModelSelectionActivityInput(DisplayedModels.ALL)
        .toIntent(this)
        modelSelectionActivityLauncher.launch(intent)
        }
    fun modelSelectionActivityResult(result: ActivityResult) {
        if (result.resultCode==RESULT_OK) {
            val output=ModelSelectionActivityOutput.fromIntent(result.data, "ModelIdActivity")

            modelInput.setText(output.model)

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
                val model=input.mapping?.model ?: return

                val result=ModelIdActivityOutput(ModelToIdMapping(model, ""))
                .toIntent()

                setResult(RESULT_OK, result)

                finish()
                }
            }
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
