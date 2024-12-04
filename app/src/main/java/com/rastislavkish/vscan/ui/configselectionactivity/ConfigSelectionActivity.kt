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

package com.rastislavkish.vscan.ui.configselectionactivity

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
import com.rastislavkish.vscan.core.Config

class ConfigSelectionActivity : AppCompatActivity() {

    private lateinit var configListAdapter: ConfigListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_selection)

        configListAdapter=ConfigListAdapter(this)
        configListAdapter.setItemClickListener(this::configClick)
        val configList: RecyclerView=findViewById(R.id.configList)
        configList.adapter=configListAdapter

        val searchInput: EditText=findViewById(R.id.searchInput)
        TextController(searchInput).setTextChangeListener(this::searchInputTextChange)
        }

    override fun onResume() {
        configListAdapter.refresh()
        super.onResume()
        }
    override fun onPause() {

        super.onPause()
        }

    fun configClick(config: Config) {
        val result=ConfigSelectionActivityOutput(listOf(config.id))

        val resultIntent=Intent()
        resultIntent.putExtra("result", Json.encodeToString(result))
        setResult(RESULT_OK, resultIntent)

        finish()
        }
    fun searchInputTextChange(text: String) {
        configListAdapter.setFilter(text)
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
