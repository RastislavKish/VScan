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

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.image.ImageData
import com.rastislavkish.vscan.core.imagesource.StaticImageSource
import com.rastislavkish.vscan.core.script.ScriptEngine

class TemplateTest {

    private fun render(source: String, variables: Map<String, Any?> = mapOf()): String {
        return Template.parse(source).renderText(ScriptEngine.newScope(variables))
        }

    private fun assertParseError(source: String, line: Int, messagePart: String) {
        try {
            Template.parse(source)
            fail("\"$source\" should not parse")
            }
        catch (e: TemplateException) {
            assertEquals(line, e.line)
            assertTrue("\"${e.message}\" should mention \"$messagePart\"", e.message!!.contains(messagePart))
            }
        }

    @Test
    fun plainTextIncludingSingleBracesIsKept() {
        val text="Answer as {\"line\": 42} or {}.\nNothing else."

        assertEquals(text, render(text))
        }

    @Test
    fun substitutesExpressions() {
        assertEquals("Find keys, 2 of them.", render("Find {{ object }}, {{ count }} of them.", mapOf("object" to "keys", "count" to 2)))
        assertEquals("BUS 42", render("{{ name.toUpperCase() }} {{ 40+2 }}", mapOf("name" to "bus")))
        assertEquals("[]", render("[{{ nothing }}]", mapOf("nothing" to null)))
        }

    @Test
    fun closingBracesInsideStringsDontEndExpressions() {
        assertEquals("a}}b %}", render("{{ 'a}}b' }} {{ \"%}\" }}"))
        }

    @Test
    fun conditionals() {
        val template="{% if n>1 %}many{% elif n==1 %}one{% else %}none{% endif %}"

        assertEquals("many", render(template, mapOf("n" to 5)))
        assertEquals("one", render(template, mapOf("n" to 1)))
        assertEquals("none", render(template, mapOf("n" to 0)))
        assertEquals("", render("{% if false %}x{% endif %}"))
        }

    @Test
    fun nestedBlocks() {
        val template="{% for row in rows %}{% if row.length %}{% for x in row %}{{ x }}{% endfor %};{% endif %}{% endfor %}"

        assertEquals("ab;c;", render(template, mapOf("rows" to listOf(listOf("a", "b"), listOf<String>(), listOf("c")))))
        }

    @Test
    fun loops() {
        val variables=mapOf("items" to listOf("a", "b", "c"))

        assertEquals("a,b,c,", render("{% for item in items %}{{ item }},{% endfor %}", variables))
        assertEquals("0:a 1:b 2:c ", render("{% for i, item in items %}{{ i }}:{{ item }} {% endfor %}", variables))
        assertEquals("", render("{% for item in [] %}x{% endfor %}"))
        }

    @Test
    fun loopVariablesDontLeak() {
        assertEquals("x", render("{% for item in ['a'] %}{% endfor %}{{ typeof item=='undefined' ? 'x' : 'leaked' }}"))
        assertEquals("outer", render("{% for v in ['inner'] %}{% endfor %}{{ v }}", mapOf("v" to "outer")))
        }

    @Test
    fun standaloneBlockLinesDisappear() {
        val template="""Find {{ object }} in the image.
            |{% if history.length > 0 %}
            |Previously you said:
            |{% for answer in history %}
            |- {{ answer }}
            |{% endfor %}
            |{% endif %}
            |Answer as {"found": true}
            |""".trimMargin()

        assertEquals(
            "Find keys in the image.\nPreviously you said:\n- a\n- b\nAnswer as {\"found\": true}\n",
            render(template, mapOf("object" to "keys", "history" to listOf("a", "b"))),
            )
        assertEquals(
            "Find keys in the image.\nAnswer as {\"found\": true}\n",
            render(template, mapOf("object" to "keys", "history" to listOf<String>())),
            )
        }

    @Test
    fun indentedStandaloneTagsDisappearToo() {
        assertEquals("a\nb\n", render("a\n    {% if true %}  \nb\n  {% endif %}\n"))
        }

    @Test
    fun standaloneTagsAtTheEdges() {
        assertEquals("x\n", render("{% if true %}\nx\n{% endif %}"))
        }

    @Test
    fun inlineTagsKeepTheirLine() {
        assertEquals("a b c\nd", render("a {% if true %}b{% endif %} c\nd"))
        assertEquals("ok\n", render("{% if true %}ok{% endif %}\n"))
        }

    @Test
    fun expressionLinesAreNotRemoved() {
        assertEquals("\nx\n", render("{{ '' }}\nx\n"))
        }

    @Test
    fun commentsAreRemoved() {
        assertEquals("a b\nc\n", render("a {# inline #}b\n{# alone on its line #}\nc\n"))
        }

    @Test
    fun imagesBecomeParts() {
        val image=ImageData.fromEncoded(byteArrayOf(1))
        val source=StaticImageSource(image)
        val template=Template.parse("Look: {{ image }} and {{ camera }}!")

        val parts=template.render(ScriptEngine.newScope(mapOf("image" to image, "camera" to source)))

        assertEquals(listOf(
            TemplatePart.Text("Look: "),
            TemplatePart.Image(image),
            TemplatePart.Text(" and "),
            TemplatePart.Image(source),
            TemplatePart.Text("!"),
            ), parts)
        }

    @Test(expected=TemplateException::class)
    fun renderTextRejectsImages() {
        render("{{ image }}", mapOf("image" to ImageData.fromEncoded(byteArrayOf(1))))
        }

    @Test
    fun templatesRenderRepeatedly() {
        val template=Template.parse("{{ n }}")

        assertEquals("1", template.renderText(ScriptEngine.newScope(mapOf("n" to 1))))
        assertEquals("2", template.renderText(ScriptEngine.newScope(mapOf("n" to 2))))
        }

    @Test
    fun reportsParseErrorsWithLines() {
        assertParseError("a\nb {{ x", 2, "Unclosed {{")
        assertParseError("{{  }}", 1, "Empty")
        assertParseError("x\n{% unknown %}", 2, "Unknown tag")
        assertParseError("{% if x %}\nnever closed", 1, "endif")
        assertParseError("{% for x in xs %}", 1, "endfor")
        assertParseError("\n\n{% endif %}", 3, "without a matching block")
        assertParseError("{% if x %}{% else %}{% elif y %}{% endif %}", 1, "Expected {% endif %}")
        assertParseError("{% for x of xs %}{% endfor %}", 1, "for item in array")
        assertParseError("{% if %}{% endif %}", 1, "needs a condition")
        assertParseError("{# unclosed", 1, "Unclosed {#")
        }

    @Test
    fun reportsRenderErrorsWithLines() {
        try {
            render("fine\n{{ missing }}")
            fail()
            }
        catch (e: TemplateException) {
            assertEquals(2, e.line)
            assertTrue(e.message!!.contains("missing"))
            }

        try {
            render("\n{% for x in 42 %}{% endfor %}")
            fail()
            }
        catch (e: TemplateException) {
            assertEquals(2, e.line)
            assertTrue(e.message!!.contains("not an array"))
            }
        }

    }
