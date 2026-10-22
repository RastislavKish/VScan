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

// Shares a resource like the torch among independent users, keeping it active while any of them holds a handle
// onActiveChange is called on transitions only, from the thread acquiring or closing the handle
class RefCounter(
    private val onActiveChange: (Boolean) -> Unit,
    ) {

    private var count=0

    val isActive: Boolean
    @Synchronized get() = count>0

    fun acquire(): Closeable {
        val activated=synchronized(this) {
            count++
            count==1
            }

        if (activated)
        onActiveChange(true)

        return object : Closeable {

            private var closed=false

            override fun close() {
                val deactivated=synchronized(this@RefCounter) {
                    if (closed)
                    return

                    closed=true
                    count--
                    count==0
                    }

                if (deactivated)
                onActiveChange(false)
                }
            }
        }
    }

// A handle for sources that don't have the requested resource
object NoOpHandle: Closeable {

    override fun close() {}
    }
