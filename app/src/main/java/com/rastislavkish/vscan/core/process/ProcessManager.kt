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
import com.rastislavkish.vscan.core.imagesource.ImageSources

// Spawns configs as processes and keeps track of the running ones
// Thread-safe
class ProcessManager(
    private val arguments: Arguments,
    private val createAnalyzer: (Config) -> AnalyzerRuntime,
    private val executor: OperationExecutor,
    // Notified of errors and ends of all processes
    private val listener: ProcessListener,
    private val defaultFlashlight: () -> Boolean,
    private val scope: CoroutineScope=CoroutineScope(SupervisorJob()+Dispatchers.Default),
    private val clock: () -> Long=SystemClock::elapsedRealtime,
    ): ProcessListener {

    private val processes=linkedMapOf<Int, Process>()
    private var nextPid=1

    // Called on any thread whenever a process starts or ends
    @Volatile var onChange: (() -> Unit)?=null

    // Throws ProcessException when the config can't be started with the given arguments
    fun spawn(config: Config, given: Map<String, String> = mapOf()): Process {
        val values=arguments.resolve(config, given)
        val analyzer=try {
            createAnalyzer(config)
            }
        catch (e: ProcessException) {
            throw e
            }
        catch (e: Exception) {
            // E.g. template syntax errors
            throw ProcessException(e.message ?: "Invalid config")
            }

        val process=synchronized(processes) {
            val process=Process(nextPid++, config, values, analyzer, executor, this, defaultFlashlight(), clock)
            processes[process.pid]=process
            process
            }

        process.start(scope)
        onChange?.invoke()
        return process
        }

    fun get(pid: Int): Process? = synchronized(processes) { processes[pid] }

    // The running processes, oldest first
    fun list(): List<Process> = synchronized(processes) { processes.values.filter { it.isRunning } }

    fun kill(pid: Int): Boolean {
        val process=get(pid)?.takeIf { it.isRunning } ?: return false
        process.kill()
        return true
        }
    fun killAll() {
        for (process in list())
        process.kill()
        }

    override fun onError(process: Process, message: String) {
        listener.onError(process, message)
        }
    override fun onEnd(process: Process) {
        synchronized(processes) {
            processes.remove(process.pid)
            }
        listener.onEnd(process)
        onChange?.invoke()
        }

    companion object {

        private var instance: ProcessManager?=null

        @Synchronized
        fun getInstance(context: Context): ProcessManager {
            if (instance==null) {
                val appContext=context.applicationContext
                val providersManager=ProvidersManager.getInstance(appContext)
                val settings=Settings.getInstance(appContext)

                instance=ProcessManager(
                    Arguments(ImageSources.getInstance(appContext).default),
                    { config ->
                        when (val analyzer=config.analyzer) {
                            is LlmAnalyzer -> LlmAnalyzerRuntime(analyzer, ProviderLlmBackend(providersManager))
                            }
                        },
                    AndroidOperationExecutor(appContext),
                    ToastingListener(appContext),
                    { settings.useFlashlight },
                    )
                }

            return instance!!
            }
        }
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
