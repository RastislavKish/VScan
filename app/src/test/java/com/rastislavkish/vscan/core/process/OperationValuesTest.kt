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
package com.rastislavkish.vscan.core.process

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.config.Extraction
import com.rastislavkish.vscan.core.config.NumberValue
import com.rastislavkish.vscan.core.script.ScriptEngine

class OperationValuesTest {

    private fun evaluate(value: NumberValue, text: String?, variables: Map<String, Any?> = mapOf()): Double {
        return OperationValues.evaluate(value, "pan", text, ScriptEngine.newScope(variables))
        }

    private fun assertMissing(value: NumberValue, text: String?) {
        try {
            evaluate(value, text)
            fail("\"$text\" shouldn't give a number")
            }
        catch (e: ProcessException) {
            assertEquals("No number for the pan in the output", e.message)
            }
        }

    @Test
    fun constantsAndExpressions() {
        assertEquals(0.8, evaluate(NumberValue(value=0.8), null), 0.0)
        assertEquals(0.25, evaluate(NumberValue(expression="x/4"), null, mapOf("x" to 1)), 0.0)

        try {
            evaluate(NumberValue(expression="'left'"), null)
            fail()
            }
        catch (e: ProcessException) {
            assertTrue(e.message!!.contains("not a number"))
            }
        }

    @Test
    fun extractsTheFirstNumber() {
        val number=NumberValue(extract=Extraction.NUMBER)

        assertEquals(-0.5, evaluate(number, "The object is at -0.5, 3 meters away"), 0.0)
        assertEquals(0.3, evaluate(number, "Pan 0,3"), 0.0)
        assertMissing(number, "None")
        assertMissing(number, null)
        }

    @Test
    fun extractsFromJson() {
        val text="Here you go:\n```json\n{\"objects\": [{\"x\": 0.4}, {\"x\": \"-0.2\"}]}\n```"

        assertEquals(0.4, evaluate(NumberValue(extract=Extraction.JSON, path="objects.0.x"), text), 0.0)
        assertEquals(-0.2, evaluate(NumberValue(extract=Extraction.JSON, path="objects.1.x"), text), 0.0)
        assertEquals(7.0, evaluate(NumberValue(extract=Extraction.JSON), "7"), 0.0)
        assertMissing(NumberValue(extract=Extraction.JSON, path="objects.2.x"), text)
        assertMissing(NumberValue(extract=Extraction.JSON, path="x"), "Not JSON")
        }

    @Test
    fun extractsWithARegex() {
        assertEquals(12.0, evaluate(NumberValue(extract=Extraction.REGEX, pattern="x=(\\d+)"), "y=3 x=12"), 0.0)
        assertEquals(3.0, evaluate(NumberValue(extract=Extraction.REGEX, pattern="\\d"), "y=3 x=12"), 0.0)
        assertMissing(NumberValue(extract=Extraction.REGEX, pattern="z=(\\d+)"), "y=3")
        }

    @Test
    fun stereoVolumes() {
        assertEquals(Pair(1f, 1f), OperationValues.stereoVolumes(1.0, 0.0))
        assertEquals(Pair(0.5f, 0f), OperationValues.stereoVolumes(0.5, -1.0))
        assertEquals(Pair(0.5f, 1f), OperationValues.stereoVolumes(1.0, 0.5))
        assertEquals(Pair(0f, 1f), OperationValues.stereoVolumes(2.0, 3.0))
        }

    @Test
    fun waveforms() {
        val (once, noLoop)=OperationValues.waveform(listOf(0, 100, 50, 100), 1)
        assertArrayEquals(longArrayOf(0, 100, 50, 100), once)
        assertEquals(-1, noLoop)

        val (twice, _)=OperationValues.waveform(listOf(0, 100, 50), 2)
        assertArrayEquals(longArrayOf(0, 100, 50, 0, 0, 100, 50, 0), twice)

        val (looped, loopIndex)=OperationValues.waveform(listOf(200, 100), 0)
        assertArrayEquals(longArrayOf(200, 100), looped)
        assertEquals(0, loopIndex)

        for (invalid in listOf(listOf<Long>(), listOf(0L, 0L), listOf(10L, -5L))) {
            try {
                OperationValues.waveform(invalid, 1)
                fail("$invalid should be invalid")
                }
            catch (e: ProcessException) {}
            }
        }
    }
