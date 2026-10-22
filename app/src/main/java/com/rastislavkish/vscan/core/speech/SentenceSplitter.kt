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

// Splits text into sentences, the units at which interrupted messages are resumed
object SentenceSplitter {

    const val MAX_CHUNK_LENGTH=500

    private val sentenceBoundary=Regex("(?<=[.!?…])\\s+|\\s*\\n+\\s*")
    private val whitespace=Regex("\\s+")

    fun split(text: String): List<String> {
        return text.split(sentenceBoundary)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { splitLong(it) }
        }

    // Sentences over the limit are split at word boundaries, or anywhere if a single word exceeds it
    private fun splitLong(sentence: String): List<String> {
        if (sentence.length<=MAX_CHUNK_LENGTH)
        return listOf(sentence)

        val chunks=mutableListOf<String>()
        var current=StringBuilder()

        for (word in sentence.split(whitespace)) {
            if (current.isNotEmpty() && current.length+1+word.length>MAX_CHUNK_LENGTH) {
                chunks.add(current.toString())
                current=StringBuilder()
                }

            var remaining=word
            while (remaining.length>MAX_CHUNK_LENGTH) {
                chunks.add(remaining.substring(0, MAX_CHUNK_LENGTH))
                remaining=remaining.substring(MAX_CHUNK_LENGTH)
                }

            if (current.isNotEmpty())
            current.append(' ')
            current.append(remaining)
            }

        if (current.isNotEmpty())
        chunks.add(current.toString())

        return chunks
        }
    }
