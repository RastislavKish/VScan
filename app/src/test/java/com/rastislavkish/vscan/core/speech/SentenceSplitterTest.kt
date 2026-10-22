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
package com.rastislavkish.vscan.core.speech

import org.junit.Assert.*
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun splitsAtSentenceEnds() {
        assertEquals(
            listOf("A bus is coming.", "It's line 42!", "Hurry?"),
            SentenceSplitter.split("A bus is coming. It's line 42!  Hurry?"),
            )
        }

    @Test
    fun splitsAtLineBreaks() {
        assertEquals(listOf("Exit", "- left", "- right"), SentenceSplitter.split("Exit\n- left\n\n- right\n"))
        }

    @Test
    fun keepsNumbersAndAbbreviationsWithoutSpaceTogether() {
        assertEquals(listOf("It costs 3.50 euros at 10:30."), SentenceSplitter.split("It costs 3.50 euros at 10:30."))
        }

    @Test
    fun emptyTextHasNoSentences() {
        assertTrue(SentenceSplitter.split("").isEmpty())
        assertTrue(SentenceSplitter.split(" \n\t ").isEmpty())
        }

    @Test
    fun longSentencesAreSplitAtWordBoundaries() {
        val sentence=(1..200).joinToString(" ") { "word$it" }

        val chunks=SentenceSplitter.split(sentence)

        assertTrue(chunks.size>1)
        assertTrue(chunks.all { it.length<=SentenceSplitter.MAX_CHUNK_LENGTH })
        assertEquals(sentence, chunks.joinToString(" "))
        }

    @Test
    fun overlongWordsAreSplitAnywhere() {
        val word="x".repeat(SentenceSplitter.MAX_CHUNK_LENGTH*2+10)

        val chunks=SentenceSplitter.split(word)

        assertEquals(3, chunks.size)
        assertEquals(word, chunks.joinToString(""))
        }

    }
