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
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.StaticImageSource
import com.rastislavkish.vscan.core.script.ScriptScope

class ProcessManagerTest {

    private object ConstantAnalyzer: AnalyzerRuntime {
        override suspend fun analyze(input: AnalysisInput): AnalysisResult = AnalysisResult("A", true)
        }
    private object IgnoringExecutor: OperationExecutor {
        override suspend fun execute(operation: Operation, scope: ScriptScope, process: Process) {}
        }

    private val ended=mutableListOf<Int>()

    private fun TestScope.manager(createAnalyzer: (Config) -> AnalyzerRuntime = { ConstantAnalyzer }) = ProcessManager(
        Arguments(StaticImageSource(ImageData.fromEncoded(byteArrayOf(1)))),
        createAnalyzer,
        IgnoringExecutor,
        object: ProcessListener {
            override fun onError(process: Process, message: String) {}
            override fun onEnd(process: Process) { ended.add(process.pid) }
            },
        { false },
        this,
        { testScheduler.currentTime },
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
    }
