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

import com.rastislavkish.vscan.core.config.Operation
import com.rastislavkish.vscan.core.script.ScriptScope

// Carries out operations whose condition held, with the iteration's variables in scope
interface OperationExecutor {

    suspend fun execute(operation: Operation, scope: ScriptScope, process: Process)

    // Stops whatever the process' operations keep running, e.g. looping sounds, when it ends
    fun release(process: Process) {}
    }
