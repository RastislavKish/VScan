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
// The built-in commands are ps, kill, killall, fg, bg and reload, any other name starts the config it's the command or the id of
class CommandInterpreter(
    private val configs: () -> List<Config>,
    private val processManager: ProcessManager,
    // Brings in the changes of the linked folder, returning the message
    private val reload: () -> String = { "Nothing to reload" },
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
                "fg" -> foreground(commandLine.shifted())
                "bg" -> background()
                "reload" -> reload()
                else -> spawn(commandLine)
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

        return processes.joinToString(". ") {
            val foreground=if (it.foreground) ", foreground" else ""
            "${it.pid} ${it.config.name}, iteration ${it.iteration}$foreground"
            }
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

    private fun spawn(commandLine: CommandLine): String {
        val config=findConfig(commandLine)
        val process=processManager.spawn(config, commandLine.bind(config))
        return "Started ${config.name} as ${process.pid}"
        }

    // fg pid brings a background process to the foreground, fg command... loads a config there
    // Config ids are never numbers, so the two can't be confused
    private fun foreground(commandLine: CommandLine): String {
        val command=commandLine.command ?: return "Usage: fg pid, or fg command..."

        val pid=command.toIntOrNull()
        if (pid!=null && commandLine.arguments.isEmpty()) {
            val process=processManager.bringToForeground(pid) ?: return "No process $pid"
            return "${process.config.name} is in the foreground"
            }

        val config=findConfig(commandLine)
        processManager.setForeground(config, commandLine.bind(config))
        return "${config.name} is in the foreground"
        }

    private fun background(): String {
        val process=processManager.background() ?: return "No foreground process"
        return "${process.config.name} runs in the background as ${process.pid}"
        }

    private fun findConfig(commandLine: CommandLine): Config {
        val command=commandLine.command ?: throw ProcessException("No command")
        val list=configs()

        return list.firstOrNull { it.command==command }
        ?: list.firstOrNull { it.id==command }
        ?: throw ProcessException("Unknown command $command")
        }
    }
