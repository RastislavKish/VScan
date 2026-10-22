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

import org.junit.Assert.*
import org.junit.Test

class RefCounterTest {

    private val changes=mutableListOf<Boolean>()
    private val counter=RefCounter { changes.add(it) }

    @Test
    fun activeWhileAnyHandleIsOpen() {
        val first=counter.acquire()
        val second=counter.acquire()
        assertTrue(counter.isActive)

        first.close()
        assertTrue(counter.isActive)

        second.close()
        assertFalse(counter.isActive)

        assertEquals(listOf(true, false), changes)
        }

    @Test
    fun closingTwiceHasNoEffect() {
        val first=counter.acquire()
        val second=counter.acquire()

        first.close()
        first.close()

        assertTrue(counter.isActive)
        second.close()
        assertEquals(listOf(true, false), changes)
        }

    @Test
    fun reactivates() {
        counter.acquire().close()
        counter.acquire().close()

        assertEquals(listOf(true, false, true, false), changes)
        }

    }
