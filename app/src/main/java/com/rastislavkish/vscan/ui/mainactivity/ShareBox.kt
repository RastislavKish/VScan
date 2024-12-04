/*
* Copyright (C) 2024 Rastislav Kish
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

import android.content.Context

class ShareBox {

    private var image: ByteArray?=null

    fun pushImage(image: ByteArray) {
        this.image=image
        }
    fun popImage(): ByteArray? {
        val image=this.image
        this.image=null

        return image
        }

    companion object {

        private var instance: ShareBox?=null

        fun getInstance(context: Context): ShareBox {
            if (instance==null) {
                instance=ShareBox()
                }

            return instance!!
            }
        }
    }
