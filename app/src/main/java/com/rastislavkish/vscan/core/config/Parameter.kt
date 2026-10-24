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

import com.rastislavkish.vscan.core.process.GlobalMemory

@Serializable
enum class ParameterType {
    @SerialName("String") STRING,
    @SerialName("Int") INT,
    @SerialName("Double") DOUBLE,
    @SerialName("Bool") BOOL,
    @SerialName("Image") IMAGE,
    @SerialName("ImageSource") IMAGE_SOURCE,
    }

// A value a config is called with, visible to its templates and expressions under its name
// Named parameters are passed as --name or -short, positional ones in their order, and a rest parameter takes the rest of the line
// The default is command-language text, so it can refer to globals (e.g. $defaultImageSource), and a parameter without one is required
@Serializable
data class Parameter(
    val name: String,
    val type: ParameterType,
    val positional: Boolean=false,
    val short: String?=null,
    val rest: Boolean=false,
    val default: String?=null,
    val description: String="",
    ) {

    companion object {

        const val IMAGE_PARAMETER_NAME="image"

        // Every config has it, a config only declares it to change its default or description
        val defaultImageParameter=Parameter(
            name=IMAGE_PARAMETER_NAME,
            type=ParameterType.IMAGE_SOURCE,
            short="i",
            default="\$${GlobalMemory.DEFAULT_IMAGE_SOURCE}",
            description="The source of the image to analyze",
            )
        }
    }
