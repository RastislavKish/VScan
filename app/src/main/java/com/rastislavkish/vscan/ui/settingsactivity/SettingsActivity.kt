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

import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent

import com.rastislavkish.vscan.R

import com.rastislavkish.vscan.core.Settings

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: Settings

    private lateinit var flashlightSwitch: Switch
    private lateinit var apiKeyInput: EditText
    private lateinit var applyKeyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings=Settings.getInstance(this)

        flashlightSwitch=findViewById(R.id.flashlightSwitch)
        apiKeyInput=findViewById(R.id.apiKeyInput)
        apiKeyInput.setOnEditorActionListener(this::onApiKeyInputEditorAction)
        applyKeyButton=findViewById(R.id.applyKeyButton)

        }

    override fun onResume() {
        flashlightSwitch.setChecked(settings.useFlashlight)

        super.onResume()
        }
    override fun onPause() {
        settings.useFlashlight=flashlightSwitch.isChecked()

        settings.save()

        super.onPause()
        }

    fun onApiKeyInputEditorAction(v: View, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId==EditorInfo.IME_ACTION_DONE) {
            applyKeyButton.performClick()
            return true
            }

        return false
        }

    fun onApplyKeyButtonClick(v: View) {
        settings.apiKey=apiKeyInput.text.toString()
        settings.save()
        apiKeyInput.text.clear()
        toast("API key applied")
        }

    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
