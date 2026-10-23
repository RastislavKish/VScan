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

import com.rastislavkish.vscan.core.Config

// Carries out command prompt lines, returning the message for the user
// The built-in commands are ps, kill and killall, any other name starts the config it's the command or the id of
class CommandInterpreter(
    private val configs: () -> List<Config>,
    private val processManager: ProcessManager,
    ) {

    fun execute(line: String): String {
        try {
            val commandLine=CommandLine.parse(line)
            val command=commandLine.command ?: return ""
            val arguments=commandLine.arguments.map { it.text }

            return when (command) {
                "ps" -> ps()
                "kill" -> kill(arguments)
                "killall" -> killAll()
                else -> spawn(command, commandLine)
                }
            }
        catch (e: ProcessException) {
            return e.message ?: "Error"
            }
        }

    private fun ps(): String {
        val processes=processManager.list()
        if (processes.isEmpty())
        return "No processes"

        return processes.joinToString(". ") { "${it.pid} ${it.config.name}, iteration ${it.iteration}" }
        }

    private fun kill(arguments: List<String>): String {
        if (arguments.isEmpty())
        return "Usage: kill pid..."

        return arguments.joinToString(". ") { argument ->
            val pid=argument.toIntOrNull()
            when {
                pid==null -> "$argument is not a process id"
                processManager.kill(pid) -> "Killed $pid"
                else -> "No process $pid"
                }
            }
        }

    private fun killAll(): String {
        val count=processManager.list().size
        processManager.killAll()

        return when (count) {
            0 -> "No processes"
            1 -> "Killed 1 process"
            else -> "Killed $count processes"
            }
        }

    private fun spawn(command: String, commandLine: CommandLine): String {
        val list=configs()
        val config=list.firstOrNull { it.command==command }
        ?: list.firstOrNull { it.id==command }
        ?: return "Unknown command $command"

        val process=processManager.spawn(config, commandLine.bind(config))
        return "Started ${config.name} as ${process.pid}"
        }
    }
