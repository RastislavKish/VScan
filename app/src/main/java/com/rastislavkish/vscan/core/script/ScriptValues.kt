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
package com.rastislavkish.vscan.core.script

import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource

// An app value scripts can pass around and compare, but not look into, e.g. an image
class OpaqueValue(
    val value: Any,
    private val typeName: String,
    ): ScriptableObject() {

    override fun getClassName(): String = typeName

    override fun getDefaultValue(hint: Class<*>?): Any = "[$typeName]"
    }

// Conversions between Kotlin and JavaScript values
// Numbers become doubles, lists arrays, string-keyed maps objects, and images or image sources opaque values
internal object ScriptValues {

    fun toJs(context: Context, scope: Scriptable, value: Any?, opaqueValues: MutableMap<Any, OpaqueValue>): Any? {
        return when (value) {
            null -> null
            is String, is Boolean -> value
            is Number -> value.toDouble()
            is Char -> value.toString()
            is List<*> -> context.newArray(scope, value.map { toJs(context, scope, it, opaqueValues) }.toTypedArray())
            is Array<*> -> context.newArray(scope, value.map { toJs(context, scope, it, opaqueValues) }.toTypedArray())
            is Map<*, *> -> {
                val obj=context.newObject(scope)
                for ((key, item) in value)
                ScriptableObject.putProperty(obj, key.toString(), toJs(context, scope, item, opaqueValues))
                obj
                }
            is ImageData -> opaque(scope, value, "Image", opaqueValues)
            is ImageSource -> opaque(scope, value, "ImageSource", opaqueValues)
            else -> opaque(scope, value, value.javaClass.simpleName, opaqueValues)
            }
        }

    private fun opaque(scope: Scriptable, value: Any, typeName: String, opaqueValues: MutableMap<Any, OpaqueValue>): OpaqueValue {
        return opaqueValues.getOrPut(value) {
            val opaque=OpaqueValue(value, typeName)
            opaque.parentScope=scope
            opaque.prototype=ScriptableObject.getObjectPrototype(scope)
            opaque
            }
        }

    fun fromJs(value: Any?): Any? {
        return when (value) {
            null, is Undefined -> null
            is String -> value
            is ConsString -> value.toString()
            is CharSequence -> value.toString()
            is Boolean -> value
            is Number -> value.toDouble()
            is OpaqueValue -> value.value
            is NativeArray -> (0 until value.length.toInt()).map { fromJs(value.get(it)) }
            is Function -> throw ScriptException("A function can't be used as a value")
            is Scriptable -> {
                val result=linkedMapOf<String, Any?>()
                for (id in value.ids) {
                    val key=id.toString()
                    result[key]=fromJs(ScriptableObject.getProperty(value, key))
                    }
                result
                }
            else -> value
            }
        }

    fun toText(value: Any?): String {
        if (value==null || value is Undefined)
        return ""

        return ScriptRuntime.toString(value)
        }
    }
