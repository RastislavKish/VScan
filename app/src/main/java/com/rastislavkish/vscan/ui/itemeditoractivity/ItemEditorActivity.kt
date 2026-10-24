/*
* Copyright (C) 2026 Rastislav Kish
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
package com.rastislavkish.vscan.ui.itemeditoractivity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

import com.rastislavkish.vscan.R
import com.rastislavkish.vscan.ui.form.Form

// Edits an item of a config list, e.g. an operation, returning it or its deletion
class ItemEditorActivity : AppCompatActivity() {

    private lateinit var input: ItemEditorActivityInput
    private lateinit var itemForm: ItemForm<*>
    private lateinit var form: Form

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_editor)

        input=ItemEditorActivityInput.fromIntent(intent, "ItemEditorActivity")
        title=input.title

        form=Form(this, findViewById<LinearLayout>(R.id.formContainer))
        form.heading(input.title)
        itemForm=ItemForm.create(input.kind, input.item)
        itemForm.build(form)
        form.refresh()

        // A new item is discarded by Cancel
        val deleteButton: Button=findViewById(R.id.deleteButton)
        if (input.index<0)
        deleteButton.visibility=View.GONE
        }

    fun onOkButtonClick(v: View) {
        val error=form.commit() ?: itemForm.problem()
        if (error!=null) {
            toast(error)
            return
            }

        finish(itemForm.json)
        }
    fun onCancelButtonClick(v: View) {
        finish()
        }
    fun onDeleteButtonClick(v: View) {
        finish(null)
        }

    private fun finish(item: String?) {
        setResult(RESULT_OK, ItemEditorActivityOutput(input.kind, input.index, item).toIntent())
        finish()
        }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
