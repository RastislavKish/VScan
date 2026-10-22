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
package com.rastislavkish.vscan.core

import org.junit.Assert.*
import org.junit.Test

class IdsTest {

    @Test
    fun slugifyLowercasesAndJoinsWordsWithDashes() {
        assertEquals("bus-lines", Ids.slugify("Bus lines", "config"))
        assertEquals("find-a-thing", Ids.slugify("  Find: a thing!  ", "config"))
        assertEquals("gpt-4o-mini", Ids.slugify("GPT-4o mini", "config"))
        }

    @Test
    fun slugifyStripsDiacritics() {
        assertEquals("citanie-tabule", Ids.slugify("Čítanie tabule", "config"))
        }

    @Test
    fun slugifyFallsBackForEmptyNames() {
        assertEquals("config", Ids.slugify("", "config"))
        assertEquals("config", Ids.slugify("?!", "config"))
        }

    @Test
    fun slugifyNeverProducesNumericIds() {
        assertEquals("config-42", Ids.slugify("42", "config"))
        }

    @Test
    fun uniqueAppendsFirstFreeSuffix() {
        assertEquals("bus", Ids.unique("bus", setOf()))
        assertEquals("bus-2", Ids.unique("bus", setOf("bus")))
        assertEquals("bus-4", Ids.unique("bus", setOf("bus", "bus-2", "bus-3")))
        }

    }
