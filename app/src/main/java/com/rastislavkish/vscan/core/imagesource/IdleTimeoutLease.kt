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
package com.rastislavkish.vscan.core.imagesource

import java.io.Closeable

// Holds a shared resource while it's being used repeatedly, and lets go of it once unused for the timeout
// Spares the cost of activating the resource on every use, e.g. of starting the camera stream for every frame
class IdleTimeoutLease(
    private val counter: RefCounter,
    private val timeoutMillis: Long,
    private val clock: () -> Long,
    ) {

    private var handle: Closeable?=null
    private var lastUse=0L

    val isHeld: Boolean
    @Synchronized get() = handle!=null

    // Marks a use, acquiring the resource if not held
    @Synchronized
    fun use() {
        lastUse=clock()

        if (handle==null)
        handle=counter.acquire()
        }

    // Releases the resource if it hasn't been used for the timeout, to be called regularly, e.g. on every frame
    @Synchronized
    fun releaseIfIdle(): Boolean {
        val handle=handle ?: return false

        if (clock()-lastUse<timeoutMillis)
        return false

        this.handle=null
        handle.close()
        return true
        }
    }
