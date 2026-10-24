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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.files.BrokenFile
import com.rastislavkish.vscan.core.files.LocalFileTree
import com.rastislavkish.vscan.core.files.VScanFiles
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.StaticImageSource

@RunWith(RobolectricTestRunner::class)
class GlobalMemoryTest {

    @get:Rule
    val temporary=TemporaryFolder()

    private lateinit var files: VScanFiles
    private val brokenFiles=mutableListOf<BrokenFile>()
    private val defaultSource=StaticImageSource(ImageData.fromEncoded(byteArrayOf(1)))
    private val lastImage=LastImage()

    // Passed through as they are, without decoding
    private val png=byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @Before
    fun setUp() {
        files=newFiles()
        }

    private fun newFiles() = VScanFiles(LocalFileTree(temporary.root.resolve("VScan")), temporary.root.resolve("manifest.json")).also {
        it.onBrokenFile={ file -> brokenFiles.add(file) }
        }

    private fun memory() = GlobalMemory(defaultSource, lastImage, newFiles())

    @Test
    fun variablesArePersistent() {
        val memory=memory()
        memory.set("thing", Variable(ParameterType.STRING, "a wallet"))
        memory.set("count", Variable(ParameterType.INT, 3.0))
        memory.set("ratio", Variable(ParameterType.DOUBLE, 0.5))
        memory.set("loud", Variable(ParameterType.BOOL, true))
        memory.set("door", Variable(ParameterType.IMAGE, ImageData.fromEncoded(png)))

        assertTrue(files.readText("memory/variables.toml")!!.contains("count = 3\n"))
        assertArrayEquals(png, files.read("memory/images/door.png"))

        val loaded=memory()
        assertEquals(listOf("count", "door", "loud", "ratio", "thing"), loaded.list().map { it.first })
        assertEquals(ParameterType.INT, loaded.get("count")?.type)
        assertEquals(3.0, loaded.get("count")?.value)
        assertEquals(0.5, loaded.get("ratio")?.value)
        assertEquals(true, loaded.get("loud")?.value)
        assertEquals("a wallet", loaded.get("thing")?.value)
        assertArrayEquals(png, (loaded.get("door")?.value as ImageData).encoded)
        assertNull(loaded.get("other"))
        }

    @Test
    fun reassigningChangesTheKind() {
        val memory=memory()
        memory.set("door", Variable(ParameterType.IMAGE, ImageData.fromEncoded(png)))
        memory.set("door", Variable(ParameterType.STRING, "closed"))
        assertNull(files.read("memory/images/door.png"))
        assertEquals("closed", memory().get("door")?.value)

        memory.set("door", Variable(ParameterType.IMAGE, ImageData.fromEncoded(png)))
        assertFalse(files.readText("memory/variables.toml")!!.contains("door"))

        assertTrue(memory.unset("door"))
        assertFalse(memory.unset("door"))
        assertNull(files.read("memory/images/door.png"))
        assertTrue(memory().list().isEmpty())
        }

    @Test
    fun builtInVariables() {
        val memory=memory()
        assertSame(defaultSource, memory.get("defaultImageSource")?.value)
        assertThrows(ProcessException::class.java) { memory.get("lastImage") }

        val image=ImageData.fromEncoded(png)
        lastImage.set(image)
        assertSame(image, memory.get("lastImage")?.value)

        assertThrows(ProcessException::class.java) { memory.set("lastImage", Variable(ParameterType.STRING, "x")) }
        assertThrows(ProcessException::class.java) { memory.set("cam", Variable(ParameterType.IMAGE_SOURCE, defaultSource)) }
        assertThrows(ProcessException::class.java) { memory.set("2door", Variable(ParameterType.STRING, "x")) }
        }

    @Test
    fun namesDifferingInCaseClash() {
        val memory=memory()
        memory.set("door", Variable(ParameterType.STRING, "x"))
        assertThrows(ProcessException::class.java) { memory.set("Door", Variable(ParameterType.STRING, "y")) }
        }

    @Test
    fun brokenVariablesAreNotOverwritten() {
        files.write("memory/variables.toml", "count = ".toByteArray())
        files.write("memory/images/door.png", png)

        val memory=memory()
        assertEquals(listOf("memory/variables.toml"), brokenFiles.map { it.path })
        // The images are still there
        assertNotNull(memory.get("door"))
        assertThrows(ProcessException::class.java) { memory.set("count", Variable(ParameterType.INT, 1.0)) }
        assertEquals("count = ", files.readText("memory/variables.toml"))
        }

    @Test
    fun loadTakesExternalChanges() {
        val memory=memory()
        memory.set("count", Variable(ParameterType.INT, 1.0))

        files.write("memory/variables.toml", "count = 2\nname = \"Ann\"\n".toByteArray())
        memory.load()
        assertEquals(2.0, memory.get("count")?.value)
        assertEquals("Ann", memory.get("name")?.value)
        }
    }
