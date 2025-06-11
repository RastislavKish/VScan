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

package com.rastislavkish.vscan.ui.modelprovidermappingsactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import android.view.View

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Provider
import com.rastislavkish.vscan.core.TextController

import com.rastislavkish.vscan.ui.modelprovidermappingactivity.ModelProviderMappingActivityInput

class ModelProviderMappingsActivity : AppCompatActivity() {

    private lateinit var mappingsListAdapter: MappingsListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_provider_mappings)

        mappingsListAdapter=MappingsListAdapter(this)
        mappingsListAdapter.setItemClickListener(this::onMappingClick)
        val mappingsList: RecyclerView=findViewById(R.id.mappingsList)
        mappingsList.adapter=mappingsListAdapter

        val searchInput: EditText=findViewById(R.id.searchInput)
        TextController(searchInput).setTextChangeListener(this::onSearchInputTextChange)
        }

    override fun onResume() {
        mappingsListAdapter.refresh()
        super.onResume()
        }
    override fun onPause() {

        super.onPause()
        }

    fun onSearchInputTextChange(text: String) {
        mappingsListAdapter.setFilter(text)
        }
    fun onAddButtonClick(v: View) {
        startModelProviderMappingActivity()
        }
    fun onMappingClick(mapping: Mapping) {
        startModelProviderMappingActivity(mapping)
        }

    fun startModelProviderMappingActivity(mapping: Mapping?=null) {
        val (model, provider)=if (mapping!=null)
        mapping
        else
        Pair(null, null)

        val intent=ModelProviderMappingActivityInput(model, provider?.id)
        .toIntent(this)
        startActivity(intent)
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
