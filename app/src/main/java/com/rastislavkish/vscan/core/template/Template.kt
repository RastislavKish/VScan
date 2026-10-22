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

import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray

import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.ImageSource
import com.rastislavkish.vscan.core.script.OpaqueValue
import com.rastislavkish.vscan.core.script.ScriptEngine
import com.rastislavkish.vscan.core.script.ScriptException
import com.rastislavkish.vscan.core.script.ScriptScope
import com.rastislavkish.vscan.core.script.ScriptValues

class TemplateException(message: String, val line: Int): Exception("Line $line: $message")

// A piece of a rendered template
sealed class TemplatePart {

    data class Text(val text: String): TemplatePart()

    // An image to include at this position, either a ready image or a source to take one from
    data class Image(val value: Any): TemplatePart() {

        init {
            require(value is ImageData || value is ImageSource) { "Not an image: $value" }
            }
        }
    }

// A Jinja-style template, with JavaScript expressions
//   {{ expression }} inserts the value, images and image sources become image parts
//   {% if condition %} ... {% elif condition %} ... {% else %} ... {% endif %}
//   {% for item in array %} ... {% endfor %}, or {% for index, item in array %}
//   {# comment #}
// A block tag or comment alone on its line removes the whole line, so blocks don't leave blank lines behind
class Template private constructor(
    private val nodes: List<Node>,
    ) {

    internal sealed class Node {

        data class Text(val text: String): Node()
        data class Expression(val source: String, val line: Int): Node()
        data class If(val branches: List<Branch>, val otherwise: List<Node>?): Node()
        data class For(val indexName: String?, val itemName: String, val iterable: String, val line: Int, val body: List<Node>): Node()
        }
    internal data class Branch(val condition: String, val line: Int, val body: List<Node>)

    fun render(scope: ScriptScope): List<TemplatePart> {
        val parts=mutableListOf<TemplatePart>()
        val text=StringBuilder()

        fun flushText() {
            if (text.isNotEmpty()) {
                parts.add(TemplatePart.Text(text.toString()))
                text.clear()
                }
            }

        fun renderNodes(nodes: List<Node>, scope: ScriptScope, context: Context) {
            for (node in nodes) {
                when (node) {
                    is Node.Text -> text.append(node.text)
                    is Node.Expression -> {
                        val value=evaluate(node.line) { scope.evaluateRaw(context, node.source) }
                        val image=(value as? OpaqueValue)?.value

                        if (image is ImageData || image is ImageSource) {
                            flushText()
                            parts.add(TemplatePart.Image(image))
                            }
                        else
                        text.append(ScriptValues.toText(value))
                        }
                    is Node.If -> {
                        val branch=node.branches.firstOrNull { branch ->
                            evaluate(branch.line) { Context.toBoolean(scope.evaluateRaw(context, branch.condition)) }
                            }

                        val body=branch?.body ?: node.otherwise
                        if (body!=null)
                        renderNodes(body, scope, context)
                        }
                    is Node.For -> {
                        val iterable=evaluate(node.line) { scope.evaluateRaw(context, node.iterable) }
                        val items=iterable as? NativeArray
                        ?: throw TemplateException("\"${node.iterable}\" is not an array", node.line)

                        for (i in 0 until items.length.toInt()) {
                            val variables=mutableMapOf<String, Any?>(node.itemName to items.get(i))
                            if (node.indexName!=null)
                            variables[node.indexName]=i.toDouble()

                            renderNodes(node.body, scope.childWithRaw(context, variables), context)
                            }
                        }
                    }
                }
            }

        try {
            ScriptEngine.run { context ->
                renderNodes(nodes, scope, context)
                }
            }
        catch (e: ScriptException) {
            // A time limit exceeded across the whole render, not attributable to a line
            throw TemplateException(e.message ?: "Script error", 0)
            }

        flushText()
        return parts
        }

    // Renders the template as plain text, failing if it contains images
    fun renderText(scope: ScriptScope): String {
        return render(scope).joinToString("") { part ->
            when (part) {
                is TemplatePart.Text -> part.text
                is TemplatePart.Image -> throw TemplateException("Images can't be used here", 0)
                }
            }
        }

    private inline fun <T> evaluate(line: Int, block: () -> T): T {
        try {
            return block()
            }
        catch (e: org.mozilla.javascript.RhinoException) {
            throw TemplateException(e.details(), line)
            }
        }

    companion object {

        fun parse(source: String): Template = Template(TemplateParser(source).parse())
        }
    }
