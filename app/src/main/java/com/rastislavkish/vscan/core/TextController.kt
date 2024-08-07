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

package com.rastislavkish.vscan.core

import android.text.Editable
import android.text.TextWatcher

import android.widget.EditText

class TextController(val source: EditText): TextWatcher {

    private var textChangeListener: ((String) -> Unit)?=null

    init {
        source.addTextChangedListener(this)
        }

    fun setTextChangeListener(listener: ((String) -> Unit)?) {
        textChangeListener=listener
        }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        textChangeListener?.invoke(s.toString())
        }
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    override fun afterTextChanged(s: Editable) {}
    }
