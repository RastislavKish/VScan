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

import kotlinx.serialization.json.JsonPrimitive

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.config.Extraction
import com.rastislavkish.vscan.core.config.NumberValue

class ValuesTest {

    @Test
    fun numbers() {
        assertEquals("2", Values.formatNumber(2.0))
        assertEquals("0.5", Values.formatNumber(0.5))
        assertEquals("-3", Values.formatNumber(-3.0))
        assertEquals(0.5, Values.parseDouble(" 0,5 "), 0.0)
        assertEquals(-2, Values.parseInt("-2"))

        assertFails { Values.parseDouble("") }
        assertFails { Values.parseInt("1.5") }
        }

    @Test
    fun patterns() {
        assertEquals(listOf(0L, 100L, 50L), Values.parsePattern("0, 100 ,50"))
        assertEquals(listOf(0L, 100L), Values.parsePattern("0 100"))
        assertEquals("0, 100", Values.formatPattern(listOf(0, 100)))
        assertFails { Values.parsePattern("0, a") }
        assertFails { Values.parsePattern("-1") }
        }

    @Test
    fun memoryValues() {
        assertEquals(JsonPrimitive(3L), Values.parseMemoryValue("3"))
        assertEquals(JsonPrimitive(0.5), Values.parseMemoryValue("0.5"))
        assertEquals(JsonPrimitive(true), Values.parseMemoryValue("true"))
        assertEquals(JsonPrimitive("near"), Values.parseMemoryValue("near"))
        assertEquals(JsonPrimitive("3"), Values.parseMemoryValue("'3'"))
        assertEquals(JsonPrimitive("red keys"), Values.parseMemoryValue("red keys"))
        assertEquals(JsonPrimitive(""), Values.parseMemoryValue(""))
        assertFails { Values.parseMemoryValue("\"open") }

        for (value in listOf(JsonPrimitive(3L), JsonPrimitive(0.5), JsonPrimitive(false), JsonPrimitive("near"), JsonPrimitive("3"), JsonPrimitive("true"), JsonPrimitive("say \"hi\""), JsonPrimitive("")))
        assertEquals(value, Values.parseMemoryValue(Values.formatMemoryValue(value)))

        assertEquals("near", Values.formatMemoryValue(JsonPrimitive("near")))
        assertEquals("\"3\"", Values.formatMemoryValue(JsonPrimitive("3")))
        }

    @Test
    fun numberValueKinds() {
        assertEquals(NumberValueKind.NONE, NumberValueKind.of(null))
        assertEquals(NumberValueKind.CONSTANT, NumberValueKind.of(NumberValue(value=0.8)))
        assertEquals(NumberValueKind.JSON, NumberValueKind.of(NumberValue(extract=Extraction.JSON, path="x")))
        assertEquals(NumberValueKind.EXPRESSION, NumberValueKind.of(NumberValue(expression="1")))

        assertNull(Values.withKind(NumberValue(value=0.8), NumberValueKind.NONE, 1.0))
        assertEquals(NumberValue(value=1.0), Values.withKind(null, NumberValueKind.CONSTANT, 1.0))
        assertEquals(NumberValue(value=0.8), Values.withKind(NumberValue(value=0.8), NumberValueKind.CONSTANT, 1.0))
        assertEquals(NumberValue(extract=Extraction.JSON, path=""), Values.withKind(NumberValue(value=0.8), NumberValueKind.JSON, 1.0))
        assertEquals(NumberValue(extract=Extraction.REGEX, pattern=""), Values.withKind(null, NumberValueKind.REGEX, 1.0))
        }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            fail("Should fail")
            }
        catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
            }
        }
    }
