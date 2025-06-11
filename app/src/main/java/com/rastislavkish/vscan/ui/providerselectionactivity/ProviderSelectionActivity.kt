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

package com.rastislavkish.vscan.ui.providerselectionactivity

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

import com.rastislavkish.vscan.core.Provider
import com.rastislavkish.vscan.core.TextController

class ProviderSelectionActivity : AppCompatActivity() {

    private lateinit var providerListAdapter: ProviderListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_selection)

        providerListAdapter=ProviderListAdapter(this)
        providerListAdapter.setItemClickListener(this::providerClick)
        val providerList: RecyclerView=findViewById(R.id.providerList)
        providerList.adapter=providerListAdapter

        val searchInput: EditText=findViewById(R.id.searchInput)
        TextController(searchInput).setTextChangeListener(this::searchInputTextChange)
        }

    override fun onResume() {
        providerListAdapter.refresh()
        super.onResume()
        }
    override fun onPause() {

        super.onPause()
        }

    fun providerClick(provider: Provider) {
        val result=ProviderSelectionActivityOutput(provider.id)

        val resultIntent=Intent()
        resultIntent.putExtra("result", Json.encodeToString(result))
        setResult(RESULT_OK, resultIntent)

        finish()
        }
    fun searchInputTextChange(text: String) {
        providerListAdapter.setFilter(text)
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
