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
import java.util.IdentityHashMap

import kotlinx.coroutines.*

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.config.CaptureMode
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.Usage
import com.rastislavkish.vscan.core.script.ScriptEngine

enum class ProcessStatus {
    RUNNING,
    // Ran all its iterations or reached a limit
    FINISHED,
    KILLED,
    // A single-shot process whose iteration failed
    FAILED,
    }

interface ProcessListener {

    fun onError(process: Process, message: String)
    fun onEnd(process: Process)
    }

// A running config
// Iterations run one after another, each analyzing the input and running the operations on the result
// The state is changed only by the process' own coroutine, other threads may read it
class Process(
    val pid: Int,
    val config: Config,
    // The values of all the config's parameters, lenses already applied
    val arguments: Map<String, Any?>,
    private val analyzer: AnalyzerRuntime,
    private val executor: OperationExecutor,
    private val listener: ProcessListener,
    // The global flashlight setting, for the default flashlight mode
    private val defaultFlashlight: Boolean,
    private val clock: () -> Long,
    ) {

    @Volatile var status=ProcessStatus.RUNNING
    private set

    // The last reported output
    @Volatile var lastOutput: String?=null
    private set

    @Volatile var history: List<String> = listOf()
    private set

    // The number of started iterations
    @Volatile var iteration=0
    private set

    @Volatile var usage=Usage()
    private set

    @Volatile var lastConversation: Conversation?=null
    private set

    private var startTime=0L
    private var job: Job?=null
    // Errors of the previous and the current iteration
    private var previousErrors=setOf<String>()
    private var currentErrors=mutableSetOf<String>()

    val elapsedSeconds: Double
    get() = (clock()-startTime)/1000.0

    private val lifetime=config.lifetime

    private val captureMode=when (lifetime.capture) {
        CaptureMode.AUTO -> if (lifetime.isLooping) CaptureMode.FRAME else CaptureMode.PHOTO
        else -> lifetime.capture
        }
    private val useFlashlight=when (lifetime.flashlight) {
        FlashlightMode.DEFAULT -> defaultFlashlight
        FlashlightMode.ON -> true
        FlashlightMode.OFF -> false
        }

    fun start(scope: CoroutineScope) {
        startTime=clock()
        job=scope.launch { run() }
        }

    fun kill() {
        job?.cancel()
        }

    suspend fun join() {
        job?.join()
        }

    private suspend fun run() {
        // Frames are captured from a running stream, with the torch on for the whole time, rather than starting them for every iteration
        val handles=mutableListOf<Closeable>()
        var endStatus=ProcessStatus.FINISHED

        try {
            if (captureMode==CaptureMode.FRAME) {
                for (source in imageSourceArguments()) {
                    handles.add(source.openStream())
                    if (useFlashlight)
                    handles.add(source.requestTorch())
                    }
                }

            val completed=if (lifetime.maxDuration>0.0)
            withTimeoutOrNull((lifetime.maxDuration*1000).toLong()) { iterate() } ?: true
            else
            iterate()

            if (!completed)
            endStatus=ProcessStatus.FAILED
            }
        catch (e: CancellationException) {
            endStatus=ProcessStatus.KILLED
            throw e
            }
        finally {
            for (handle in handles)
            handle.close()

            status=endStatus
            executor.release(this)
            listener.onEnd(this)
            }
        }

    // Returns false when a single-shot process failed
    private suspend fun iterate(): Boolean {
        while (true) {
            if (limitReached())
            return true

            val iterationStart=clock()
            val succeeded=runIteration()
            previousErrors=currentErrors
            currentErrors=mutableSetOf()

            if (!lifetime.isLooping)
            return succeeded
            if (lifetime.maxIterations>0 && iteration>=lifetime.maxIterations)
            return true

            val nextStart=iterationStart+(lifetime.interval*1000).toLong()
            delay(maxOf(0L, nextStart-clock()))
            }
        }

    private fun limitReached(): Boolean {
        if (lifetime.maxIterations>0 && iteration>=lifetime.maxIterations)
        return true
        if (lifetime.maxTokens>0 && usage.totalTokens>=lifetime.maxTokens)
        return true

        val condition=lifetime.iterationCondition
        if (condition!=null) {
            // Seen as before the next iteration
            val variables=variables()+mapOf("iteration" to (iteration+1).toDouble())
            return !evaluateOrReport("iteration condition") {
                ScriptEngine.newScope(variables).evaluateBoolean(condition)
                }
            }

        return false
        }

    private suspend fun runIteration(): Boolean {
        iteration++

        val captures=IdentityHashMap<ImageSource, ImageData>()
        val input=AnalysisInput(variables(), lastOutput) { source ->
            captures.getOrPut(source) { capture(source) }
            }

        val result=try {
            analyzer.analyze(input)
            }
        catch (e: CancellationException) {
            throw e
            }
        catch (e: Exception) {
            reportError(e.message ?: "Analysis failed")
            return false
            }

        usage=usage+result.usage
        lastConversation=result.conversation
        if (result.changed) {
            lastOutput=result.output
            history=(history+result.output).takeLast(MAX_HISTORY)
            }
        val scope=ScriptEngine.newScope(variables()+mapOf(
            "rawOutput" to result.output,
            "changed" to result.changed,
            ))

        // A failing operation doesn't keep the others from running
        for (operation in config.operations) {
            val condition=operation.condition
            if (condition!=null && !evaluateOrReport("operation condition") { scope.evaluateBoolean(condition) })
            continue

            try {
                executor.execute(operation, scope, this)
                }
            catch (e: CancellationException) {
                throw e
                }
            catch (e: Exception) {
                reportError(e.message ?: "Operation failed")
                }
            }

        return true
        }

    private suspend fun capture(source: ImageSource): ImageData {
        return when (captureMode) {
            CaptureMode.FRAME -> source.getVideoFrame()
            else -> source.capture(useFlashlight)
            }
        }

    private fun variables(): Map<String, Any?> {
        return arguments+mapOf(
            "lastOutput" to lastOutput,
            "history" to history,
            "iteration" to iteration.toDouble(),
            "elapsed" to elapsedSeconds,
            )
        }

    private fun imageSourceArguments(): List<ImageSource> {
        return arguments.values.filterIsInstance<ImageSource>().distinct()
        }

    // A failing expression counts as false
    private inline fun evaluateOrReport(what: String, block: () -> Boolean): Boolean {
        try {
            return block()
            }
        catch (e: Exception) {
            reportError("Error in the $what: ${e.message}")
            return false
            }
        }

    // A looping process failing the same way in consecutive iterations reports it once
    private fun reportError(message: String) {
        val repeated=message in previousErrors
        currentErrors.add(message)

        if (!repeated)
        listener.onError(this, message)
        }

    companion object {

        const val MAX_HISTORY=20

        // Names of the process variables, which parameters can't take
        val reservedNames=setOf("lastOutput", "rawOutput", "changed", "history", "iteration", "elapsed")
        }
    }
