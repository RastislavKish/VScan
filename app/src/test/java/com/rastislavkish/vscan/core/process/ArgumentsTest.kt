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
import com.rastislavkish.vscan.core.config.ConfigLens
import com.rastislavkish.vscan.core.config.LensTarget
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.LensSource
import com.rastislavkish.vscan.core.imagesource.StaticImageSource
import com.rastislavkish.vscan.core.imagesource.Zoom

class ArgumentsTest {

    private val defaultSource=StaticImageSource(ImageData.fromEncoded(byteArrayOf(1)))
    private val arguments=Arguments(defaultSource)

    private fun assertFails(messagePart: String, block: () -> Unit) {
        try {
            block()
            fail("Should have failed")
            }
        catch (e: ProcessException) {
            assertTrue("\"${e.message}\" should mention \"$messagePart\"", e.message!!.contains(messagePart))
            }
        }

    @Test
    fun defaultsAndConversions() {
        val config=Config(parameters=listOf(
            Parameter("object", ParameterType.STRING, default="keys"),
            Parameter("count", ParameterType.INT, default="2"),
            Parameter("ratio", ParameterType.DOUBLE, default="0.5"),
            Parameter("loud", ParameterType.BOOL, default="false"),
            ))

        val values=arguments.resolve(config, mapOf("object" to "a wallet", "loud" to "True"))

        assertSame(defaultSource, values["image"])
        assertEquals("a wallet", values["object"])
        assertEquals(2.0, values["count"])
        assertEquals(0.5, values["ratio"])
        assertEquals(true, values["loud"])
        }

    @Test
    fun invalidArguments() {
        val config=Config(parameters=listOf(
            Parameter("count", ParameterType.INT, default="2"),
            Parameter("required", ParameterType.STRING),
            ))

        assertFails("Missing required") { arguments.resolve(config) }
        assertFails("not a whole number") { arguments.resolve(config, mapOf("required" to "x", "count" to "2.5")) }
        assertFails("no parameter other") { arguments.resolve(config, mapOf("required" to "x", "other" to "y")) }
        assertFails("Unknown image source") { arguments.resolve(config, mapOf("required" to "x", "image" to "\$glasses")) }
        }

    @Test
    fun invalidParameters() {
        assertFails("process variable") { arguments.resolve(Config(parameters=listOf(Parameter("lastOutput", ParameterType.STRING, default="")))) }
        assertFails("Duplicate") { arguments.resolve(Config(parameters=listOf(Parameter("a", ParameterType.STRING, default=""), Parameter("a", ParameterType.INT, default="1")))) }
        }

    @Test
    fun lensAppliesToTheChosenSources() {
        val parameters=listOf(Parameter("other", ParameterType.IMAGE_SOURCE, default="\$default"))
        val lens=listOf(Zoom(2f))

        val imageOnly=arguments.resolve(Config(parameters=parameters, lens=ConfigLens(lens, LensTarget.IMAGE)))
        assertTrue(imageOnly["image"] is LensSource)
        assertSame(defaultSource, imageOnly["other"])

        val all=arguments.resolve(Config(parameters=parameters, lens=ConfigLens(lens, LensTarget.ALL)))
        assertTrue(all["image"] is LensSource)
        assertTrue(all["other"] is LensSource)

        assertSame(defaultSource, arguments.resolve(Config())["image"])
        }
    }
