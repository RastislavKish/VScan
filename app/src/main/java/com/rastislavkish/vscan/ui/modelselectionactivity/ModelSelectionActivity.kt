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

package com.rastislavkish.vscan.ui.modelselectionactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import android.view.View

import com.rastislavkish.vscan.R

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import com.rastislavkish.vscan.core.TextController
import com.rastislavkish.vscan.core.Model

class ModelSelectionActivity : AppCompatActivity() {

    private var displayedModels: DisplayedModels=DisplayedModels.ALL

    private lateinit var modelListAdapter: ModelListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_selection)

        val input=try {
            ModelSelectionActivityInput.fromIntent(intent, "ModelSelectionActivity")
            }
        catch (e: Exception) {
            ModelSelectionActivityInput()
            }

        displayedModels=input.displayedModels

        modelListAdapter=ModelListAdapter(this)
        modelListAdapter.setDisplayedModels(displayedModels)
        modelListAdapter.setItemClickListener(this::modelClick)
        val modelList: RecyclerView=findViewById(R.id.modelList)
        modelList.adapter=modelListAdapter

        val searchInput: EditText=findViewById(R.id.searchInput)
        TextController(searchInput).setTextChangeListener(this::searchInputTextChange)
        }

    override fun onResume() {
        modelListAdapter.refresh()
        super.onResume()
        }
    override fun onPause() {

        super.onPause()
        }

    fun modelClick(model: Model) {
        val result=ModelSelectionActivityOutput(model.identifier)

        val resultIntent=Intent()
        resultIntent.putExtra("result", Json.encodeToString(result))
        setResult(RESULT_OK, resultIntent)

        finish()
        }
    fun searchInputTextChange(text: String) {
        modelListAdapter.setFilter(text)
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
