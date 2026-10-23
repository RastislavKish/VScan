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
package com.rastislavkish.vscan.core.toml

import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.*

// Writes the JSON form of a value as TOML
// Objects become tables and the arrays of objects at the top level arrays of tables, deeper ones are written inline
// Multi-line strings are written literally where possible, so prompts read as they are
internal class TomlWriter {

    private val out=StringBuilder()

    fun comment(line: String) {
        out.append(if (line.isEmpty()) "#" else "# $line").append('\n')
        }
    fun blankLine() {
        out.append('\n')
        }

    fun table(path: List<String>, obj: JsonObject, descriptor: SerialDescriptor?) {
        val classDescriptor=resolve(descriptor, obj)
        val entries=obj.entries.filter { it.value !is JsonNull }

        val tables=entries.filter { it.value is JsonObject }
        val arraysOfTables=if (path.isEmpty()) entries.filter { isArrayOfTables(it.value) } else listOf()
        val values=entries-tables.toSet()-arraysOfTables.toSet()

        if (path.isNotEmpty() && (values.isNotEmpty() || tables.isEmpty())) {
            if (out.isNotEmpty() && !out.endsWith("\n\n"))
            blankLine()
            out.append('[').append(path.joinToString(".") { key(it) }).append("]\n")
            }
        for ((name, value) in values)
        line(name, value, classDescriptor)

        // Sections in the order of the fields
        for ((name, value) in entries) {
            if (value is JsonObject)
            table(path+keyName(classDescriptor, name), value, fieldDescriptor(classDescriptor, name))
            else if (path.isEmpty() && isArrayOfTables(value))
            arrayOfTables(path+keyName(classDescriptor, name), value as JsonArray, fieldDescriptor(classDescriptor, name))
            }
        }

    private fun arrayOfTables(path: List<String>, array: JsonArray, descriptor: SerialDescriptor?) {
        val elementDescriptor=descriptor?.takeIf { it.kind==StructureKind.LIST }?.getElementDescriptor(0)
        for (element in array) {
            if (out.isNotEmpty() && !out.endsWith("\n\n"))
            blankLine()
            out.append("[[").append(path.joinToString(".") { key(it) }).append("]]\n")

            val element=element as JsonObject
            val elementClass=resolve(elementDescriptor, element)
            for ((name, value) in element) {
                if (value !is JsonNull)
                line(name, value, elementClass)
                }
            }
        }

    private fun line(name: String, value: JsonElement, descriptor: SerialDescriptor?) {
        out.append(key(keyName(descriptor, name)))
        .append(" = ")
        .append(inline(value, fieldDescriptor(descriptor, name)))
        .append('\n')
        }

    private fun inline(value: JsonElement, descriptor: SerialDescriptor?): String {
        return when (value) {
            is JsonNull -> "\"\""
            is JsonPrimitive -> if (value.isString) string(value.content) else value.content
            is JsonArray -> {
                val elementDescriptor=descriptor?.takeIf { it.kind==StructureKind.LIST }?.getElementDescriptor(0)
                value.joinToString(", ", "[", "]") { inline(it, elementDescriptor) }
                }
            is JsonObject -> {
                val classDescriptor=resolve(descriptor, value)
                val entries=value.entries.filter { it.value !is JsonNull }
                if (entries.isEmpty())
                "{}"
                else
                entries.joinToString(", ", "{ ", " }") { (name, element) ->
                    "${key(keyName(classDescriptor, name))} = ${inline(element, fieldDescriptor(classDescriptor, name))}"
                    }
                }
            }
        }

    override fun toString(): String = out.toString()

    companion object {

        private val bareKey=Regex("[A-Za-z0-9_-]+")

        private fun isArrayOfTables(value: JsonElement): Boolean = value is JsonArray && value.isNotEmpty() && value.all { it is JsonObject }

        // A sealed class' value is described by its subclass
        private fun resolve(descriptor: SerialDescriptor?, obj: JsonObject): SerialDescriptor? {
            if (descriptor?.kind==PolymorphicKind.SEALED)
            return Toml.subclassDescriptor(descriptor, (obj["type"] as? JsonPrimitive)?.content)
            return descriptor
            }

        private fun fieldDescriptor(descriptor: SerialDescriptor?, name: String): SerialDescriptor? {
            return when (descriptor?.kind) {
                StructureKind.MAP -> descriptor.getElementDescriptor(1)
                StructureKind.CLASS, StructureKind.OBJECT -> {
                    val index=descriptor.getElementIndex(name)
                    if (index==CompositeDecoder.UNKNOWN_NAME) null else descriptor.getElementDescriptor(index)
                    }
                else -> null
                }
            }

        // Field names become snake_case, map keys and unknown names stay
        private fun keyName(descriptor: SerialDescriptor?, name: String): String {
            return when (descriptor?.kind) {
                StructureKind.CLASS, StructureKind.OBJECT -> Toml.snakeCase(name)
                else -> name
                }
            }

        private fun key(name: String): String = if (bareKey.matches(name)) name else basicString(name)

        private fun isPrintable(c: Char, allowNewline: Boolean): Boolean = !c.isISOControl() || c=='\t' || (allowNewline && c=='\n')

        fun string(text: String): String {
            if ('\n' in text) {
                // The newline after the opening quotes isn't part of the string
                if ("'''" !in text && !text.endsWith("'") && text.all { isPrintable(it, true) })
                return "'''\n$text'''"

                return "\"\"\"\n"+escape(text, true)+"\"\"\""
                }

            if ('\\' in text && '\'' !in text && text.all { isPrintable(it, false) })
            return "'$text'"

            return basicString(text)
            }

        private fun basicString(text: String): String = "\""+escape(text, false)+"\""

        private fun escape(text: String, multiline: Boolean): String {
            val result=StringBuilder()
            for (c in text) {
                when {
                    c=='\\' -> result.append("\\\\")
                    c=='"' -> result.append("\\\"")
                    c=='\n' && multiline -> result.append('\n')
                    c=='\n' -> result.append("\\n")
                    c=='\t' -> result.append("\\t")
                    c=='\r' -> result.append("\\r")
                    c.isISOControl() -> result.append("\\u%04X".format(c.code))
                    else -> result.append(c)
                    }
                }
            return result.toString()
            }
        }
    }
