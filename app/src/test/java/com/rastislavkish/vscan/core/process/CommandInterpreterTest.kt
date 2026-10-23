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
import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.StaticImageSource
import com.rastislavkish.vscan.core.script.ScriptScope

class CommandInterpreterTest {

    private val spawned=mutableListOf<Process>()

    private val configs=listOf(
        Config(id="bus-lines", name="Bus lines", command="blr", lifetime=Lifetime(interval=1.0)),
        Config(id="find", name="Find", parameters=listOf(Parameter("object", ParameterType.STRING, positional=true, rest=true))),
        )

    private fun TestScope.interpreter(): CommandInterpreter {
        val manager=ProcessManager(
            Arguments(StaticImageSource(ImageData.fromEncoded(byteArrayOf(1)))),
            { object: AnalyzerRuntime {
                override suspend fun analyze(input: AnalysisInput): AnalysisResult = AnalysisResult("A", true)
                }},
            object: OperationExecutor {
                override suspend fun execute(operation: Operation, scope: ScriptScope, process: Process) {}
                },
            object: ProcessListener {
                override fun onError(process: Process, message: String) {}
                override fun onEnd(process: Process) {}
                },
            { false },
            this,
            { testScheduler.currentTime },
            )

        return CommandInterpreter({ configs }, manager)
        }

    @Test
    fun startsConfigsByCommandOrId() = runTest {
        val interpreter=interpreter()

        assertEquals("Started Bus lines as 1", interpreter.execute("blr"))
        assertEquals("Started Bus lines as 2", interpreter.execute("bus-lines"))
        assertEquals("Started Find as 3", interpreter.execute("find red keys"))
        assertEquals("Missing object", interpreter.execute("find"))
        assertEquals("Unknown command bus", interpreter.execute("bus"))
        assertEquals("Unclosed \"", interpreter.execute("find \"keys"))
        assertEquals("", interpreter.execute("  "))

        interpreter.execute("killall")
        }

    @Test
    fun managesProcesses() = runTest {
        val interpreter=interpreter()
        assertEquals("No processes", interpreter.execute("ps"))

        interpreter.execute("blr")
        interpreter.execute("blr")
        advanceTimeBy(1500)
        assertEquals("1 Bus lines, iteration 2. 2 Bus lines, iteration 2", interpreter.execute("ps"))

        assertEquals("Killed 1. No process 7. x is not a process id", interpreter.execute("kill 1 7 x"))
        assertEquals("2 Bus lines, iteration 2", interpreter.execute("ps"))
        assertEquals("Usage: kill pid...", interpreter.execute("kill"))

        assertEquals("Killed 1 process", interpreter.execute("killall"))
        assertEquals("No processes", interpreter.execute("ps"))
        }
    }
