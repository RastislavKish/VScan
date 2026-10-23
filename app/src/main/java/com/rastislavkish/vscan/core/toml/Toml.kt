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

import java.time.temporal.TemporalAccessor

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*

import org.tomlj.Toml as TomlParser
import org.tomlj.TomlArray
import org.tomlj.TomlTable

class TomlException(message: String): Exception(message)

// Reads and writes the @Serializable classes as TOML, through their JSON form
// Field names are snake_case in TOML and camelCase in Kotlin, map keys are kept as they are, and polymorphic values carry their type in a type field
// TOML has no null, so null values are left out
object Toml {

    // Newer versions may add fields an older build doesn't know
    // Defaults are written too, since e.g. the base config's definition consists of them, and changing it mustn't change the user's configs
    private val json=Json {
        ignoreUnknownKeys=true
        encodeDefaults=true
        }

    // Throws TomlException for syntax errors and values that don't fit
    fun <T> decode(serializer: KSerializer<T>, text: String): T {
        val result=TomlParser.parse(text)
        val error=result.errors().firstOrNull()
        if (error!=null)
        throw TomlException("Line ${error.position().line()}: ${error.message}")

        try {
            return json.decodeFromJsonElement(serializer, toJson(result, serializer.descriptor))
            }
        catch (e: SerializationException) {
            throw TomlException(e.message ?: "Invalid values")
            }
        catch (e: IllegalArgumentException) {
            throw TomlException(e.message ?: "Invalid values")
            }
        }

    // The header is written as comment lines at the top, omitted names are fields left out, e.g. ones the file's location implies
    fun <T> encode(serializer: KSerializer<T>, value: T, header: String?=null, omit: Set<String> = setOf()): String {
        val element=json.encodeToJsonElement(serializer, value)
        val root=(element as? JsonObject ?: throw IllegalArgumentException("Only objects can be written as TOML"))
        .let { JsonObject(it.filterKeys { name -> name !in omit }) }

        val writer=TomlWriter()
        if (header!=null) {
            for (line in header.lines())
            writer.comment(line)
            writer.blankLine()
            }
        writer.table(listOf(), root, serializer.descriptor)
        return writer.toString()
        }

    // --- Naming -------------------------------------------------------------

    fun snakeCase(name: String): String {
        val result=StringBuilder()
        for (c in name) {
            if (c.isUpperCase()) {
                if (result.isNotEmpty())
                result.append('_')
                result.append(c.lowercaseChar())
                }
            else
            result.append(c)
            }
        return result.toString()
        }

    // The descriptor of a sealed class' subclass by its serial name
    internal fun subclassDescriptor(descriptor: SerialDescriptor, type: String?): SerialDescriptor? {
        if (type==null || descriptor.kind!=PolymorphicKind.SEALED)
        return null

        val subclasses=descriptor.getElementDescriptor(1)
        for (i in 0 until subclasses.elementsCount) {
            val subclass=subclasses.getElementDescriptor(i)
            if (subclass.serialName==type || subclasses.getElementName(i)==type)
            return subclass
            }
        return null
        }

    // The index of a class' field named in TOML, by its snake_case or its own name
    internal fun elementIndex(descriptor: SerialDescriptor, key: String): Int? {
        for (i in 0 until descriptor.elementsCount) {
            val name=descriptor.getElementName(i)
            if (name==key || snakeCase(name)==key)
            return i
            }
        return null
        }

    // --- TOML to JSON -------------------------------------------------------

    private fun toJson(value: Any?, descriptor: SerialDescriptor?): JsonElement {
        return when (value) {
            null -> JsonNull
            is TomlTable -> tableToJson(value, descriptor)
            is TomlArray -> {
                val elementDescriptor=descriptor?.takeIf { it.kind==StructureKind.LIST }?.getElementDescriptor(0)
                JsonArray((0 until value.size()).map { toJson(value.get(it), elementDescriptor) })
                }
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            // Dates and times are kept as text
            is TemporalAccessor -> JsonPrimitive(value.toString())
            else -> JsonPrimitive(value.toString())
            }
        }

    private fun tableToJson(table: TomlTable, descriptor: SerialDescriptor?): JsonObject {
        val keys=table.keySet()

        return when (descriptor?.kind) {
            StructureKind.MAP -> {
                val valueDescriptor=descriptor.getElementDescriptor(1)
                JsonObject(keys.associateWith { toJson(table.get(listOf(it)), valueDescriptor) })
                }
            PolymorphicKind.SEALED -> {
                val subclass=subclassDescriptor(descriptor, table.get(listOf("type")) as? String)
                classToJson(table, subclass)
                }
            StructureKind.CLASS, StructureKind.OBJECT -> classToJson(table, descriptor)
            else -> classToJson(table, null)
            }
        }

    private fun classToJson(table: TomlTable, descriptor: SerialDescriptor?): JsonObject {
        val result=linkedMapOf<String, JsonElement>()
        for (key in table.keySet()) {
            val value=table.get(listOf(key))
            val index=descriptor?.let { elementIndex(it, key) }

            if (descriptor!=null && index!=null)
            result[descriptor.getElementName(index)]=toJson(value, descriptor.getElementDescriptor(index))
            else
            result[key]=toJson(value, null)
            }
        return JsonObject(result)
        }
    }
