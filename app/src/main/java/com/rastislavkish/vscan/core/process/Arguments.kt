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

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.imagesource.LensSource
import com.rastislavkish.vscan.core.imagesource.StaticImageSource

// Turns the texts a config is called with into the values of its parameters
// Until the command language and the memory arrive, $default and $lastImage are the only globals
class Arguments(
    private val defaultImageSource: ImageSource,
    private val lastImage: LastImage=LastImage(),
    ) {

    // Missing arguments take their parameter's default, and the config's lens is applied to the image sources it's meant for
    fun resolve(config: Config, given: Map<String, String> = mapOf()): Map<String, Any?> {
        val parameters=config.allParameters
        validate(parameters)

        for (name in given.keys) {
            if (parameters.none { it.name==name })
            throw ProcessException("${config.name} has no parameter $name")
            }

        val result=mutableMapOf<String, Any?>()
        for (parameter in parameters) {
            val text=given[parameter.name] ?: parameter.default
            ?: throw ProcessException("Missing ${parameter.name}")

            var value=convert(parameter, text)
            if (value is ImageSource && config.lens.appliesTo(parameter))
            value=LensSource(value, config.lens.lens)

            result[parameter.name]=value
            }

        return result
        }

    private fun validate(parameters: List<Parameter>) {
        val names=mutableSetOf<String>()
        for (parameter in parameters) {
            if (parameter.name in Process.reservedNames)
            throw ProcessException("${parameter.name} is a process variable, it can't be a parameter name")
            if (!names.add(parameter.name))
            throw ProcessException("Duplicate parameter ${parameter.name}")
            }
        }

    private fun convert(parameter: Parameter, text: String): Any? {
        fun invalid(): Nothing = throw ProcessException("${parameter.name}: \"$text\" is not ${typeDescription(parameter.type)}")

        return when (parameter.type) {
            ParameterType.STRING -> text
            // Numbers are doubles in JavaScript
            ParameterType.INT -> (text.trim().toIntOrNull() ?: invalid()).toDouble()
            ParameterType.DOUBLE -> text.trim().toDoubleOrNull() ?: invalid()
            ParameterType.BOOL -> when (text.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> invalid()
                }
            ParameterType.IMAGE_SOURCE -> when (text.trim()) {
                "\$default" -> defaultImageSource
                // The image at the time of the call
                "\$lastImage" -> StaticImageSource(lastImage.taken?.image ?: throw ProcessException("No image taken yet"))
                else -> throw ProcessException("${parameter.name}: Unknown image source $text")
                }
            ParameterType.IMAGE -> throw ProcessException("${parameter.name}: Image parameters aren't supported yet")
            }
        }

    private fun typeDescription(type: ParameterType): String = when (type) {
        ParameterType.STRING -> "a string"
        ParameterType.INT -> "a whole number"
        ParameterType.DOUBLE -> "a number"
        ParameterType.BOOL -> "true or false"
        ParameterType.IMAGE -> "an image"
        ParameterType.IMAGE_SOURCE -> "an image source"
        }
    }
