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
package com.rastislavkish.vscan.core.template

import com.rastislavkish.vscan.core.template.Template.Branch
import com.rastislavkish.vscan.core.template.Template.Node

internal class TemplateParser(
    private val source: String,
    ) {

    private sealed class Token {

        abstract val line: Int

        data class Text(val text: String, override val line: Int): Token()
        data class Expression(val source: String, override val line: Int): Token()
        data class Tag(val content: String, override val line: Int): Token()
        data class Comment(override val line: Int): Token()
        }

    private val identifier=Regex("[A-Za-z_$][A-Za-z0-9_$]*")
    private val forTag=Regex("for\\s+(?:($identifier)\\s*,\\s*)?($identifier)\\s+in\\s+(.+)", RegexOption.DOT_MATCHES_ALL)

    private var tokens=listOf<Token>()
    private var position=0

    fun parse(): List<Node> {
        tokens=removeStandaloneLines(tokenize())
        position=0

        val nodes=parseNodes(setOf())
        if (position<tokens.size) {
            val token=tokens[position] as Token.Tag
            throw TemplateException("Unexpected {% ${token.content} %}", token.line)
            }

        return nodes
        }

    // Tokens

    private fun tokenize(): List<Token> {
        val result=mutableListOf<Token>()
        var index=0

        while (index<source.length) {
            val start=nextTagStart(index)

            if (start>index)
            result.add(Token.Text(source.substring(index, start), lineAt(index)))
            if (start>=source.length)
            break

            val line=lineAt(start)
            when (source[start+1]) {
                '{' -> {
                    val end=findClosing(start+2, "}}", line, "{{")
                    val expression=source.substring(start+2, end).trim()
                    if (expression.isEmpty())
                    throw TemplateException("Empty {{ }}", line)

                    result.add(Token.Expression(expression, line))
                    index=end+2
                    }
                '%' -> {
                    val end=findClosing(start+2, "%}", line, "{%")
                    result.add(Token.Tag(source.substring(start+2, end).trim(), line))
                    index=end+2
                    }
                else -> {
                    val end=source.indexOf("#}", start+2)
                    if (end<0)
                    throw TemplateException("Unclosed {#", line)

                    result.add(Token.Comment(line))
                    index=end+2
                    }
                }
            }

        return result
        }

    private fun nextTagStart(from: Int): Int {
        var index=from
        while (index<source.length-1) {
            if (source[index]=='{' && source[index+1] in "{%#")
            return index
            index++
            }
        return source.length
        }

    // Finds the closing delimiter, skipping JavaScript string literals, which may contain it
    private fun findClosing(from: Int, closing: String, line: Int, opening: String): Int {
        var index=from
        var quote: Char?=null

        while (index<source.length) {
            val char=source[index]

            if (quote!=null) {
                if (char=='\\')
                index++
                else if (char==quote)
                quote=null
                }
            else if (char=='"' || char=='\'' || char=='`')
            quote=char
            else if (source.startsWith(closing, index))
            return index

            index++
            }

        throw TemplateException("Unclosed $opening", line)
        }

    private fun lineAt(index: Int): Int {
        var line=1
        for (i in 0 until index)
        if (source[i]=='\n')
        line++
        return line
        }

    // Block tags and comments alone on their line take the whole line with them, including its line break
    // Standalone tags are recognized on the original text, so tags on consecutive lines all qualify, and their cuts are then combined
    private fun removeStandaloneLines(tokens: List<Token>): List<Token> {
        // The kept range of each text token
        val keepFrom=IntArray(tokens.size)
        val keepTo=IntArray(tokens.size) { (tokens[it] as? Token.Text)?.text?.length ?: 0 }

        for (i in tokens.indices) {
            val token=tokens[i]
            if (token !is Token.Tag && token !is Token.Comment)
            continue

            val before=tokens.getOrNull(i-1)
            val after=tokens.getOrNull(i+1)

            // At the start of the template, or after a line break with only whitespace in between
            val lineStart=when (before) {
                null -> 0
                is Token.Text -> {
                    val newline=before.text.lastIndexOf('\n')
                    val tail=before.text.substring(newline+1)
                    if (tail.isBlank() && (newline>=0 || i-1==0)) newline+1 else null
                    }
                else -> null
                }

            // At the end of the template, or followed by only whitespace up to a line break, which goes too
            val lineEnd=when (after) {
                null -> 0
                is Token.Text -> {
                    val newline=after.text.indexOf('\n')
                    val head=if (newline<0) after.text else after.text.substring(0, newline)
                    if (!head.isBlank()) null
                    else if (newline>=0) newline+1
                    else if (i+1==tokens.size-1) after.text.length
                    else null
                    }
                else -> null
                }

            if (lineStart==null || lineEnd==null)
            continue

            if (before is Token.Text)
            keepTo[i-1]=minOf(keepTo[i-1], lineStart)
            if (after is Token.Text)
            keepFrom[i+1]=maxOf(keepFrom[i+1], lineEnd)
            }

        val result=mutableListOf<Token>()
        for (i in tokens.indices) {
            val token=tokens[i]

            when (token) {
                is Token.Comment -> {}
                is Token.Text -> {
                    if (keepFrom[i]<keepTo[i])
                    result.add(Token.Text(token.text.substring(keepFrom[i], keepTo[i]), token.line))
                    }
                else -> result.add(token)
                }
            }

        return result
        }

    // Nodes

    // Parses nodes until one of the given tags, which is left unconsumed
    private fun parseNodes(terminators: Set<String>): List<Node> {
        val nodes=mutableListOf<Node>()

        while (position<tokens.size) {
            val token=tokens[position]

            when (token) {
                is Token.Text -> {
                    nodes.add(Node.Text(token.text))
                    position++
                    }
                is Token.Expression -> {
                    nodes.add(Node.Expression(token.source, token.line))
                    position++
                    }
                is Token.Tag -> {
                    val keyword=token.content.substringBefore(' ').substringBefore('\t').substringBefore('\n')
                    if (keyword in terminators)
                    return nodes

                    position++
                    nodes.add(when (keyword) {
                        "if" -> parseIf(token)
                        "for" -> parseFor(token)
                        "elif", "else", "endif", "endfor" -> throw TemplateException("{% $keyword %} without a matching block", token.line)
                        else -> throw TemplateException("Unknown tag {% ${token.content} %}", token.line)
                        })
                    }
                is Token.Comment -> position++
                }
            }

        if (terminators.isNotEmpty())
        throw TemplateException("Missing {% ${terminators.first { it.startsWith("end") }} %}", tokens.lastOrNull()?.line ?: 1)

        return nodes
        }

    private fun parseIf(opening: Token.Tag): Node {
        val branches=mutableListOf<Branch>()
        var otherwise: List<Node>?=null

        var condition=argument(opening, "if")
        var line=opening.line

        while (true) {
            val body=parseNodes(setOf("elif", "else", "endif"))
            branches.add(Branch(condition, line, body))

            val tag=tokens.getOrNull(position) as Token.Tag?
            ?: throw TemplateException("Missing {% endif %}", opening.line)
            position++

            when {
                tag.content=="endif" -> break
                tag.content=="else" -> {
                    otherwise=parseNodes(setOf("endif", "elif", "else"))
                    val end=tokens.getOrNull(position) as Token.Tag?
                    if (end?.content!="endif")
                    throw TemplateException("Expected {% endif %} after {% else %}", end?.line ?: tag.line)
                    position++
                    break
                    }
                else -> {
                    condition=argument(tag, "elif")
                    line=tag.line
                    }
                }
            }

        return Node.If(branches, otherwise)
        }

    private fun parseFor(opening: Token.Tag): Node {
        val match=forTag.matchEntire(opening.content)
        ?: throw TemplateException("Expected {% for item in array %} or {% for index, item in array %}", opening.line)

        val indexName=match.groupValues[1].ifEmpty { null }
        val itemName=match.groupValues[2]
        val iterable=match.groupValues[3].trim()

        val body=parseNodes(setOf("endfor"))
        if (position>=tokens.size)
        throw TemplateException("Missing {% endfor %}", opening.line)
        position++

        return Node.For(indexName, itemName, iterable, opening.line, body)
        }

    private fun argument(tag: Token.Tag, keyword: String): String {
        val argument=tag.content.removePrefix(keyword).trim()
        if (argument.isEmpty() || !tag.content.substring(keyword.length).first().isWhitespace())
        throw TemplateException("{% $keyword %} needs a condition", tag.line)

        return argument
        }
    }
