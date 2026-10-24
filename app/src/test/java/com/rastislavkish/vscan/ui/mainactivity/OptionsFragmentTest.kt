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
package com.rastislavkish.vscan.ui.mainactivity

import kotlinx.serialization.json.JsonPrimitive

import org.junit.Assert.*
import org.junit.Test

import com.rastislavkish.vscan.core.Config
import com.rastislavkish.vscan.core.config.Parameter
import com.rastislavkish.vscan.core.config.ParameterType

class OptionsFragmentTest {

    @Test
    fun replacesItems() {
        val list=listOf("a", "b", "c")
        assertEquals(listOf("a", "x", "c"), OptionsFragment.replaced(list, 1, "x"))
        assertEquals(listOf("a", "c"), OptionsFragment.replaced(list, 1, null))
        assertEquals(listOf("a", "b", "c", "d"), OptionsFragment.replaced(list, -1, "d"))
        assertEquals(list, OptionsFragment.replaced(list, -1, null))
        }

    @Test
    fun movesItems() {
        val list=listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), OptionsFragment.moved(list, 1, 0))
        assertEquals(listOf("a", "c", "b"), OptionsFragment.moved(list, 1, 2))
        }

    @Test
    fun imageParameterIsDeclaredOnlyWhenChanged() {
        val objectParameter=Parameter("object", ParameterType.STRING, positional=true)
        val config=Config(parameters=listOf(objectParameter))

        assertEquals(listOf(objectParameter), OptionsFragment.withParameters(config, config.allParameters).parameters)

        val glasses=Parameter.defaultImageParameter.copy(default="\$glasses")
        val changed=OptionsFragment.withParameters(config, OptionsFragment.replaced(config.allParameters, 0, glasses))
        assertEquals(listOf(glasses, objectParameter), changed.parameters)

        // Deleting it goes back to the implicit one
        val reset=OptionsFragment.withParameters(changed, OptionsFragment.replaced(changed.allParameters, 0, null))
        assertEquals(listOf(objectParameter), reset.parameters)
        assertEquals(Parameter.defaultImageParameter, reset.allParameters.first())
        }

    @Test
    fun listsMemoryVariablesInOrder() {
        val config=Config(memory=mapOf("b" to JsonPrimitive(1), "a" to JsonPrimitive("x")))
        assertEquals(listOf("b", "a"), OptionsFragment.memoryVariables(config).map { it.name })
        }
    }
