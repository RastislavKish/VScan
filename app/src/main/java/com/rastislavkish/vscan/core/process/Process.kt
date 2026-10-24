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
import com.rastislavkish.vscan.core.config.IterationMode
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.config.LlmChangeDetectionMode
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.SetOperation
import com.rastislavkish.vscan.core.device.DeviceMonitor
import com.rastislavkish.vscan.core.device.NoDeviceMonitor
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
// Each iteration analyzes the input and then commits the result: change detection, the state and the operations
// In the sync mode an iteration starts once the previous one has been committed, in the async mode the analyses overlap
// Either way, iterations are committed one at a time in the order they started, the state being changed only by commits
// The iteration condition holds the iterations while it's false, only the limits and the user end a looping process
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
    private val device: DeviceMonitor=NoDeviceMonitor,
    private val clock: () -> Long,
    ) {

    // The config and its analyzer, replaced together
    private class Program(val config: Config, val analyzer: AnalyzerRuntime) {

        // Whether the device variable is used, so its sensors need to be listened to
        val usesDevice=usesDevice(config)
        }

    // From its start to its commit
    private class Iteration(
        val number: Int,
        val program: Program,
        val input: AnalysisInput,
        val reportAsChange: Boolean,
        // Asked for by the user, so its errors are always reported
        val manual: Boolean,
        // Completed once the iteration started before it has been committed
        val previous: Deferred<Unit>,
        val committed: CompletableDeferred<Unit>,
        )

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
    // The commit of the last started iteration, the next one waiting for it
    private var lastCommit: Deferred<Unit> = CompletableDeferred(Unit)
    // Errors of the previous and the current commit
    private var previousErrors=setOf<String>()
    private var currentErrors=mutableSetOf<String>()
    // Failed iterations in a row, for the error interval
    @Volatile private var failures=0
    // The last reported error of the iteration condition, reported again only when it changes
    private var conditionError: String?=null
    // Held while the own iterations run and the config uses the device variable
    private var deviceHandle: Closeable?=null

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
    // It's committed after the own iterations started before it, and the iteration condition doesn't hold it
    // reportAsChange makes the operations see the result as a change even when the output stayed the same, lastOutput and history still changing only with the output
    // onCapture is called with the image taken for the image parameter
    suspend fun scan(reportAsChange: Boolean, onCapture: (ImageData) -> Unit = {}): Boolean {
        val handle=if (program.usesDevice) device.listen() else null
        try {
            if (handle!=null)
            device.awaitReadings()

            return runIteration(startIteration(CaptureMode.PHOTO, reportAsChange, true, onCapture))
            }
        finally {
            handle?.close()
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
            deviceHandle?.close()
            deviceHandle=null

            status=if (foreground) ProcessStatus.RUNNING else endStatus
            executor.release(this)
            listener.onEnd(this)
            }
        }

    // Returns false when a single-shot process failed
    private suspend fun iterate(captureMode: CaptureMode): Boolean = coroutineScope {
        val inFlight=Semaphore(if (lifetime.mode==IterationMode.ASYNC) maxOf(1, lifetime.maxInFlight) else 1)
        var lastStart: Long?=null

        while (true) {
            // Released once the iteration is committed
            inFlight.acquire()
            if (!awaitStart(lastStart)) {
                inFlight.release()
                break
                }

            lastStart=clock()
            val iteration=startIteration(captureMode, false, false)

            if (!lifetime.isLooping) {
                inFlight.release()
                return@coroutineScope runIteration(iteration)
                }

            val job=launch { runIteration(iteration) }
            // Also when it's cancelled before it starts, so nothing waits for its commit forever
            job.invokeOnCompletion {
                iteration.committed.complete(Unit)
                inFlight.release()
                }
            }

        // The iterations still running are committed before the process ends
        true
        }

    // Waits for the interval since the last start and for the iteration condition, returning false when a limit is reached
    private suspend fun awaitStart(lastStart: Long?): Boolean {
        while (true) {
            updateDeviceHandle()
            if (limitReached())
            return false

            val remaining=if (lastStart==null) 0L else lastStart+(lifetime.intervalAfter(failures)*1000).toLong()-clock()
            if (remaining<=0L && conditionHolds())
            return true

            delay(if (remaining>0L) minOf(remaining, CONDITION_CHECK_INTERVAL) else CONDITION_CHECK_INTERVAL)
            }
        }

    // Listens to the device while the own iterations run and the config uses it, which an edit may change
    private suspend fun updateDeviceHandle() {
        if (program.usesDevice && deviceHandle==null) {
            deviceHandle=device.listen()
            device.awaitReadings()
            }
        else if (!program.usesDevice && deviceHandle!=null) {
            deviceHandle?.close()
            deviceHandle=null
            }
        }

    private fun limitReached(): Boolean {
        if (lifetime.maxIterations>0 && iteration>=lifetime.maxIterations)
        return true
        if (lifetime.maxTokens>0 && usage.totalTokens>=lifetime.maxTokens)
        return true

        return false
        }

    // A failing condition counts as false
    private fun conditionHolds(): Boolean {
        val condition=lifetime.iterationCondition ?: return true

        // Seen as before the next iteration
        val variables=variables()+mapOf("iteration" to (iteration+1).toDouble())
        try {
            val holds=ScriptEngine.newScope(variables).evaluateBoolean(condition)
            conditionError=null
            return holds
            }
        catch (e: Exception) {
            val message="Error in the iteration condition: ${e.message}"
            if (message!=conditionError)
            listener.onError(this, message)
            conditionError=message
            return false
            }
        }

    private fun startIteration(captureMode: CaptureMode, reportAsChange: Boolean, manual: Boolean, onCapture: (ImageData) -> Unit = {}): Iteration {
        synchronized(this) {
            iteration++
            val program=program
            val previous=lastCommit
            val committed=CompletableDeferred<Unit>()
            lastCommit=committed

            val imageArgument=arguments[Parameter.IMAGE_PARAMETER_NAME]
            val captures=IdentityHashMap<ImageSource, ImageData>()
            val input=AnalysisInput(variables()) { source ->
                captures.getOrPut(source) {
                    capture(source, captureMode).also { image ->
                        if (source===imageArgument) {
                            onCapture(image)
                            listener.onCapture(this, image)
                            }
                        }
                    }
                }

            return Iteration(iteration, program, input, reportAsChange, manual, previous, committed)
            }
        }

    // Analyzes, waits for the previous iteration's commit and commits, returning whether the iteration succeeded
    private suspend fun runIteration(iteration: Iteration): Boolean {
        try {
            var error: String?=null
            val result=try {
                analyze(iteration)
                }
            catch (e: CancellationException) {
                throw e
                }
            catch (e: Exception) {
                error=e.message ?: "Analysis failed"
                null
                }

            iteration.previous.await()

            if (iteration.manual)
            previousErrors=setOf()
            if (result!=null)
            commit(iteration, result)
            else
            reportError(error ?: "Analysis failed")
            previousErrors=currentErrors
            currentErrors=mutableSetOf()

            failures=if (result!=null) 0 else failures+1
            return result!=null
            }
        finally {
            iteration.committed.complete(Unit)
            }
        }

    private suspend fun analyze(iteration: Iteration): AnalysisResult {
        val analyzer=iteration.program.analyzer
        val timeout=iteration.program.config.lifetime.analysisTimeout
        if (timeout<=0.0)
        return analyzer.analyze(iteration.input)

        return withTimeoutOrNull((timeout*1000).toLong()) { analyzer.analyze(iteration.input) }
        ?: throw ProcessException("Analysis timed out after ${formatSeconds(timeout)} s")
        }

    private suspend fun commit(iteration: Iteration, result: AnalysisResult) {
        val program=iteration.program
        val changed=result.isChange(lastOutput)

        usage=usage+result.usage
        lastConversation=result.conversation
        if (changed) {
            lastOutput=result.output
            history=(history+result.output).takeLast(MAX_HISTORY)
            }
        val scope=ScriptEngine.newScope(variables()+mapOf(
            "iteration" to iteration.number.toDouble(),
            "rawOutput" to result.output,
            "changed" to (changed || iteration.reportAsChange),
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
        val variables=arguments+memory+mapOf(
            "lastOutput" to lastOutput,
            "history" to history,
            "iteration" to iteration.toDouble(),
            "elapsed" to elapsedSeconds,
            )

        return if (program.usesDevice) variables+("device" to device.state()) else variables
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

    // A looping process failing the same way in consecutive commits reports it once
    private fun reportError(message: String) {
        val repeated=message in previousErrors
        currentErrors.add(message)

        if (!repeated)
        listener.onError(this, message)
        }

    companion object {

        const val MAX_HISTORY=20
        // Milliseconds between checks of a false iteration condition
        private const val CONDITION_CHECK_INTERVAL=250L

        // Names of the process variables, which parameters and the memory can't take
        val reservedNames=setOf("lastOutput", "rawOutput", "changed", "history", "iteration", "elapsed", "device")

        private val deviceReference=Regex("\\bdevice\\.[A-Za-z]")

        // Throws ProcessException when the config can't run
        fun validate(config: Config) {
            validateMemory(config)
            validateLifetime(config)
            }

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

        fun validateLifetime(config: Config) {
            val lifetime=config.lifetime
            if (lifetime.maxInFlight<1)
            throw ProcessException("max_in_flight must be at least 1")

            val analyzer=config.analyzer
            if (lifetime.mode==IterationMode.ASYNC && analyzer is LlmAnalyzer && analyzer.changeDetection.mode==LlmChangeDetectionMode.LLM_DRIVEN)
            throw ProcessException("The async mode can't be used with llm_driven change detection, the model wouldn't see the latest output")
            }

        // Whether any text of the config refers to the device variable
        private fun usesDevice(config: Config): Boolean = deviceReference.containsMatchIn(Json.encodeToString(Config.serializer(), config))

        private fun formatSeconds(seconds: Double): String = if (seconds%1.0==0.0) seconds.toLong().toString() else seconds.toString()

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
