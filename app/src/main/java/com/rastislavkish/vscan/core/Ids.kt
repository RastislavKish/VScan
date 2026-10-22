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
package com.rastislavkish.vscan.core

import java.text.Normalizer
import java.util.Locale

// Human-readable string identifiers derived from object names, e.g. "Bus lines" -> "bus-lines"
object Ids {

    fun slugify(name: String, fallback: String): String {
        val slug=Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

        if (slug.isEmpty())
        return fallback

        // Purely numeric ids would be indistinguishable from the legacy integer ones
        if (slug.all { it.isDigit() })
        return "$fallback-$slug"

        return slug
        }

    fun unique(base: String, taken: Set<String>): String {
        if (base !in taken)
        return base

        var i=2
        while ("$base-$i" in taken)
        i++

        return "$base-$i"
        }

    fun generate(name: String, fallback: String, taken: Set<String>): String
    = unique(slugify(name, fallback), taken)
    }
