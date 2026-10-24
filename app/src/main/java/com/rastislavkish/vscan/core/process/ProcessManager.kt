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

import android.content.Context
import android.os.SystemClock
import android.widget.Toast

import kotlinx.coroutines.*

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.ProvidersManager
import com.rastislavkish.vscan.core.Settings
import com.rastislavkish.vscan.core.config.LlmAnalyzer
import com.rastislavkish.vscan.core.device.AndroidDeviceMonitor
import com.rastislavkish.vscan.core.device.DeviceMonitor
import com.rastislavkish.vscan.core.device.NoDeviceMonitor
import com.rastislavkish.vscan.core.files.VScanFiles
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSources

// Spawns configs as processes and keeps track of the running ones, and of the foreground process the scan screen works with
// Thread-safe
class ProcessManager(
    private val arguments: Arguments,
    private val createAnalyzer: (Config) -> AnalyzerRuntime,
    private val executor: OperationExecutor,
    // Notified of errors and ends of all processes
    private val listener: ProcessListener,
    private val defaultFlashlight: () -> Boolean,
    private val device: DeviceMonitor=NoDeviceMonitor,
    private val scope: CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.Default),
    private val clock: () -> Long=SystemClock::elapsedRealtime,
    // Set by the foreground process' captures, the one the global memory gives as $lastImage
    val lastImage: LastImage=LastImage(),
    ): ProcessListener {

    val memory: GlobalMemory
    get() = arguments.memory

    private val processes=linkedMapOf<Int, Process>()
    private var nextPid=1

    @Volatile var foreground: Process?=null
    private set

    // Called on any thread whenever a process starts or ends
    @Volatile var onChange: (() -> Unit)?=null

    // Throws ProcessException when the config can't be started with the given arguments
    fun spawn(config: Config, given: Map<String, String> = mapOf()): Process {
        val process=create(config, given)
        process.start(scope)
        onChange?.invoke()
        return process
        }

    // Replaces the foreground process with a new one waiting for scans, the old one's state being discarded
    // Throws ProcessException when the config can't be started with the given arguments, the old process staying then
    fun setForeground(config: Config, given: Map<String, String> = mapOf()): Process {
        val process=create(config, given, true)
        replaceForeground(process)
        return process
        }

    // Edits the foreground process' config, from its next iteration
    // An invalid config is kept, its iterations failing, so the user can go on editing it
    fun updateForeground(config: Config) {
        val process=foreground ?: return
        val analyzer=try {
            analyzer(config)
            }
        catch (e: ProcessException) {
            FailingAnalyzer(e.message ?: "Invalid config")
            }

        process.update(config, analyzer)
        }

    // Starts and stops the foreground process' own iterations
    fun startForeground() {
        foreground?.start(scope) ?: return
        onChange?.invoke()
        }
    fun stopForeground() {
        foreground?.kill()
        }

    // Moves the foreground process to the background, where it runs on its own, and puts a fresh process of its config in its place
    // Returns the moved process, or null without a foreground process
    fun background(): Process? {
        val process=foreground ?: return null
        val replacement=create(process.config, process.given, true)

        synchronized(processes) {
            process.foreground=false
            foreground=replacement
            }
        process.start(scope)
        onChange?.invoke()
        return process
        }

    // Brings a running background process to the foreground, where it keeps iterating until stopped
    // Returns null when there's no such process
    fun bringToForeground(pid: Int): Process? {
        val process=get(pid)?.takeIf { !it.foreground && it.isRunning } ?: return null
        process.foreground=true
        replaceForeground(process)
        return process
        }

    fun get(pid: Int): Process? = synchronized(processes) { processes[pid] }

    // The running processes, oldest first, the foreground one only while it iterates on its own
    fun list(): List<Process> = synchronized(processes) {
        processes.values.filter { if (it.foreground) it.isIterating else it.isRunning }
        }

    fun kill(pid: Int): Boolean {
        val process=get(pid)?.takeIf { it in list() } ?: return false
        process.kill()
        return true
        }
    fun killAll() {
        for (process in list())
        process.kill()
        }

    private fun create(config: Config, given: Map<String, String>, foreground: Boolean=false): Process {
        val values=arguments.resolve(config, given)
        val analyzer=analyzer(config)

        return synchronized(processes) {
            val process=Process(nextPid++, config, values, analyzer, executor, this, defaultFlashlight(), given, device, clock)
            process.foreground=foreground
            processes[process.pid]=process
            process
            }
        }

    private fun analyzer(config: Config): AnalyzerRuntime {
        Process.validate(config)
        try {
            return createAnalyzer(config)
            }
        catch (e: ProcessException) {
            throw e
            }
        catch (e: Exception) {
            // E.g. template syntax errors
            throw ProcessException(e.message ?: "Invalid config")
            }
        }

    private fun replaceForeground(process: Process) {
        val old=synchronized(processes) {
            val old=foreground
            foreground=process

            old?.foreground=false
            // An iterating one is removed when it ends
            if (old!=null && !old.isIterating)
            processes.remove(old.pid)
            old
            }

        old?.kill()
        onChange?.invoke()
        }

    override fun onError(process: Process, message: String) {
        listener.onError(process, message)
        }
    override fun onEnd(process: Process) {
        synchronized(processes) {
            // The foreground process stays, waiting for scans
            if (!process.foreground)
            processes.remove(process.pid)
            }
        listener.onEnd(process)
        onChange?.invoke()
        }
    override fun onCapture(process: Process, image: ImageData) {
        if (process.foreground)
        lastImage.set(image)
        listener.onCapture(process, image)
        }

    companion object {

        private var instance: ProcessManager?=null

        @Synchronized
        fun getInstance(context: Context): ProcessManager {
            if (instance==null) {
                val appContext=context.applicationContext
                val providersManager=ProvidersManager.getInstance(appContext)
                val settings=Settings.getInstance(appContext)

                val lastImage=LastImage()

                instance=ProcessManager(
                    Arguments(GlobalMemory(ImageSources.getInstance(appContext).default, lastImage, VScanFiles.getInstance(appContext))),
                    { config ->
                        when (val analyzer=config.analyzer) {
                            is LlmAnalyzer -> LlmAnalyzerRuntime(analyzer, ProviderLlmBackend(providersManager))
                            }
                        },
                    AndroidOperationExecutor(appContext),
                    ToastingListener(appContext),
                    { settings.useFlashlight },
                    AndroidDeviceMonitor(appContext),
                    lastImage=lastImage,
                    )
                }

            return instance!!
            }
        }
    }

// Kept in place of the analyzer of an invalid config
private class FailingAnalyzer(
    private val message: String,
    ): AnalyzerRuntime {

    override suspend fun analyze(input: AnalysisInput): AnalysisResult = throw ProcessException(message)
    }

// Shows process errors as toasts
private class ToastingListener(
    private val context: Context,
    ): ProcessListener {

    override fun onError(process: Process, message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    override fun onEnd(process: Process) {}
    }
