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

class IdleTimeoutLeaseTest {

    private var time=0L
    private val counter=RefCounter {}
    private val lease=IdleTimeoutLease(counter, 10000, { time })

    @Test
    fun acquiresOnFirstUse() {
        assertFalse(counter.isActive)

        lease.use()

        assertTrue(counter.isActive)
        assertTrue(lease.isHeld)
        }

    @Test
    fun staysHeldWhileUsedWithinTheTimeout() {
        lease.use()

        for (i in 1..5) {
            time+=9000
            assertFalse(lease.releaseIfIdle())
            lease.use()
            }

        assertTrue(counter.isActive)
        }

    @Test
    fun releasesOnceIdleForTheTimeout() {
        lease.use()

        time+=9999
        assertFalse(lease.releaseIfIdle())
        time+=1
        assertTrue(lease.releaseIfIdle())

        assertFalse(counter.isActive)
        assertFalse(lease.isHeld)
        }

    @Test
    fun reacquiresAfterRelease() {
        lease.use()
        time+=20000
        lease.releaseIfIdle()

        lease.use()

        assertTrue(counter.isActive)
        }

    @Test
    fun releasingKeepsOtherHoldersActive() {
        val explicit=counter.acquire()
        lease.use()
        time+=20000

        assertTrue(lease.releaseIfIdle())
        assertTrue(counter.isActive)

        explicit.close()
        assertFalse(counter.isActive)
        }

    @Test
    fun releaseWithoutUseDoesNothing() {
        assertFalse(lease.releaseIfIdle())
        }

    }
