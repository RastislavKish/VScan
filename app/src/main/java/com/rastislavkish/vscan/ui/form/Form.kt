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
package com.rastislavkish.vscan.ui.form

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// An editor built in code: headings, fields bound to the edited value, and links to further screens
// The fields show the value through their getters on refresh(), and change it through their setters as the user edits them, refreshing the form afterwards
// A setter may throw IllegalArgumentException for a value it doesn't take, which its field shows as its error, keeping the text for the user to fix
class Form(
    val context: Context,
    val container: LinearLayout,
    ) {

    private val refreshers=mutableListOf<() -> Unit>()
    // The fields whose texts weren't taken, with their retries
    private val failed=linkedMapOf<TextInputLayout, () -> Boolean>()
    // While refreshing, the views don't report the values they're given
    private var refreshing=false

    fun heading(text: String): TextView {
        val view=TextView(context)
        view.text=text
        view.isAccessibilityHeading=true
        view.setTextAppearance(android.R.style.TextAppearance_Medium)
        add(view)
        return view
        }

    // A text changing with the edited value, hidden while empty
    fun label(text: () -> String?): TextView {
        val view=TextView(context)
        add(view)
        refreshers.add {
            val current=text()
            view.text=current ?: ""
            view.visibility=if (current.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        return view
        }

    // A text opening something on click, e.g. "System prompt: ..."
    fun link(text: () -> String, onClick: () -> Unit): TextView {
        val view=TextView(context)
        view.setOnClickListener { onClick() }
        add(view)
        refreshers.add { view.text=text() }
        return view
        }

    fun button(text: String, onClick: () -> Unit): Button {
        val view=Button(context)
        view.text=text
        view.setOnClickListener { onClick() }
        add(view, ViewGroup.LayoutParams.WRAP_CONTENT)
        return view
        }

    fun switch(label: String, get: () -> Boolean, set: (Boolean) -> Unit): Switch {
        val view=Switch(context)
        view.text=label
        view.isSaveEnabled=false
        view.setOnCheckedChangeListener { _, checked ->
            if (!refreshing && checked!=get()) {
                set(checked)
                refresh()
                }
            }
        add(view)
        refreshers.add {
            if (view.isChecked!=get())
            view.isChecked=get()
            }
        return view
        }

    fun <T> choice(label: String, options: List<Pair<String, T>>, get: () -> T, set: (T) -> Unit): View {
        val row=LinearLayout(context)
        row.orientation=LinearLayout.HORIZONTAL

        val labelView=TextView(context)
        labelView.text=label
        val spinner=Spinner(context, Spinner.MODE_DROPDOWN)
        spinner.isSaveEnabled=false

        val adapter=ArrayAdapter(context, android.R.layout.simple_spinner_item, options.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter=adapter
        spinner.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                val value=options[position].second
                if (!refreshing && value!=get()) {
                    set(value)
                    refresh()
                    }
                }
            override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        row.addView(labelView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f))
        row.addView(spinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(row)

        refreshers.add {
            val position=options.indexOfFirst { it.second==get() }
            if (position>=0 && position!=spinner.selectedItemPosition)
            spinner.setSelection(position)
            }
        return row
        }

    // A text field showing the value formatted, and parsing what the user types, IllegalArgumentException meaning it isn't a valid value
    fun <T> field(
        label: String,
        format: (T) -> String,
        parse: (String) -> T,
        get: () -> T,
        set: (T) -> Unit,
        inputType: Int=InputType.TYPE_CLASS_TEXT,
        ): TextInputLayout {
        val layout=TextInputLayout(context)
        val input=TextInputEditText(layout.context)
        input.isSaveEnabled=false
        input.inputType=inputType
        layout.addView(input)
        layout.prefixText=label
        layout.placeholderText=label

        // Whether the text was taken
        fun apply(): Boolean {
            try {
                val value=parse(input.text.toString())
                if (value!=get())
                set(value)
                layout.error=null
                failed.remove(layout)
                return true
                }
            catch (e: IllegalArgumentException) {
                layout.error=e.message ?: "Invalid value"
                failed[layout]=::apply
                return false
                }
            }

        input.addTextChangedListener(object: TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!refreshing && apply())
                refresh()
                }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) {}
            })
        add(layout)

        refreshers.add {
            // A text being fixed stays, as does one already meaning the value, e.g. 2.0 for 2
            if (layout !in failed) {
                val shown=try { parse(input.text.toString()) } catch (e: IllegalArgumentException) { null }
                if (shown!=get())
                input.setText(format(get()))
                }
            }
        return layout
        }

    fun text(label: String, get: () -> String, set: (String) -> Unit, multiLine: Boolean=false): TextInputLayout {
        return field(label, { it }, { it }, get, set, textInputType(multiLine))
        }
    // An empty text meaning null
    fun optionalText(label: String, get: () -> String?, set: (String?) -> Unit, multiLine: Boolean=false): TextInputLayout {
        return field(label, { it ?: "" }, { it.ifEmpty { null } }, get, set, textInputType(multiLine))
        }
    fun int(label: String, get: () -> Int, set: (Int) -> Unit): TextInputLayout {
        return field(label, { it.toString() }, Values::parseInt, get, set, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
        }
    fun double(label: String, get: () -> Double, set: (Double) -> Unit): TextInputLayout {
        return field(label, Values::formatNumber, Values::parseDouble, get, set, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        }

    // The views shown only while the condition holds
    fun visibleWhen(condition: () -> Boolean, vararg views: View) {
        refreshers.add {
            val visibility=if (condition()) View.VISIBLE else View.GONE
            for (view in views)
            view.visibility=visibility
            }
        }

    // An item per line, opened on click, with Move up / Move down accessibility actions when the order can change, and Delete when given
    fun <T> list(items: () -> List<T>, describe: (T) -> String, open: (Int) -> Unit, move: ((Int, Int) -> Unit)?=null, delete: ((Int) -> Unit)?=null): LinearLayout {
        val list=LinearLayout(context)
        list.orientation=LinearLayout.VERTICAL
        add(list)

        fun moveItem(view: View, by: Int): Boolean {
            val from=list.indexOfChild(view)
            val to=from+by
            if (move==null || from<0 || to<0 || to>=list.childCount)
            return false

            move(from, to)
            refresh()
            // The focus follows the moved item
            list.getChildAt(to)?.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            return true
            }

        fun deleteItem(view: View): Boolean {
            val index=list.indexOfChild(view)
            if (delete==null || index<0)
            return false

            delete(index)
            refresh()
            // The focus goes to the item taking its place, or the previous one
            val next=list.getChildAt(minOf(index, list.childCount-1))
            if (next!=null)
            next.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            else
            list.announceForAccessibility("Deleted")
            return true
            }

        refreshers.add {
            val current=items()
            while (list.childCount>current.size)
            list.removeViewAt(list.childCount-1)
            while (list.childCount<current.size) {
                val view=TextView(context)
                view.setOnClickListener { open(list.indexOfChild(view)) }
                if (move!=null) {
                    ViewCompat.addAccessibilityAction(view, "Move up") { v, _ -> moveItem(v, -1) }
                    ViewCompat.addAccessibilityAction(view, "Move down") { v, _ -> moveItem(v, 1) }
                    }
                if (delete!=null)
                ViewCompat.addAccessibilityAction(view, "Delete") { v, _ -> deleteItem(v) }
                list.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            for ((index, item) in current.withIndex())
            (list.getChildAt(index) as TextView).text=describe(item)
            }
        return list
        }

    fun refresh() {
        refreshing=true
        try {
            for (refresher in refreshers)
            refresher()
            }
        finally {
            refreshing=false
            }
        }

    // Retries the fields whose texts weren't taken, as they may depend on each other, e.g. the edges of a region
    // Returns the error of the first field still not taken, or null when all were
    fun commit(): String? {
        for (retry in failed.values.toList())
        retry()
        refresh()

        val layout=failed.keys.firstOrNull() ?: return null
        return "${layout.prefixText}: ${layout.error}"
        }

    private fun add(view: View, width: Int=ViewGroup.LayoutParams.MATCH_PARENT) {
        container.addView(view, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun textInputType(multiLine: Boolean): Int {
        return if (multiLine)
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else
        InputType.TYPE_CLASS_TEXT
        }
    }
