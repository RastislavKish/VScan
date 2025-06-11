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

package com.rastislavkish.vscan.ui.confirmationactivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent

import android.widget.Button
import android.widget.TextView
import android.view.View

import com.rastislavkish.vscan.R

class ConfirmationActivity : AppCompatActivity() {

    private lateinit var input: ConfirmationActivityInput

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirmation)

        input=ConfirmationActivityInput.fromIntent(intent, "ConfirmationActivity")

        val textLabel: TextView=findViewById(R.id.textLabel)
        textLabel.setText(input.text)
        }

    fun onYesButtonClick(v: View) {
        val result=ConfirmationActivityOutput(true, input.additionalData)
        .toIntent()
        setResult(RESULT_OK, result)

        finish()
        }
    fun onNoButtonClick(v: View) {
        val result=ConfirmationActivityOutput(false, input.additionalData)
        .toIntent()
        setResult(RESULT_OK, result)

        finish()
        }

    }
