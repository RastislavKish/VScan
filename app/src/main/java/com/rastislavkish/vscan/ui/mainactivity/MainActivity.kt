/*
* Copyright (C) 2023 Rastislav Kish
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

package com.rastislavkish.vscan.ui.mainactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent

import android.content.Intent
import android.content.SharedPreferences

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.PermissionRequester
import com.rastislavkish.vscan.core.Settings

import com.rastislavkish.vscan.ui.scanactivity.ScanActivity
import com.rastislavkish.vscan.ui.scanactivity.ScanConfig

class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings

    private lateinit var systemPromptInput: EditText;
    private lateinit var userPromptInput: EditText;

    private lateinit var apiKeyInput: EditText
    private lateinit var apiKeyApplyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permissionRequester=PermissionRequester(this)
        if (!permissionRequester.permissionsGranted) {
            permissionRequester.requestPermissions(this)
            }

        settings=Settings.getInstance(this)

        systemPromptInput=findViewById(R.id.systemPromptInput)
        userPromptInput=findViewById(R.id.userPromptInput)

        apiKeyInput=findViewById(R.id.apiKeyInput)
        apiKeyInput.setOnEditorActionListener(this::onApiKeyEditorAction)

        apiKeyApplyButton=findViewById(R.id.apiKeyApplyButton)
        }

    fun startButtonClick(v: View) {
        val permissionRequester=PermissionRequester(this)
        if (!permissionRequester.permissionsGranted) {
            permissionRequester.requestPermissions(this)
            return
            }

        val systemPrompt=systemPromptInput.text.toString()
        var userPrompt=userPromptInput.text.toString()
        val apiKey=settings.apiKey

        if (apiKey=="") {
            return
            }

        if (userPrompt=="") {
            userPrompt="What's in the image?"
            }

        startScan(systemPrompt, userPrompt, apiKey)
        }
    fun apiKeyApplyButtonClick(v: View) {
        val apiKey=apiKeyInput.text.toString()

        if (apiKey=="") {
            toast("Error: OpenAI API key not set. You need dto configure your OpenAI API key. See the project's readme for more details.")
            return
            }

        settings.apiKey=apiKey
        settings.save()

        apiKeyInput.text.clear()

        toast("API key set")
        }

    fun onApiKeyEditorAction(v: View, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId==EditorInfo.IME_ACTION_DONE) {
            apiKeyApplyButton.performClick()
            return true
            }

        return false
        }

    fun startScan(systemPrompt: String, userPrompt: String, apiKey: String) {
        val config=ScanConfig(systemPrompt, userPrompt, apiKey)

        val intent=Intent(this, ScanActivity::class.java)
        intent.putExtra("config", Json.encodeToString(config))

        startActivity(intent)
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
