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
            Arguments(GlobalMemory(StaticImageSource(ImageData.fromEncoded(byteArrayOf(1))), LastImage())),
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

        this@CommandInterpreterTest.manager=manager
        return CommandInterpreter({ configs }, manager)
        }

    private lateinit var manager: ProcessManager

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
    
    @Test
    fun movesProcessesBetweenForegroundAndBackground() = runTest {
        val interpreter=interpreter()
        assertEquals("No foreground process", interpreter.execute("bg"))
        assertEquals("Usage: fg pid, or fg command...", interpreter.execute("fg"))

        assertEquals("Find is in the foreground", interpreter.execute("fg find red keys"))
        assertEquals(mapOf("object" to "red keys"), manager.foreground?.given)
        assertEquals("Missing object", interpreter.execute("fg find"))
        assertEquals("Find", manager.foreground?.config?.name)

        interpreter.execute("fg blr")
        assertEquals("No processes", interpreter.execute("ps"))
        assertEquals("Bus lines runs in the background as 2", interpreter.execute("bg"))
        advanceTimeBy(100)
        assertEquals("2 Bus lines, iteration 1", interpreter.execute("ps"))

        assertEquals("Bus lines is in the foreground", interpreter.execute("fg 2"))
        advanceTimeBy(100)
        assertEquals("2 Bus lines, iteration 1, foreground", interpreter.execute("ps"))
        assertEquals("No process 7", interpreter.execute("fg 7"))

        assertEquals("Killed 1 process", interpreter.execute("killall"))
        assertEquals("Bus lines", manager.foreground?.config?.name)
        }
    
    @Test
    fun assignsVariables() = runTest {
        val interpreter=interpreter()

        assertEquals("count = 3", interpreter.execute("count = 3"))
        assertEquals("ratio = 0.5", interpreter.execute("ratio=0.5"))
        assertEquals("loud = true", interpreter.execute("loud =true"))
        assertEquals("thing = \"red keys\"", interpreter.execute("thing = red keys"))
        assertEquals("quoted = \"3\"", interpreter.execute("quoted = '3'"))
        assertEquals("price = \"\$5\"", interpreter.execute("price = \\\$5"))
        assertEquals("copy = \"red keys\"", interpreter.execute("copy = \$thing"))
        assertEquals("Unknown variable \$other", interpreter.execute("x = \$other"))
        assertEquals("Image sources can't be stored yet", interpreter.execute("cam = \$defaultImageSource"))
        assertEquals("Usage: name = value", interpreter.execute("x ="))

        assertEquals(ParameterType.INT, manager.memory.get("count")?.type)
        assertEquals(ParameterType.STRING, manager.memory.get("quoted")?.type)
        assertEquals("copy = \"red keys\". count = 3. loud = true. price = \"\$5\". quoted = \"3\". ratio = 0.5. thing = \"red keys\"", interpreter.execute("vars"))
        }

    @Test
    fun unsetsVariables() = runTest {
        val interpreter=interpreter()
        interpreter.execute("count = 3")

        assertEquals("Removed count. No variable other", interpreter.execute("unset count \$other"))
        assertEquals("No variables", interpreter.execute("vars"))
        assertEquals("Usage: unset name...", interpreter.execute("unset"))
        }

    @Test
    fun configsTakeVariables() = runTest {
        val interpreter=interpreter()
        interpreter.execute("thing = red keys")

        interpreter.execute("find \$thing")
        assertEquals("red keys", manager.list().last().arguments["object"])
        interpreter.execute("killall")
        }
    }
