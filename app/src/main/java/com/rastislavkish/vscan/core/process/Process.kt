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
import kotlinx.coroutines.sync.*

import kotlinx.serialization.json.*

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.FlashlightMode
import com.rastislavkish.vscan.core.config.CaptureMode
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.SetOperation
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.openai.Conversation
import com.rastislavkish.vscan.core.openai.Usage
import com.rastislavkish.vscan.core.script.ScriptEngine
import com.rastislavkish.vscan.core.script.ScriptScope

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
    // An image taken for the image parameter
    fun onCapture(process: Process, image: ImageData) {}
    }

// A running config
// Iterations run one after another, each analyzing the input and running the operations on the result
// The state is changed only by the running iteration, other threads may read it
// The foreground process is the one the scan screen works with: it runs iterations on demand, loops only when started, and stays when its iterations end
class Process(
    val pid: Int,
    config: Config,
    // The values of all the config's parameters, lenses already applied
    val arguments: Map<String, Any?>,
    analyzer: AnalyzerRuntime,
    private val executor: OperationExecutor,
    private val listener: ProcessListener,
    // The global flashlight setting, for the default flashlight mode
    private val defaultFlashlight: Boolean,
    // The argument texts it was called with
    val given: Map<String, String> = mapOf(),
    private val clock: () -> Long,
    ) {

    // The config and its analyzer, replaced together
    private class Program(val config: Config, val analyzer: AnalyzerRuntime)

    @Volatile private var program=Program(config, analyzer)

    val config: Config
    get() = program.config

    @Volatile var foreground=false

    @Volatile var status=ProcessStatus.RUNNING
    private set

    // The last reported output
    @Volatile var lastOutput: String?=null
    private set

    @Volatile var history: List<String> = listOf()
    private set

    // The process memory, by the config's declarations, changed by set operations
    @Volatile var memory: Map<String, Any?> = initialMemory(config, mapOf())
    private set

    // The number of started iterations
    @Volatile var iteration=0
    private set

    @Volatile var usage=Usage()
    private set

    // The user may continue the conversation, or start one before the first iteration
    @Volatile var lastConversation: Conversation?=null

    private val startTime=clock()
    @Volatile private var job: Job?=null
    // Iterations on demand and the own ones take turns
    private val iterationMutex=Mutex()
    // Errors of the previous and the current iteration
    private var previousErrors=setOf<String>()
    private var currentErrors=mutableSetOf<String>()

    val elapsedSeconds: Double
    get() = (clock()-startTime)/1000.0

    private val lifetime
    get() = config.lifetime

    private val captureMode
    get() = when (lifetime.capture) {
        CaptureMode.AUTO -> if (lifetime.isLooping) CaptureMode.FRAME else CaptureMode.PHOTO
        else -> lifetime.capture
        }
    private val useFlashlight
    get() = when (lifetime.flashlight) {
        FlashlightMode.DEFAULT -> defaultFlashlight
        FlashlightMode.ON -> true
        FlashlightMode.OFF -> false
        }

    // Starts the iterations, unless they already run
    fun start(scope: CoroutineScope) {
        synchronized(this) {
            if (job?.isActive==true)
            return
            job=scope.launch { run() }
            }
        }

    // Ends a background process, the foreground one only stops iterating
    fun kill() {
        job?.cancel()
        }

    // Takes effect from the next iteration
    // The variables kept in the memory keep their values, new ones start with theirs
    fun update(config: Config, analyzer: AnalyzerRuntime) {
        synchronized(this) {
            memory=initialMemory(config, memory)
            program=Program(config, analyzer)
            }
        }

    // False from the moment it's killed, even before it has stopped
    val isRunning: Boolean
    get() = job?.isActive ?: true

    // Whether its own iterations run, false for a foreground process waiting for scans
    val isIterating: Boolean
    get() = job?.isActive==true

    // Runs one iteration now, taking photos, and returns whether it succeeded
    // reportAsChange makes the operations see the result as a change even when the output stayed the same, lastOutput and history still changing only with the output
    // onCapture is called with the image taken for the image parameter
    suspend fun scan(reportAsChange: Boolean, onCapture: (ImageData) -> Unit = {}): Boolean {
        return iterationMutex.withLock {
            // Asked for, so its errors are always reported
            previousErrors=setOf()
            val succeeded=runIteration(CaptureMode.PHOTO, reportAsChange, onCapture)
            previousErrors=currentErrors
            currentErrors=mutableSetOf()
            succeeded
            }
        }

    suspend fun join() {
        job?.join()
        }

    private suspend fun run() {
        // Frames are captured from a running stream, with the torch on for the whole time, rather than starting them for every iteration
        val handles=mutableListOf<Closeable>()
        var endStatus=ProcessStatus.FINISHED
        val captureMode=captureMode

        try {
            if (captureMode==CaptureMode.FRAME) {
                for (source in imageSourceArguments()) {
                    handles.add(source.openStream())
                    if (useFlashlight)
                    handles.add(source.requestTorch())
                    }
                }

            val completed=if (lifetime.maxDuration>0.0)
            withTimeoutOrNull((lifetime.maxDuration*1000).toLong()) { iterate(captureMode) } ?: true
            else
            iterate(captureMode)

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

            status=if (foreground) ProcessStatus.RUNNING else endStatus
            executor.release(this)
            listener.onEnd(this)
            }
        }

    // Returns false when a single-shot process failed
    private suspend fun iterate(captureMode: CaptureMode): Boolean {
        while (true) {
            val iterationStart=clock()
            val succeeded=iterationMutex.withLock {
                if (limitReached())
                return true

                val succeeded=runIteration(captureMode, false)
                previousErrors=currentErrors
                currentErrors=mutableSetOf()
                succeeded
                }

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

    private suspend fun runIteration(captureMode: CaptureMode, reportAsChange: Boolean, onCapture: (ImageData) -> Unit = {}): Boolean {
        iteration++
        val program=program

        val imageArgument=arguments[Parameter.IMAGE_PARAMETER_NAME]
        val captures=IdentityHashMap<ImageSource, ImageData>()
        val input=AnalysisInput(variables(), lastOutput) { source ->
            captures.getOrPut(source) {
                capture(source, captureMode).also { image ->
                    if (source===imageArgument) {
                        onCapture(image)
                        listener.onCapture(this, image)
                        }
                    }
                }
            }

        val result=try {
            program.analyzer.analyze(input)
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
            "changed" to (result.changed || reportAsChange),
            ))

        // A failing operation doesn't keep the others from running
        for (operation in program.config.operations) {
            val condition=operation.condition
            if (condition!=null && !evaluateOrReport("operation condition") { scope.evaluateBoolean(condition) })
            continue

            try {
                if (operation is SetOperation)
                set(operation, scope)
                else
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

    private fun set(operation: SetOperation, scope: ScriptScope) {
        val value=try {
            scope.evaluate(operation.value)
            }
        catch (e: Exception) {
            throw ProcessException("Error in the value of ${operation.name}: ${e.message}")
            }

        synchronized(this) {
            // Unless the config was edited meanwhile
            if (operation.name in memory)
            memory=memory+(operation.name to value)
            }
        scope.set(operation.name, value)
        }

    private suspend fun capture(source: ImageSource, captureMode: CaptureMode): ImageData {
        return when (captureMode) {
            CaptureMode.FRAME -> source.getVideoFrame()
            else -> source.capture(useFlashlight)
            }
        }

    private fun variables(): Map<String, Any?> {
        return arguments+memory+mapOf(
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

        // Names of the process variables, which parameters and the memory can't take
        val reservedNames=setOf("lastOutput", "rawOutput", "changed", "history", "iteration", "elapsed")

        // Throws ProcessException when the memory declarations or the set operations don't fit
        fun validateMemory(config: Config) {
            val parameterNames=config.allParameters.map { it.name }.toSet()
            for (name in config.memory.keys) {
                if (!GlobalMemory.isValidName(name))
                throw ProcessException("$name is not a valid memory variable name, use letters, digits and underscores, starting with a letter")
                if (name in reservedNames)
                throw ProcessException("$name is a process variable, it can't be a memory variable name")
                if (name in parameterNames)
                throw ProcessException("$name is both a parameter and a memory variable")
                }

            for (operation in config.operations) {
                if (operation is SetOperation && operation.name !in config.memory)
                throw ProcessException("The set operation changes ${operation.name}, which is not declared in the memory")
                }
            }

        // The values of the memory variables declared in the config, taken from the previous memory where it has them
        private fun initialMemory(config: Config, previous: Map<String, Any?>): Map<String, Any?> {
            return config.memory.mapValues { (name, value) ->
                if (name in previous)
                previous[name]
                else if (value.isString)
                value.content
                else
                value.booleanOrNull ?: value.double
                }
            }
        }
    }
