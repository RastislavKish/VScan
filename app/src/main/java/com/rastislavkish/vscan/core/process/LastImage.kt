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

import java.time.LocalDateTime

import com.rastislavkish.vscan.core.image.ImageData

// The image the foreground process took last, or the one shared into the app, which configs can use as $lastImage
class LastImage {

    class Taken(
        val image: ImageData,
        val timestamp: LocalDateTime,
        )

    @Volatile var taken: Taken?=null
    private set

    fun set(image: ImageData) {
        taken=Taken(image, LocalDateTime.now())
        }
    fun clear() {
        taken=null
        }
    }
