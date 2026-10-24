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

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType

class CommandLineTest {

    private val config=Config(parameters=listOf(
        Parameter("count", ParameterType.INT, positional=true, default="1"),
        Parameter("object", ParameterType.STRING, positional=true, rest=true, default="keys"),
        Parameter("loud", ParameterType.BOOL, short="l", default="false"),
        Parameter("ratio", ParameterType.DOUBLE, default="1"),
        ))

    private fun bind(line: String): Map<String, String> = CommandLine.parse(line).bind(config)

    private fun assertFails(line: String, messagePart: String) {
        try {
            bind(line)
            fail("\"$line\" should fail")
            }
        catch (e: ProcessException) {
            assertTrue("\"${e.message}\" should mention \"$messagePart\"", e.message!!.contains(messagePart))
            }
        }

    @Test
    fun tokenizes() {
        val line=CommandLine.parse("  find \"red keys\" 'it\\'s'  -x \"\" ")

        assertEquals("find", line.command)
        assertEquals(listOf("red keys", "it's", "-x", ""), line.arguments.map { it.text })
        assertEquals(listOf(true, true, false, true), line.arguments.map { it.quoted })
        assertNull(CommandLine.parse("   ").command)
        }

    @Test(expected=ProcessException::class)
    fun unclosedQuotesFail() {
        CommandLine.parse("find \"keys")
        }

    @Test
    fun bindsPositionalArguments() {
        assertEquals(mapOf<String, String>(), bind("find"))
        assertEquals(mapOf("count" to "2"), bind("find 2"))
        assertEquals(mapOf("count" to "2", "object" to "red  keys, please"), bind("find 2 red  keys, please "))
        assertEquals(mapOf("count" to "2", "object" to "red keys"), bind("find 2 \"red keys\""))
        // The rest is taken as typed, options included
        assertEquals(mapOf("count" to "-3", "object" to "\"a\" --loud"), bind("find -3 \"a\" --loud"))
        }

    @Test
    fun bindsNamedArguments() {
        assertEquals(mapOf("ratio" to "0.5", "image" to "\$defaultImageSource"), bind("find --ratio 0.5 -i=\$defaultImageSource"))
        assertEquals(mapOf("ratio" to "0.5"), bind("find --ratio=0.5"))
        assertEquals(mapOf("loud" to "true", "count" to "3"), bind("find --loud 3"))
        assertEquals(mapOf("loud" to "false", "count" to "3"), bind("find -l false 3"))
        assertEquals(mapOf("loud" to "true"), bind("find -l"))
        // Quoted, an option-like text is a value
        assertEquals(mapOf("count" to "--ratio"), bind("find \"--ratio\""))
        }

    @Test
    fun invalidArguments() {
        assertFails("find --size 2", "Unknown option --size")
        assertFails("find -z", "Unknown option -z")
        assertFails("find --ratio", "needs a value")
        assertFails("find --ratio 1 --ratio 2", "given twice")

        try {
            CommandLine.parse("base 1").bind(Config())
            fail()
            }
        catch (e: ProcessException) {
            assertEquals("Unexpected argument 1", e.message)
            }
        }
    
    @Test
    fun parsesArgumentsAlone() {
        val line=CommandLine.parseArguments("red keys")
        assertNull(line.command)
        assertEquals(mapOf("object" to "red keys"), line.bind(Config(parameters=listOf(Parameter("object", ParameterType.STRING, positional=true, rest=true)))))
        }

    @Test
    fun shiftedLineStartsWithTheNextToken() {
        val line=CommandLine.parse("fg find  red keys").shifted()
        assertEquals("find", line.command)
        assertEquals(listOf("red", "keys"), line.arguments.map { it.text })
        assertEquals(mapOf("object" to "red keys"), line.bind(Config(parameters=listOf(Parameter("object", ParameterType.STRING, positional=true, rest=true)))))
        }
    }
