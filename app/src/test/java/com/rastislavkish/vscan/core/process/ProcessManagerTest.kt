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

import kotlinx.coroutines.test.*

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.config.Lifetime
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.imagesource.StaticImageSource
import com.rastislavkish.vscan.core.script.ScriptScope

class ProcessManagerTest {

    private object ConstantAnalyzer: AnalyzerRuntime {
        override suspend fun analyze(input: AnalysisInput): AnalysisResult = AnalysisResult("A", { true })
        }
    private object IgnoringExecutor: OperationExecutor {
        override suspend fun execute(operation: Operation, scope: ScriptScope, process: Process) {}
        }

    // Takes the image, as an LLM analysis does
    private object CapturingAnalyzer: AnalyzerRuntime {
        override suspend fun analyze(input: AnalysisInput): AnalysisResult {
            input.capture(input.variables["image"] as ImageSource)
            return AnalysisResult("A", { it!="A" })
            }
        }

    private val image=ImageData.fromEncoded(byteArrayOf(1))
    private val ended=mutableListOf<Int>()
    private val lastImage=LastImage()

    private fun TestScope.manager(createAnalyzer: (Config) -> AnalyzerRuntime = { ConstantAnalyzer }) = ProcessManager(
        Arguments(GlobalMemory(StaticImageSource(image), lastImage)),
        createAnalyzer,
        IgnoringExecutor,
        object: ProcessListener {
            override fun onError(process: Process, message: String) {}
            override fun onEnd(process: Process) { ended.add(process.pid) }
            },
        { false },
        scope=this,
        clock={ testScheduler.currentTime },
        lastImage=lastImage,
        )

    @Test
    fun tracksRunningProcesses() = runTest {
        val manager=manager()
        val looping=Config(lifetime=Lifetime(interval=1.0))

        val first=manager.spawn(looping)
        val second=manager.spawn(looping)
        val singleShot=manager.spawn(Config())

        assertEquals(listOf(1, 2, 3), listOf(first.pid, second.pid, singleShot.pid))
        advanceTimeBy(100)
        assertEquals(listOf(1, 2), manager.list().map { it.pid })

        assertTrue(manager.kill(1))
        assertFalse(manager.kill(3))
        advanceTimeBy(100)
        assertEquals(listOf(2), manager.list().map { it.pid })

        manager.killAll()
        advanceUntilIdle()
        assertEquals(listOf<Process>(), manager.list())
        assertEquals(listOf(3, 1, 2), ended)
        // Pids aren't reused
        assertEquals(4, manager.spawn(Config()).pid)
        }

    @Test
    fun invalidConfigsDontStart() = runTest {
        val manager=manager { config -> LlmAnalyzerRuntime(config.analyzer as LlmAnalyzer, object: LlmBackend {
            override fun newConversation(analyzer: LlmAnalyzer, systemMessage: com.rastislavkish.vscan.core.openai.SystemMessage?) = throw UnsupportedOperationException()
            override suspend fun respond(conversation: com.rastislavkish.vscan.core.openai.Conversation) = throw UnsupportedOperationException()
            })}

        try {
            manager.spawn(Config(analyzer=LlmAnalyzer(userPrompt="{% if x %}")))
            fail()
            }
        catch (e: ProcessException) {
            assertTrue(e.message!!.contains("endif"))
            }

        assertEquals(listOf<Process>(), manager.list())
        }
    
    @Test
    fun foregroundProcessWaitsForScans() = runTest {
        val manager=manager { CapturingAnalyzer }
        val process=manager.setForeground(Config(lifetime=Lifetime(interval=1.0)))
        advanceTimeBy(5000)

        assertSame(process, manager.foreground)
        assertEquals(0, process.iteration)
        // Listed only while it iterates on its own
        assertEquals(listOf<Process>(), manager.list())
        assertNull(lastImage.taken)

        assertTrue(process.scan(true))
        assertEquals("A", process.lastOutput)
        assertSame(image, lastImage.taken?.image)
        }

    @Test
    fun foregroundProcessStaysWhenStopped() = runTest {
        val manager=manager()
        val process=manager.setForeground(Config(lifetime=Lifetime(interval=1.0)))

        manager.startForeground()
        advanceTimeBy(2500)
        assertEquals(listOf(process), manager.list())
        assertEquals(3, process.iteration)

        // kill only stops its iterations
        assertTrue(manager.kill(process.pid))
        advanceUntilIdle()
        assertSame(process, manager.foreground)
        assertEquals(ProcessStatus.RUNNING, process.status)
        assertEquals(listOf<Process>(), manager.list())
        assertFalse(manager.kill(process.pid))

        // A single-shot config iterates once, and stays
        manager.updateForeground(Config())
        manager.startForeground()
        advanceUntilIdle()
        assertEquals(4, process.iteration)
        assertSame(process, manager.foreground)
        }

    @Test
    fun replacingTheForegroundProcessDiscardsTheOldOne() = runTest {
        val manager=manager()
        val first=manager.setForeground(Config(lifetime=Lifetime(interval=1.0)))
        manager.startForeground()
        advanceTimeBy(100)

        val second=manager.setForeground(Config())
        advanceTimeBy(100)
        assertSame(second, manager.foreground)
        assertFalse(first.foreground)
        assertEquals(ProcessStatus.KILLED, first.status)
        assertNull(manager.get(first.pid))

        // A config that can't start leaves the old process
        val failing=Config(parameters=listOf(Parameter("object", ParameterType.STRING)))
        assertThrows(ProcessException::class.java) { manager.setForeground(failing) }
        assertSame(second, manager.foreground)
        }

    @Test
    fun invalidEditsAreKeptAndFailTheIterations() = runTest {
        val manager=manager { config ->
            if (config.name=="Broken")
            throw IllegalStateException("Syntax error")
            ConstantAnalyzer
            }
        val process=manager.setForeground(Config())

        manager.updateForeground(Config(name="Broken"))
        assertEquals("Broken", process.config.name)
        assertFalse(process.scan(true))

        manager.updateForeground(Config(name="Fixed"))
        assertTrue(process.scan(true))
        }

    @Test
    fun reportsConfigProblems() = runTest {
        val manager=manager { config ->
            if (config.name=="Broken")
            throw IllegalStateException("Syntax error")
            ConstantAnalyzer
            }

        assertNull(manager.problem(Config()))
        assertEquals("Syntax error", manager.problem(Config(name="Broken")))
        assertEquals("max_in_flight must be at least 1", manager.problem(Config(lifetime=Lifetime(maxInFlight=0))))
        }

    @Test
    fun movesProcessesBetweenForegroundAndBackground() = runTest {
        val manager=manager()
        val looping=Config(lifetime=Lifetime(interval=1.0))
        val first=manager.setForeground(looping)

        assertSame(first, manager.background())
        advanceTimeBy(1500)
        assertFalse(first.foreground)
        assertEquals(2, first.iteration)
        val replacement=manager.foreground!!
        assertNotSame(first, replacement)
        assertEquals(looping, replacement.config)

        assertSame(first, manager.bringToForeground(first.pid))
        advanceTimeBy(100)
        assertSame(first, manager.foreground)
        // Keeps iterating
        assertTrue(first.isIterating)
        assertNull(manager.get(replacement.pid))
        assertNull(manager.bringToForeground(replacement.pid))

        manager.killAll()
        advanceUntilIdle()
        assertSame(first, manager.foreground)
        }

    @Test
    fun lastImageIsTakenWhenCalled() = runTest {
        val manager=manager { CapturingAnalyzer }
        assertThrows(ProcessException::class.java) { manager.spawn(Config(), mapOf("image" to "\$lastImage")) }

        val other=ImageData.fromEncoded(byteArrayOf(2))
        lastImage.set(other)
        val process=manager.spawn(Config(), mapOf("image" to "\$lastImage"))
        lastImage.set(image)
        advanceUntilIdle()

        assertEquals(ProcessStatus.FINISHED, process.status)
        // Background processes don't change it
        assertSame(image, lastImage.taken?.image)
        }
    }
