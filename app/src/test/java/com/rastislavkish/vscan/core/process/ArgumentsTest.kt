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
    private val lastImage=LastImage()
    private val memory=GlobalMemory(defaultSource, lastImage)
    private val arguments=Arguments(memory)

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
        assertFails("Unknown variable") { arguments.resolve(config, mapOf("required" to "x", "image" to "\$glasses")) }
        }

    @Test
    fun invalidParameters() {
        assertFails("process variable") { arguments.resolve(Config(parameters=listOf(Parameter("lastOutput", ParameterType.STRING, default="")))) }
        assertFails("Duplicate") { arguments.resolve(Config(parameters=listOf(Parameter("a", ParameterType.STRING, default=""), Parameter("a", ParameterType.INT, default="1")))) }
        }

    @Test
    fun lensAppliesToTheChosenSources() {
        val parameters=listOf(Parameter("other", ParameterType.IMAGE_SOURCE, default="\$defaultImageSource"))
        val lens=listOf(Zoom(2f))

        val imageOnly=arguments.resolve(Config(parameters=parameters, lens=ConfigLens(lens, LensTarget.IMAGE)))
        assertTrue(imageOnly["image"] is LensSource)
        assertSame(defaultSource, imageOnly["other"])

        val all=arguments.resolve(Config(parameters=parameters, lens=ConfigLens(lens, LensTarget.ALL)))
        assertTrue(all["image"] is LensSource)
        assertTrue(all["other"] is LensSource)

        assertSame(defaultSource, arguments.resolve(Config())["image"])
        }
    
    @Test
    fun variables() {
        val image=ImageData.fromEncoded(byteArrayOf(2))
        memory.set("thing", Variable(ParameterType.STRING, "a wallet"))
        memory.set("two", Variable(ParameterType.INT, 2.0))
        memory.set("half", Variable(ParameterType.DOUBLE, 0.5))
        memory.set("door", Variable(ParameterType.IMAGE, image))

        val config=Config(parameters=listOf(
            Parameter("object", ParameterType.STRING),
            Parameter("count", ParameterType.INT),
            Parameter("ratio", ParameterType.DOUBLE),
            Parameter("picture", ParameterType.IMAGE, default="\$door"),
            ))

        val values=arguments.resolve(config, mapOf("object" to "\$thing", "count" to "\$two", "ratio" to " \$half ", "image" to "\$door"))
        assertEquals("a wallet", values["object"])
        assertEquals(2.0, values["count"])
        assertEquals(0.5, values["ratio"])
        assertSame(image, values["picture"])
        // An image serves as an image source giving it
        assertTrue(values["image"] is StaticImageSource)

        // Numbers convert as their texts, \$ starts a text with a dollar
        assertEquals("2", arguments.resolve(config, mapOf("object" to "\$two", "count" to "1", "ratio" to "1"))["object"])
        assertEquals("\$thing", arguments.resolve(config, mapOf("object" to "\\\$thing", "count" to "1", "ratio" to "1"))["object"])

        assertFails("not a whole number") { arguments.resolve(config, mapOf("object" to "x", "count" to "\$half", "ratio" to "1")) }
        assertFails("Expected an image source, got a string") { arguments.resolve(config, mapOf("object" to "x", "count" to "1", "ratio" to "1", "image" to "\$thing")) }
        assertFails("Pass an image as a variable") { arguments.resolve(config, mapOf("object" to "x", "count" to "1", "ratio" to "1", "picture" to "door.jpg")) }
        assertFails("No image taken yet") { arguments.resolve(config, mapOf("object" to "x", "count" to "1", "ratio" to "1", "picture" to "\$lastImage")) }

        lastImage.set(image)
        assertSame(image, arguments.resolve(config, mapOf("object" to "x", "count" to "1", "ratio" to "1", "picture" to "\$lastImage"))["picture"])
        }

    @Test
    fun anyParameters() {
        val image=ImageData.fromEncoded(byteArrayOf(2))
        memory.set("photo", Variable(ParameterType.IMAGE, image))
        memory.set("two", Variable(ParameterType.INT, 2.0))

        val config=Config(parameters=listOf(Parameter("value", ParameterType.ANY, default="keys")))
        fun value(text: String): Any? = arguments.resolve(config, mapOf("value" to text))["value"]

        // Texts are read like assigned values
        assertEquals("keys", arguments.resolve(config)["value"])
        assertEquals(3.0, value("3"))
        assertEquals(0.5, value("0.5"))
        assertEquals(true, value("true"))
        assertEquals("3", value("'3'"))
        assertEquals("red keys", value("red keys"))
        assertEquals("\$5", value("\\\$5"))

        // Variables pass as they are
        assertEquals(2.0, value("\$two"))
        assertSame(image, value("\$photo"))
        assertSame(defaultSource, value("\$defaultImageSource"))
        assertFails("Unknown variable") { value("\$missing") }

        // The lens applies to image sources given to Any parameters when it applies to all
        val lens=ConfigLens(listOf(Zoom(2f)), LensTarget.ALL)
        val lensed=arguments.resolve(config.copy(lens=lens), mapOf("value" to "\$defaultImageSource"))
        assertTrue(lensed["value"] is LensSource)
        assertSame(image, arguments.resolve(config.copy(lens=lens), mapOf("value" to "\$photo"))["value"])
        }
    }
