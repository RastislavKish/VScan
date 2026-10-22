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
package com.rastislavkish.vscan.core.imagesource

import android.content.Context

import com.rastislavkish.vscan.core.camera.PhoneCamera

// The app-wide image sources
class ImageSources private constructor(context: Context) {

    val phoneCamera=PhoneCamera.getInstance(context)

    // What the default source currently stands for, to become user-configurable with global image sources
    @Volatile
    var defaultTarget: ImageSource=phoneCamera

    // The source used unless a config chooses another one
    val default: ImageSource=AliasSource { defaultTarget }

    companion object {

        private var instance: ImageSources?=null

        @Synchronized
        fun getInstance(context: Context): ImageSources {
            if (instance==null) {
                instance=ImageSources(context)
                }

            return instance!!
            }
        }
    }
