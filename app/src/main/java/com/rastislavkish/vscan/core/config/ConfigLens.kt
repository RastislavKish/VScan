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
package com.rastislavkish.vscan.core.config

import kotlinx.serialization.*

import com.rastislavkish.vscan.core.imagesource.Lens
import com.rastislavkish.vscan.core.imagesource.LensOperation

@Serializable
enum class LensTarget {
    // Only the image parameter
    @SerialName("image") IMAGE,
    // Every ImageSource parameter, and every Any parameter given one
    @SerialName("all") ALL,
    }

// A lens the config applies to whatever image sources it's called with
@Serializable
data class ConfigLens(
    val operations: List<LensOperation> = listOf(),
    val appliesTo: LensTarget=LensTarget.IMAGE,
    ) {

    val lens: Lens
    get() = Lens(operations)

    val isEmpty: Boolean
    get() = operations.isEmpty()

    fun appliesTo(parameter: Parameter): Boolean {
        if (isEmpty || (parameter.type!=ParameterType.IMAGE_SOURCE && parameter.type!=ParameterType.ANY))
        return false

        return when (appliesTo) {
            LensTarget.IMAGE -> parameter.name==Parameter.IMAGE_PARAMETER_NAME
            LensTarget.ALL -> true
            }
        }
    }
