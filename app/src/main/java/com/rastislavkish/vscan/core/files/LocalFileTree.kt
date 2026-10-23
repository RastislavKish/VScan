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

import java.io.File

// A directory of the file system
class LocalFileTree(
    val root: File,
    ): FileTree {

    fun file(path: String): File = if (path.isEmpty()) root else File(root, path)

    override fun list(path: String): List<FileTree.Entry>? {
        val directory=file(path)
        if (!directory.isDirectory)
        return null

        return (directory.listFiles() ?: arrayOf()).map { entry(it) }
        }

    override fun read(path: String): ByteArray? {
        val file=file(path)
        return if (file.isFile) file.readBytes() else null
        }

    override fun write(path: String, data: ByteArray): FileTree.Entry {
        val file=file(path)
        file.parentFile?.mkdirs()
        // Written aside first, so a failure doesn't leave a half-written file
        val temporary=File(file.parentFile, ".${file.name}.tmp")
        temporary.writeBytes(data)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw FileTreeException("Can't write $path")
            }
        return entry(file)
        }

    override fun delete(path: String) {
        file(path).deleteRecursively()
        }

    private fun entry(file: File) = FileTree.Entry(file.name, file.isDirectory, file.lastModified(), if (file.isFile) file.length() else 0)
    }
