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
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputLayout

import kotlinx.serialization.json.JsonPrimitive

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import com.rastislavkish.vscan.R
import com.rastislavkish.vscan.core.imagesource.Crop
import com.rastislavkish.vscan.core.imagesource.RelativeRect
import com.rastislavkish.vscan.ui.itemeditoractivity.ItemForm
import com.rastislavkish.vscan.ui.itemeditoractivity.ItemKind
import com.rastislavkish.vscan.ui.itemeditoractivity.MemoryVariable

@RunWith(RobolectricTestRunner::class)
class FormTest {

    private val context: Context=ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_VScan)
    private val container=LinearLayout(context)
    private val form=Form(context, container)

    private fun TextInputLayout.input(): EditText = editText!!

    @Test
    fun fieldsShowAndChangeTheValue() {
        var interval=2.0
        val field=form.double("Interval", { interval }, { interval=it })
        form.refresh()
        assertEquals("2", field.input().text.toString())

        field.input().setText("0,5")
        assertEquals(0.5, interval, 0.0)
        assertNull(field.error)

        // An invalid text stays for the user to fix, the value keeping the last valid one
        field.input().setText("0.")
        assertEquals(0.0, interval, 0.0)
        field.input().setText("x")
        assertEquals("Not a number", field.error)
        assertEquals(0.0, interval, 0.0)
        form.refresh()
        assertEquals("x", field.input().text.toString())

        interval=3.0
        field.input().setText("3")
        assertNull(field.error)
        assertNull(form.commit())
        }

    @Test
    fun visibilityFollowsTheValue() {
        var on=false
        val switch=form.switch("On", { on }, { on=it })
        val shown=form.text("Shown when on", { "" }, {})
        form.visibleWhen({ on }, shown)
        form.refresh()
        assertEquals(View.GONE, shown.visibility)

        switch.isChecked=true
        assertTrue(on)
        assertEquals(View.VISIBLE, shown.visibility)
        }

    @Test
    fun listsFollowTheItems() {
        var items=listOf("a", "b")
        val list=form.list({ items }, { it.uppercase() }, {})
        form.refresh()
        assertEquals(listOf("A", "B"), (0 until list.childCount).map { (list.getChildAt(it) as TextView).text.toString() })

        items=listOf("c")
        form.refresh()
        assertEquals(1, list.childCount)
        assertEquals("C", (list.getChildAt(0) as TextView).text.toString())
        }

    @Test
    fun commitRetriesFieldsDependingOnEachOther() {
        val itemForm=ItemForm.create(ItemKind.LENS_OPERATION, ItemForm.encode(Crop(RelativeRect(0f, 0f, 0.5f, 1f))))
        itemForm.build(form)
        form.refresh()

        val fields=(0 until container.childCount).map { container.getChildAt(it) }.filterIsInstance<TextInputLayout>()
        val (left, _, right)=fields

        // The left edge can't pass the right one until that moves
        left.input().setText("0.6")
        assertNotNull(left.error)
        right.input().setText("0.9")
        assertNotNull(left.error)

        // Committing retries it
        assertNull(form.commit())
        assertEquals(Crop(RelativeRect(0.6f, 0f, 0.9f, 1f)), itemForm.item)
        assertNull(left.error)
        }

    @Test
    fun itemFormsCheckTheirItems() {
        val variable=ItemForm.create(ItemKind.MEMORY_VARIABLE, ItemForm.encode(MemoryVariable("", JsonPrimitive(""))))
        variable.build(form)
        form.refresh()
        assertNotNull(variable.problem())

        val fields=(0 until container.childCount).map { container.getChildAt(it) }.filterIsInstance<TextInputLayout>()
        fields[0].input().setText("count")
        fields[1].input().setText("3")
        assertNull(variable.problem())
        assertEquals(MemoryVariable("count", JsonPrimitive(3L)), ItemForm.decodeMemoryVariable(variable.json))
        }
    }
