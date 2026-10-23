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

// The syntax of the command prompt: a command name followed by arguments
// Arguments are separated by spaces and can be quoted with " or ', a backslash escaping the next character inside quotes
// Named arguments are --name value, --name=value, -s value or -s=value, a Bool one may omit the value (true or false) to mean true
// Positional arguments fill the positional parameters in order, and a rest parameter takes the rest of the line as typed
class CommandLine private constructor(
    val line: String,
    val tokens: List<Token>,
    // False for a line of arguments only
    private val hasCommand: Boolean=true,
    ) {

    class Token(
        val text: String,
        // Where the token starts in the line, including an opening quote
        val start: Int,
        val quoted: Boolean,
        )

    val command: String?
    get() = if (hasCommand) tokens.firstOrNull()?.text else null

    // The tokens after the command
    val arguments: List<Token>
    get() = if (hasCommand) tokens.drop(1) else tokens

    // The line without its command, the first argument becoming the command, e.g. for fg
    fun shifted(): CommandLine = CommandLine(line, arguments)

    // Assigns the arguments to the config's parameters, as texts for Arguments.resolve
    fun bind(config: Config): Map<String, String> {
        val parameters=config.allParameters
        val positional=parameters.filter { it.positional }
        val result=mutableMapOf<String, String>()
        val arguments=arguments
        var positionalIndex=0
        var i=0

        fun put(parameter: Parameter, value: String) {
            if (result.containsKey(parameter.name))
            throw ProcessException("${parameter.name} given twice")
            result[parameter.name]=value
            }

        while (i<arguments.size) {
            val token=arguments[i]
            val option=if (token.quoted) null else parseOption(token.text)

            if (option!=null) {
                val (name, isShort, inlineValue)=option
                val parameter=parameters.firstOrNull { if (isShort) it.short==name else it.name==name }
                ?: throw ProcessException("Unknown option ${if (isShort) "-" else "--"}$name")

                val value=when {
                    inlineValue!=null -> inlineValue
                    parameter.type==ParameterType.BOOL && arguments.getOrNull(i+1)?.text?.lowercase() !in setOf("true", "false") -> "true"
                    else -> {
                        i++
                        arguments.getOrNull(i)?.text ?: throw ProcessException("${token.text} needs a value")
                        }
                    }

                put(parameter, value)
                }
            else {
                val parameter=positional.getOrNull(positionalIndex++)
                ?: throw ProcessException("Unexpected argument ${token.text}")

                if (parameter.rest) {
                    // A single quoted argument is taken unquoted
                    val value=if (i==arguments.lastIndex) token.text else line.substring(token.start).trim()
                    put(parameter, value)
                    break
                    }

                put(parameter, token.text)
                }

            i++
            }

        return result
        }

    companion object {

        fun parse(line: String): CommandLine = CommandLine(line, tokenize(line))
        fun parseArguments(line: String): CommandLine = CommandLine(line, tokenize(line), false)

        private fun tokenize(line: String): List<Token> {
            val tokens=mutableListOf<Token>()
            var i=0

            while (i<line.length) {
                if (line[i].isWhitespace()) {
                    i++
                    continue
                    }

                val start=i
                val quote=line[i]
                if (quote=='"' || quote=='\'') {
                    val text=StringBuilder()
                    i++
                    while (true) {
                        if (i>=line.length)
                        throw ProcessException("Unclosed $quote")

                        val c=line[i]
                        if (c=='\\' && i+1<line.length) {
                            text.append(line[i+1])
                            i+=2
                            }
                        else if (c==quote) {
                            i++
                            break
                            }
                        else {
                            text.append(c)
                            i++
                            }
                        }

                    tokens.add(Token(text.toString(), start, true))
                    }
                else {
                    while (i<line.length && !line[i].isWhitespace())
                    i++

                    tokens.add(Token(line.substring(start, i), start, false))
                    }
                }

            return tokens
            }

        // The name, whether it's a short one, and the value after =, or null for a non-option, e.g. a negative number
        private fun parseOption(text: String): Triple<String, Boolean, String?>? {
            val isLong=text.startsWith("--") && text.length>2
            val isShort=!isLong && text.startsWith("-") && text.length>1 && text[1].isLetter()
            if (!isLong && !isShort)
            return null

            val body=text.substring(if (isLong) 2 else 1)
            val equals=body.indexOf('=')

            return if (equals>=0)
            Triple(body.substring(0, equals), isShort, body.substring(equals+1))
            else
            Triple(body, isShort, null)
            }
        }
    }
