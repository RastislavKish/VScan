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

package com.rastislavkish.vscan.ui.textinputactivity

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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent

import com.rastislavkish.vscan.R

class TextInputActivity : AppCompatActivity() {

    private lateinit var input: TextInputActivityInput

    private lateinit var titleLabel: TextView
    private lateinit var textInput: EditText
    private lateinit var okButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_input)

        input=try {
            TextInputActivityInput.fromIntent(intent, "TextInputActivity")
            }
        catch (e: Exception) {
            TextInputActivityInput("Text")
            }

        titleLabel=findViewById(R.id.titleLabel)
        titleLabel.setText(input.title)
        textInput=findViewById(R.id.textInput)
        textInput.setText(input.text)
        textInput.setOnEditorActionListener(this::onTextInputEditorAction)

        okButton=findViewById(R.id.okButton)

        displayKeyboard()
        }

    fun onOkButtonClick(v: View) {
        val text=textInput.text.toString()
        val context=input.context

        val result=TextInputActivityOutput(text, context)
        .toIntent()

        setResult(RESULT_OK, result)

        finish()
        }
    fun onCancelButtonClick(v: View) {
        finish()
        }

    fun onTextInputEditorAction(v: View, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId==EditorInfo.IME_ACTION_DONE) {
            okButton.performClick()

            return true
            }

        return false
        }

    fun displayKeyboard() {
        textInput.requestFocus()

        val imm=getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(textInput, InputMethodManager.SHOW_IMPLICIT)
        }
    fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
