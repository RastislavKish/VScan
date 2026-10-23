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
package com.rastislavkish.vscan.core.files

import java.io.IOException

// A tree of files addressed by paths like configs/find/config.toml
// Throws IOException when the tree can't be accessed
interface FileTree {

    data class Entry(
        val name: String,
        val isDirectory: Boolean,
        val lastModified: Long,
        val size: Long,
        )

    // The entries of a directory, null when it doesn't exist
    fun list(path: String): List<Entry>?
    // Null when the file doesn't exist
    fun read(path: String): ByteArray?
    // Creates the missing directories, and returns the entry of the written file
    fun write(path: String, data: ByteArray): Entry
    // Deletes a file or a directory with its contents, nothing when it doesn't exist
    fun delete(path: String)

    fun exists(path: String): Boolean {
        val parent=path.substringBeforeLast('/', "")
        val name=path.substringAfterLast('/')
        return list(parent)?.any { it.name==name } ?: false
        }

    companion object {

        fun join(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"
        }
    }

class FileTreeException(message: String): IOException(message)
