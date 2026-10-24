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
import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.imagesource.LensSource
import com.rastislavkish.vscan.core.imagesource.StaticImageSource

// Turns the texts a config is called with into the values of its parameters
// A text like $door takes the value of the global variable
// An Any parameter takes a variable's value as it is, and reads a text like a value assigned on the command prompt
class Arguments(
    val memory: GlobalMemory,
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
        val name=GlobalMemory.reference(text)
        if (name!=null) {
            val variable=memory.get(name) ?: throw ProcessException("${parameter.name}: Unknown variable \$$name")
            return convert(parameter, variable)
            }

        // \$ starts a text with a dollar
        val literal=if (text.trimStart().startsWith("\\$")) text.trimStart().substring(1) else text
        return convertText(parameter, literal)
        }

    private fun convertText(parameter: Parameter, literal: String): Any? {
        fun invalid(): Nothing = throw ProcessException("${parameter.name}: \"$literal\" is not ${typeDescription(parameter.type)}")

        return when (parameter.type) {
            ParameterType.ANY -> Variable.parse(literal).value
            ParameterType.STRING -> literal
            // Numbers are doubles in JavaScript
            ParameterType.INT -> (literal.trim().toIntOrNull() ?: invalid()).toDouble()
            ParameterType.DOUBLE -> literal.trim().toDoubleOrNull() ?: invalid()
            ParameterType.BOOL -> when (literal.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> invalid()
                }
            ParameterType.IMAGE, ParameterType.IMAGE_SOURCE -> throw ProcessException("${parameter.name}: Pass ${typeDescription(parameter.type)} as a variable, e.g. \$lastImage")
            }
        }

    // Strings, numbers and bools convert as their texts, an image serves as an image source always giving it
    private fun convert(parameter: Parameter, variable: Variable): Any? {
        val text=variable.text
        if (parameter.type==ParameterType.ANY)
        return variable.value

        val takesImages=parameter.type==ParameterType.IMAGE || parameter.type==ParameterType.IMAGE_SOURCE
        if (text!=null && !takesImages)
        return convertText(parameter, text)

        return when {
            variable.type==parameter.type -> variable.value
            variable.type==ParameterType.IMAGE && parameter.type==ParameterType.IMAGE_SOURCE -> StaticImageSource(variable.value as ImageData)
            else -> throw ProcessException("${parameter.name}: Expected ${typeDescription(parameter.type)}, got ${typeDescription(variable.type)}")
            }
        }

    private fun typeDescription(type: ParameterType): String = when (type) {
        ParameterType.STRING -> "a string"
        ParameterType.INT -> "a whole number"
        ParameterType.DOUBLE -> "a number"
        ParameterType.BOOL -> "true or false"
        ParameterType.IMAGE -> "an image"
        ParameterType.IMAGE_SOURCE -> "an image source"
        ParameterType.ANY -> "a value"
        }
    }
