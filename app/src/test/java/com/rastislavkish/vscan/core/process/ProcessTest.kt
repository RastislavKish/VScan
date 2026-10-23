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

import java.io.Closeable

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.config.CaptureMode
import com.rastislavkish.vscan.core.config.Lifetime
import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.config.ToastOperation
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.openai.Usage
import com.rastislavkish.vscan.core.script.ScriptScope

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessTest {

    // Returns the given outputs in turn, a null one failing, and records when it ran
    private class ScriptedAnalyzer(
        private val outputs: List<String?>,
        private val clock: () -> Long,
        private val tokens: Int=0,
        ): AnalyzerRuntime {

        val inputs=mutableListOf<AnalysisInput>()
        val times=mutableListOf<Long>()

        override suspend fun analyze(input: AnalysisInput): AnalysisResult {
            inputs.add(input)
            times.add(clock())
            val output=outputs[minOf(inputs.size, outputs.size)-1]
            ?: throw ProcessException("Failed")

            return AnalysisResult(output, output!=input.lastOutput, Usage(totalTokens=tokens))
            }
        }

    // Records the variables each executed operation saw
    private class RecordingExecutor: OperationExecutor {

        val executed=mutableListOf<Map<String, Any?>>()
        var released=false

        override suspend fun execute(operation: Operation, scope: ScriptScope, process: Process) {
            if (operation is ToastOperation && operation.text=="fail")
            throw ProcessException("Operation failed")

            executed.add(mapOf(
                "lastOutput" to scope.evaluate("lastOutput"),
                "rawOutput" to scope.evaluate("rawOutput"),
                "changed" to scope.evaluate("changed"),
                "iteration" to scope.evaluate("iteration"),
                ))
            }

        override fun release(process: Process) {
            released=true
            }
        }

    private class RecordingListener: ProcessListener {

        val errors=mutableListOf<String>()
        var ended=false

        override fun onError(process: Process, message: String) {
            errors.add(message)
            }
        override fun onEnd(process: Process) {
            ended=true
            }
        }

    private class CountingSource: ImageSource {

        val image=ImageData.fromEncoded(byteArrayOf(1, 2, 3))
        var photos=0
        var frames=0
        var flashes=0
        var openStreams=0
        var torches=0

        override suspend fun capture(flashlight: Boolean): ImageData {
            photos++
            if (flashlight)
            flashes++
            return image
            }
        override suspend fun getVideoFrame(): ImageData {
            frames++
            return image
            }
        override fun openStream(): Closeable {
            openStreams++
            return Closeable { openStreams-- }
            }
        override fun requestTorch(): Closeable {
            torches++
            return Closeable { torches-- }
            }
        }

    private val listener=RecordingListener()
    private val executor=RecordingExecutor()

    private fun TestScope.process(
        config: Config,
        analyzer: AnalyzerRuntime,
        arguments: Map<String, Any?> = mapOf(),
        defaultFlashlight: Boolean=false,
        ): Process {
        val process=Process(1, config, arguments, analyzer, executor, listener, defaultFlashlight) { testScheduler.currentTime }
        process.start(this)
        return process
        }

    private fun TestScope.analyzer(vararg outputs: String?, tokens: Int=0) = ScriptedAnalyzer(outputs.toList(), { testScheduler.currentTime }, tokens)

    private fun looping(interval: Double=2.0, maxIterations: Int=0): Config = Config(lifetime=Lifetime(interval=interval, maxIterations=maxIterations))

    @Test
    fun singleShotRunsOnce() = runTest {
        val analyzer=analyzer("A bus")
        val process=process(Config(), analyzer)
        process.join()

        assertEquals(1, analyzer.inputs.size)
        assertEquals(ProcessStatus.FINISHED, process.status)
        assertEquals("A bus", process.lastOutput)
        assertEquals(listOf(mapOf("lastOutput" to "A bus", "rawOutput" to "A bus", "changed" to true, "iteration" to 1.0)), executor.executed)
        assertTrue(listener.ended)
        assertTrue(executor.released)
        }

    @Test
    fun loopsAtTheInterval() = runTest {
        val analyzer=analyzer("A")
        val process=process(looping(maxIterations=3), analyzer)
        process.join()

        assertEquals(listOf(0L, 2000L, 4000L), analyzer.times)
        assertEquals(ProcessStatus.FINISHED, process.status)
        // Ends right after the last iteration
        assertEquals(4000L, testScheduler.currentTime)
        }

    @Test
    fun unchangedOutputsKeepTheLastReportedOne() = runTest {
        val analyzer=analyzer("1", "1", "2")
        val config=looping(maxIterations=3).copy(operations=listOf(ToastOperation(condition="changed")))
        val process=process(config, analyzer)
        process.join()

        assertEquals(listOf("1", "2"), process.history)
        assertEquals(listOf(null, "1", "1"), analyzer.inputs.map { it.lastOutput })
        assertEquals(listOf("1", "2"), executor.executed.map { it["rawOutput"] })
        assertEquals(listOf(1.0, 3.0), executor.executed.map { it["iteration"] })
        }

    @Test
    fun operationConditionsSeeTheProcessVariables() = runTest {
        val analyzer=analyzer("None", "42")
        val config=looping(maxIterations=2).copy(operations=listOf(ToastOperation(condition="changed && lastOutput!='None'")))
        process(config, analyzer).join()

        assertEquals(listOf("42"), executor.executed.map { it["lastOutput"] })
        }

    @Test
    fun repeatedErrorsAreReportedOnce() = runTest {
        val analyzer=analyzer(null, null, null, "OK", null)
        process(looping(maxIterations=5), analyzer).join()

        assertEquals(listOf("Failed", "Failed"), listener.errors)
        }

    @Test
    fun failingOperationsDontStopTheOthers() = runTest {
        val config=Config(operations=listOf(ToastOperation(text="fail"), ToastOperation()))
        process(config, analyzer("A")).join()

        assertEquals(listOf("Operation failed"), listener.errors)
        assertEquals(1, executor.executed.size)
        }

    @Test
    fun failedSingleShotFails() = runTest {
        val process=process(Config(), analyzer(null))
        process.join()

        assertEquals(ProcessStatus.FAILED, process.status)
        assertEquals(listOf("Failed"), listener.errors)
        }

    @Test
    fun killingStopsTheLoop() = runTest {
        val analyzer=analyzer("A")
        val process=process(looping(), analyzer)

        advanceTimeBy(5000)
        process.kill()
        process.join()

        assertEquals(3, analyzer.inputs.size)
        assertEquals(ProcessStatus.KILLED, process.status)
        assertTrue(listener.ended)
        }

    @Test
    fun limitsEndTheProcess() = runTest {
        val byDuration=analyzer("A")
        val durationLimited=process(Config(lifetime=Lifetime(interval=2.0, maxDuration=5.0)), byDuration)
        durationLimited.join()
        assertEquals(3, byDuration.inputs.size)
        assertEquals(ProcessStatus.FINISHED, durationLimited.status)

        val byTokens=analyzer("A", tokens=40)
        process(Config(lifetime=Lifetime(interval=2.0, maxTokens=100)), byTokens).join()
        assertEquals(3, byTokens.inputs.size)

        val byCondition=analyzer("A")
        process(Config(lifetime=Lifetime(interval=2.0, iterationCondition="iteration<=2")), byCondition).join()
        assertEquals(2, byCondition.inputs.size)
        }

    @Test
    fun loopingCapturesFramesFromAnOpenStream() = runTest {
        val source=CountingSource()
        val analyzer=object: AnalyzerRuntime {
            override suspend fun analyze(input: AnalysisInput): AnalysisResult {
                assertEquals(1, source.openStreams)
                assertEquals(1, source.torches)
                // The same source within an iteration gives the same image
                input.capture(source)
                input.capture(source)
                return AnalysisResult("A", true)
                }
            }

        val config=Config(lifetime=Lifetime(interval=1.0, maxIterations=2, flashlight=FlashlightMode.ON))
        process(config, analyzer, mapOf("image" to source)).join()

        assertEquals(2, source.frames)
        assertEquals(0, source.photos)
        assertEquals(0, source.openStreams)
        assertEquals(0, source.torches)
        }

    @Test
    fun singleShotTakesAPhoto() = runTest {
        val source=CountingSource()
        val analyzer=object: AnalyzerRuntime {
            override suspend fun analyze(input: AnalysisInput): AnalysisResult {
                input.capture(source)
                return AnalysisResult("A", true)
                }
            }

        process(Config(), analyzer, mapOf("image" to source), defaultFlashlight=true).join()
        process(Config(lifetime=Lifetime(flashlight=FlashlightMode.OFF, capture=CaptureMode.PHOTO)), analyzer, mapOf("image" to source), defaultFlashlight=true).join()

        assertEquals(2, source.photos)
        assertEquals(1, source.flashes)
        assertEquals(0, source.frames)
        }
    }
