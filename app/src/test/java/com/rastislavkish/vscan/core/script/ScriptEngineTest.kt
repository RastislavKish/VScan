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

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.image.ImageData

class ScriptEngineTest {

    private fun eval(source: String, variables: Map<String, Any?> = mapOf()): Any? = ScriptEngine.newScope(variables).evaluate(source)

    private fun assertFails(source: String, messagePart: String?=null) {
        try {
            eval(source)
            fail("\"$source\" should have failed")
            }
        catch (e: ScriptException) {
            if (messagePart!=null)
            assertTrue("\"${e.message}\" should mention \"$messagePart\"", e.message!!.contains(messagePart))
            }
        }

    @Test
    fun evaluatesExpressions() {
        assertEquals(7.0, eval("1+2*3"))
        assertEquals("ab", eval("'a'+'b'"))
        assertEquals(true, eval("3>2 && 'x'.length==1"))
        }

    @Test
    fun supportsModernSyntax() {
        assertEquals(listOf(2.0, 4.0), eval("[1, 2].map(x => x*2)"))
        assertEquals("line 42", eval("const n=42; let s=`line ${'$'}{n}`; s"))
        assertEquals(true, eval("[1, 2, 3].includes(2)"))
        }

    @Test
    fun convertsVariablesIn() {
        val variables=mapOf(
            "count" to 3,
            "name" to "bus",
            "flag" to true,
            "history" to listOf("a", "b"),
            "obj" to mapOf("line" to 42, "tags" to listOf("x")),
            "nothing" to null,
            )

        assertEquals(4.0, eval("count+1", variables))
        assertEquals("BUS", eval("name.toUpperCase()", variables))
        assertEquals(false, eval("!flag", variables))
        assertEquals(2.0, eval("history.length", variables))
        assertEquals("b", eval("history[1]", variables))
        assertEquals(42.0, eval("obj.line", variables))
        assertEquals("x", eval("obj.tags[0]", variables))
        assertEquals(true, eval("nothing===null", variables))
        }

    @Test
    fun convertsResultsOut() {
        assertNull(eval("undefined"))
        assertNull(eval("null"))
        assertEquals(listOf("a", 1.0), eval("['a', 1]"))
        assertEquals(mapOf("a" to 1.0, "b" to listOf(true)), eval("({a: 1, b: [true]})"))
        }

    @Test
    fun conditionsUseJavaScriptTruthiness() {
        val scope=ScriptEngine.newScope(mapOf("empty" to "", "text" to "x", "zero" to 0, "list" to listOf<Any>()))

        assertFalse(scope.evaluateBoolean("empty"))
        assertTrue(scope.evaluateBoolean("text"))
        assertFalse(scope.evaluateBoolean("zero"))
        assertTrue(scope.evaluateBoolean("list"))
        assertFalse(scope.evaluateBoolean("null"))
        }

    @Test
    fun convertsToTextLikeJavaScript() {
        val scope=ScriptEngine.newScope()

        assertEquals("42", scope.evaluateString("42"))
        assertEquals("3.5", scope.evaluateString("3.5"))
        assertEquals("", scope.evaluateString("null"))
        assertEquals("", scope.evaluateString("undefined"))
        assertEquals("a,b", scope.evaluateString("['a', 'b']"))
        }

    @Test
    fun reportsErrors() {
        assertFails("1 +", "Syntax error")
        assertFails("missing+1", "missing")
        assertFails("throw new Error('boom')", "boom")
        }

    @Test
    fun hasNoAccessToJava() {
        assertFails("java.lang.System.exit(0)")
        assertFails("Packages.java.lang.System.exit(0)")
        assertFails("this.constructor.constructor('return java.lang.System')().exit(0)")
        }

    @Test
    fun standardObjectsCantBeAltered() {
        assertFails("Math.PI=3; Math.foo=1")
        assertFails("Array.prototype.evil=1")
        assertEquals(Math.PI, eval("Math.PI"))
        }

    @Test
    fun scopesAreIsolated() {
        eval("var leaked=1; globalLeak=2")

        assertFails("leaked")
        assertFails("globalLeak")
        }

    @Test
    fun childScopesSeeAndShadowTheirParent() {
        val parent=ScriptEngine.newScope(mapOf("a" to 1, "b" to 2))
        val child=parent.child(mapOf("b" to 20))

        assertEquals(21.0, child.evaluate("a+b"))
        assertEquals(3.0, parent.evaluate("a+b"))
        }

    @Test
    fun setUpdatesTheScope() {
        val scope=ScriptEngine.newScope()
        scope.set("lastOutput", "Bus 42")

        assertEquals("Bus 42", scope.evaluate("lastOutput"))
        }

    @Test
    fun imagesAreOpaque() {
        val image=ImageData.fromEncoded(byteArrayOf(1, 2, 3))
        val scope=ScriptEngine.newScope(mapOf("image" to image, "images" to listOf(image)))

        assertSame(image, scope.evaluate("image"))
        assertSame(image, scope.evaluate("images[0]"))
        assertEquals("[Image]", scope.evaluateString("image"))
        assertNull(scope.evaluate("image.original"))
        assertTrue(scope.evaluateBoolean("image===images[0]"))
        }

    @Test
    fun opaqueValuesKeepTheirIdentityAcrossScopes() {
        val image=ImageData.fromEncoded(byteArrayOf(1))
        val parent=ScriptEngine.newScope(mapOf("a" to image))
        val child=parent.child(mapOf("b" to image))
        child.set("c", image)

        assertTrue(child.evaluateBoolean("a===b && b===c"))
        }

    @Test
    fun functionsCantBeResults() {
        assertFails("x => x", "function")
        }

    @Test(timeout=10000)
    fun endlessLoopsTimeOut() {
        assertFails("while (true) {}", "longer than")
        }

    @Test(timeout=10000)
    fun timeoutCantBeCaught() {
        assertFails("try { while (true) {} } catch (e) {} 'escaped'", "longer than")
        }

    @Test(timeout=10000)
    fun deepRecursionFails() {
        assertFails("function f() { return f() } f()")
        }

    }
