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
package com.rastislavkish.vscan.ui.form

import kotlinx.serialization.json.*

import com.rastislavkish.vscan.core.config.Extraction
import com.rastislavkish.vscan.core.config.NumberValue
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.process.Variable

// The ways a number setting of an operation can be given, see NumberValue
enum class NumberValueKind(val label: String) {
    NONE("Not set"),
    CONSTANT("Constant"),
    NUMBER("First number in the output"),
    JSON("Number at a JSON path"),
    REGEX("Number matched by a pattern"),
    EXPRESSION("Expression"),
    ;

    companion object {

        fun of(value: NumberValue?): NumberValueKind {
            return when {
                value==null -> NONE
                value.expression!=null -> EXPRESSION
                value.extract==Extraction.NUMBER -> NUMBER
                value.extract==Extraction.JSON -> JSON
                value.extract==Extraction.REGEX -> REGEX
                else -> CONSTANT
                }
            }
        }
    }

// Converting the edited values to and from the texts of the fields
// The parsers throw IllegalArgumentException with a message for the user
object Values {

    fun formatNumber(value: Double): String {
        return if (value%1.0==0.0 && Math.abs(value)<1e15)
        value.toLong().toString()
        else
        value.toString()
        }

    // A decimal comma is taken too
    fun parseDouble(text: String): Double {
        return requireNotNull(text.trim().replace(',', '.').toDoubleOrNull()) { "Not a number" }
        }
    fun parseInt(text: String): Int {
        return requireNotNull(text.trim().toIntOrNull()) { "Not a whole number" }
        }

    // Vibration patterns as milliseconds separated by commas or spaces
    fun formatPattern(pattern: List<Long>): String = pattern.joinToString(", ")
    fun parsePattern(text: String): List<Long> {
        val parts=text.split(',', ' ').filter { it.isNotBlank() }
        return parts.map { requireNotNull(it.trim().toLongOrNull()?.takeIf { it>=0 }) { "Use milliseconds separated by commas" } }
        }

    // The initial values of the process memory, typed like values assigned on the command prompt: 3 is a number, true a bool, '3' or "3" a string
    fun formatMemoryValue(value: JsonPrimitive): String {
        if (!value.isString)
        return value.content

        val text=value.content
        val parsed=Variable.parse(text)
        if (parsed.type==ParameterType.STRING && parsed.value==text)
        return text
        return "\""+text.replace("\\", "\\\\").replace("\"", "\\\"")+"\""
        }
    fun parseMemoryValue(text: String): JsonPrimitive {
        val variable=try {
            Variable.parse(text)
            }
        catch (e: Exception) {
            // E.g. an unclosed quote
            throw IllegalArgumentException(e.message)
            }

        return when (variable.type) {
            ParameterType.BOOL -> JsonPrimitive(variable.value as Boolean)
            ParameterType.INT -> JsonPrimitive((variable.value as Double).toLong())
            ParameterType.DOUBLE -> JsonPrimitive(variable.value as Double)
            else -> JsonPrimitive(variable.value as String)
            }
        }

    // A number value of the kind, keeping what the current one has
    fun withKind(value: NumberValue?, kind: NumberValueKind, constant: Double): NumberValue? {
        return when (kind) {
            NumberValueKind.NONE -> null
            NumberValueKind.CONSTANT -> NumberValue(value=value?.value ?: constant)
            NumberValueKind.NUMBER -> NumberValue(extract=Extraction.NUMBER)
            NumberValueKind.JSON -> NumberValue(extract=Extraction.JSON, path=value?.path ?: "")
            NumberValueKind.REGEX -> NumberValue(extract=Extraction.REGEX, pattern=value?.pattern ?: "")
            NumberValueKind.EXPRESSION -> NumberValue(expression=value?.expression ?: "")
            }
        }
    }
