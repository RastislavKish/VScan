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
package com.rastislavkish.vscan.core.script

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

import com.rastislavkish.vscan.core.template.Template

// Rhino is a desktop Java library, so this checks it actually runs on Android's runtime
@RunWith(AndroidJUnit4::class)
class ScriptEngineInstrumentedTest {

    @Test
    fun evaluatesModernJavaScript() {
        val scope=ScriptEngine.newScope(mapOf("history" to listOf("a", "b")))

        assertEquals("A-B", scope.evaluate("history.map(x => x.toUpperCase()).join('-')"))
        assertEquals("line 42", scope.evaluate("const n=42; `line ${'$'}{n}`"))
        }

    @Test
    fun rendersTemplates() {
        val template=Template.parse("{% for i, x in history %}{{ i }}:{{ x }} {% endfor %}")

        assertEquals("0:a 1:b ", template.renderText(ScriptEngine.newScope(mapOf("history" to listOf("a", "b")))))
        }

    @Test(timeout=10000)
    fun endlessLoopsTimeOut() {
        try {
            ScriptEngine.newScope().evaluate("while (true) {}")
            fail()
            }
        catch (e: ScriptException) {
            assertTrue(e.message!!.contains("longer than"))
            }
        }

    @Test
    fun hasNoAccessToJava() {
        try {
            ScriptEngine.newScope().evaluate("java.lang.System.exit(0)")
            fail()
            }
        catch (e: ScriptException) {
            }
        }

    }
