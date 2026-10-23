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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

import com.rastislavkish.vscan.core.config.Extraction
import com.rastislavkish.vscan.core.config.NumberValue
import com.rastislavkish.vscan.core.script.ScriptScope

// The computations behind the operations' settings
object OperationValues {

    private val numberPattern=Regex("-?\\d+(?:[.,]\\d+)?")

    // what names the setting in error messages, which leave the text out, so repeated failures are reported once
    fun evaluate(value: NumberValue, what: String, text: String?, scope: ScriptScope): Double {
        fun missing(): Nothing = throw ProcessException("No number for the $what in the output")

        value.value?.let { return it }

        value.expression?.let { expression ->
            val result=scope.evaluate(expression)
            return (result as? Number)?.toDouble()
            ?: throw ProcessException("The $what expression gave $result, not a number")
            }

        val extract=value.extract ?: throw ProcessException("The $what has no value, extract or expression")
        if (text==null)
        missing()

        return when (extract) {
            Extraction.NUMBER -> parseNumber(numberPattern.find(text)?.value) ?: missing()
            Extraction.JSON -> jsonNumber(text, value.path ?: "") ?: missing()
            Extraction.REGEX -> {
                val pattern=value.pattern ?: throw ProcessException("The $what extraction has no pattern")
                val regex=try {
                    Regex(pattern)
                    }
                catch (e: Exception) {
                    throw ProcessException("Invalid $what pattern: ${e.message}")
                    }

                val match=regex.find(text) ?: missing()
                parseNumber(match.groupValues.getOrNull(1) ?: match.value) ?: missing()
                }
            }
        }

    // Left and right volumes, keeping the full volume on the side the sound is panned to
    fun stereoVolumes(volume: Double, pan: Double): Pair<Float, Float> {
        val v=volume.coerceIn(0.0, 1.0)
        val p=pan.coerceIn(-1.0, 1.0)

        return Pair((v*minOf(1.0, 1.0-p)).toFloat(), (v*minOf(1.0, 1.0+p)).toFloat())
        }

    // The timings of a vibration pattern repeated the given number of times, and the index to loop from (-1 for none), as Android takes them
    fun waveform(pattern: List<Long>, repeat: Int): Pair<LongArray, Int> {
        if (pattern.isEmpty() || pattern.any { it<0 } || pattern.sum()==0L)
        throw ProcessException("Invalid vibration pattern $pattern")
        if (repeat<0)
        throw ProcessException("The repeat count can't be negative")

        // Android patterns start with an off duration, so an odd-length pattern is padded to keep the next repetition starting with off
        val unit=if (pattern.size%2==0) pattern else pattern+0L

        if (repeat==0)
        return Pair(unit.toLongArray(), 0)

        return Pair(List(repeat) { unit }.flatten().toLongArray(), -1)
        }

    private fun parseNumber(text: String?): Double? = text?.replace(',', '.')?.toDoubleOrNull()

    private fun jsonNumber(text: String, path: String): Double? {
        var element: JsonElement=try {
            Json.parseToJsonElement(extractJson(text))
            }
        catch (e: Exception) {
            return null
            }

        for (key in path.split('.').filter { it.isNotEmpty() }) {
            element=when (val current=element) {
                is JsonObject -> current[key]
                is JsonArray -> key.toIntOrNull()?.let { current.getOrNull(it) }
                else -> null
                } ?: return null
            }

        return (element as? JsonPrimitive)?.let { if (it.isString) parseNumber(it.content) else it.doubleOrNull }
        }

    // Models often wrap JSON in a Markdown code block or a sentence
    private fun extractJson(text: String): String {
        val start=text.indexOfFirst { it=='{' || it=='[' }
        val end=text.indexOfLast { it=='}' || it==']' }

        return if (start>=0 && end>start) text.substring(start, end+1) else text
        }
    }
