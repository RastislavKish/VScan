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

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.files.BrokenFile
import com.rastislavkish.vscan.core.files.VScanFiles
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.toml.Toml
import com.rastislavkish.vscan.core.toml.TomlException

// A value of a global variable, numbers being doubles as in JavaScript
class Variable(
    val type: ParameterType,
    val value: Any,
    ) {

    // How it's typed on the command line, null for images and image sources
    val text: String?
    get() = when (type) {
        ParameterType.STRING -> value as String
        ParameterType.INT -> (value as Double).toLong().toString()
        ParameterType.DOUBLE, ParameterType.BOOL -> value.toString()
        ParameterType.IMAGE, ParameterType.IMAGE_SOURCE -> null
        ParameterType.ANY -> throw IllegalStateException("A variable has a concrete type")
        }

    companion object {

        private val integerPattern=Regex("-?[0-9]+")
        private val numberPattern=Regex("-?[0-9]+\\.[0-9]+([eE][-+]?[0-9]+)?|-?[0-9]+[eE][-+]?[0-9]+")

        // A value as typed on the command prompt, without variable references
        // A quoted text is a string, an unquoted word a bool or a number when it looks like one, several words a string as typed
        fun parse(text: String): Variable {
            val token=CommandLine.parseArguments(text).arguments.singleOrNull()
            return when {
                token==null -> Variable(ParameterType.STRING, text.trim())
                token.quoted -> Variable(ParameterType.STRING, token.text)
                else -> literal(token.text)
                }
            }

        private fun literal(text: String): Variable {
            if (text.startsWith("\\$"))
            return Variable(ParameterType.STRING, text.substring(1))
            if (text=="true" || text=="false")
            return Variable(ParameterType.BOOL, text=="true")
            if (integerPattern.matches(text))
            text.toLongOrNull()?.let { return Variable(ParameterType.INT, it.toDouble()) }
            if (numberPattern.matches(text))
            return Variable(ParameterType.DOUBLE, text.toDouble())
            return Variable(ParameterType.STRING, text)
            }
        }
    }

// The global variables, referred to as $name on the command line
// The built-in defaultImageSource and lastImage come from the app, the user's variables are kept in the memory folder: strings, numbers and bools in variables.toml, images in images/ as their original bytes, read only when used
// Thread-safe
class GlobalMemory(
    private val defaultImageSource: ImageSource,
    private val lastImage: LastImage,
    // Null keeps the variables only while the app runs, e.g. in tests
    private val files: VScanFiles?=null,
    ) {

    private val values=mutableMapOf<String, Variable>()
    // The images' paths by their names
    private val images=mutableMapOf<String, String>()
    // The images used last
    private val loadedImages=object: LinkedHashMap<String, ImageData>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageData>): Boolean = size>LOADED_IMAGES
        }
    // The error of a broken variables file, which isn't overwritten then
    private var fileError: String?=null

    init {
        load()
        }

    // Throws ProcessException for a missing image
    @Synchronized
    fun get(name: String): Variable? {
        when (name) {
            DEFAULT_IMAGE_SOURCE -> return Variable(ParameterType.IMAGE_SOURCE, defaultImageSource)
            // The image at the time of the use
            LAST_IMAGE -> return Variable(ParameterType.IMAGE, lastImage.taken?.image ?: throw ProcessException("No image taken yet"))
            }

        values[name]?.let { return it }

        val path=images[name] ?: return null
        val image=loadedImages.getOrPut(name) {
            val bytes=files?.read(path) ?: throw ProcessException("The image $name is missing")
            ImageData.fromEncoded(bytes)
            }
        return Variable(ParameterType.IMAGE, image)
        }

    // Throws ProcessException when the variable can't be assigned
    @Synchronized
    fun set(name: String, variable: Variable) {
        checkAssignable(name)
        if (variable.type==ParameterType.IMAGE_SOURCE)
        throw ProcessException("Image sources can't be stored yet")
        checkWritable()

        val wasValue=name in values
        val oldImage=images[name]

        if (variable.type==ParameterType.IMAGE) {
            val image=variable.value as ImageData
            val path="$IMAGES/$name.${image.encodedFormat.extension}"
            files?.write(path, image.encoded)
            if (oldImage!=null && oldImage!=path)
            files?.delete(oldImage)

            values.remove(name)
            images[name]=path
            loadedImages[name]=image
            if (wasValue)
            writeValues()
            }
        else {
            if (oldImage!=null) {
                files?.delete(oldImage)
                images.remove(name)
                loadedImages.remove(name)
                }

            values[name]=variable
            writeValues()
            }
        }

    // Returns false when there's no such variable
    @Synchronized
    fun unset(name: String): Boolean {
        if (isBuiltIn(name))
        throw ProcessException("$name is a built-in variable")
        if (name !in values && name !in images)
        return false
        checkWritable()

        if (values.remove(name)!=null)
        writeValues()
        images.remove(name)?.let { files?.delete(it) }
        loadedImages.remove(name)
        return true
        }

    // The names and types of the user's variables, without loading the images
    @Synchronized
    fun list(): List<Pair<String, Variable?>> {
        val result=values.map { (name, variable) -> name to variable as Variable? }+images.keys.map { it to null }
        return result.sortedBy { it.first.lowercase() }
        }

    // Reads the memory folder again, e.g. after the linked folder changed
    @Synchronized
    fun load() {
        values.clear()
        images.clear()
        loadedImages.clear()
        fileError=null
        val files=files ?: return

        try {
            val stored=files.readToml(VARIABLES, serializer) ?: mapOf()
            for ((name, value) in stored) {
                if (isValidName(name) && !isBuiltIn(name))
                values[name]=fromJson(value)
                }
            }
        catch (e: TomlException) {
            val error=e.message ?: "Invalid file"
            fileError="$VARIABLES: $error"
            files.reportBrokenFile(BrokenFile(VARIABLES, error, "VScan doesn't use the variables from it, and doesn't assign or remove variables until the file is fixed, so the file isn't overwritten."))
            }

        for (entry in files.list(IMAGES) ?: listOf()) {
            val name=entry.name.substringBeforeLast('.')
            if (!entry.isDirectory && isValidName(name) && !isBuiltIn(name) && name !in values)
            images[name]="$IMAGES/${entry.name}"
            }
        }

    private fun checkAssignable(name: String) {
        if (!isValidName(name))
        throw ProcessException("$name is not a valid variable name, use letters, digits and underscores, starting with a letter")
        if (isBuiltIn(name))
        throw ProcessException("$name is a built-in variable")

        // Folders on some storage don't tell the case apart
        val other=(values.keys+images.keys).firstOrNull { it!=name && it.equals(name, ignoreCase=true) }
        if (other!=null)
        throw ProcessException("$name would clash with $other, which differs only in case")
        }

    private fun checkWritable() {
        val error=fileError ?: return
        throw ProcessException("The variables can't be changed until the file is fixed. $error")
        }

    private fun writeValues() {
        val stored=values.mapValues { (_, variable) -> toJson(variable) }
        files?.write(VARIABLES, Toml.encode(serializer, stored, FILE_HEADER).toByteArray())
        }

    private fun toJson(variable: Variable): JsonPrimitive = when (variable.type) {
        ParameterType.STRING -> JsonPrimitive(variable.value as String)
        ParameterType.INT -> JsonPrimitive((variable.value as Double).toLong())
        ParameterType.DOUBLE -> JsonPrimitive(variable.value as Double)
        ParameterType.BOOL -> JsonPrimitive(variable.value as Boolean)
        else -> throw IllegalArgumentException("${variable.type} isn't stored in the variables file")
        }

    private fun fromJson(value: JsonPrimitive): Variable {
        if (value.isString)
        return Variable(ParameterType.STRING, value.content)
        value.booleanOrNull?.let { return Variable(ParameterType.BOOL, it) }
        value.longOrNull?.let { return Variable(ParameterType.INT, it.toDouble()) }
        return Variable(ParameterType.DOUBLE, value.double)
        }

    companion object {

        const val DEFAULT_IMAGE_SOURCE="defaultImageSource"
        const val LAST_IMAGE="lastImage"

        const val MEMORY="memory"
        private const val VARIABLES="$MEMORY/variables.toml"
        private const val IMAGES="$MEMORY/images"
        private const val FILE_HEADER="VScan's global variables, the images are in the images folder\nAssigning a variable in VScan rewrites this file, leaving out comments"

        private const val LOADED_IMAGES=4

        private val serializer=MapSerializer(String.serializer(), JsonPrimitive.serializer())
        private val namePattern=Regex("[A-Za-z][A-Za-z0-9_]*")
        private val referencePattern=Regex("\\$([A-Za-z][A-Za-z0-9_]*)")

        fun isValidName(name: String): Boolean = namePattern.matches(name)
        fun isBuiltIn(name: String): Boolean = name==DEFAULT_IMAGE_SOURCE || name==LAST_IMAGE

        // The name a text like $door refers to, or null for other texts
        fun reference(text: String): String? = referencePattern.matchEntire(text.trim())?.groupValues?.get(1)
        }
    }
