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

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

// A folder the user picked through the storage access framework
// Each directory is listed with a single query, and the documents' ids are cached by path
class DocumentFileTree(
    context: Context,
    val treeUri: Uri,
    ): FileTree {

    private val resolver=context.contentResolver
    private val rootId=DocumentsContract.getTreeDocumentId(treeUri)
    private val ids=mutableMapOf("" to rootId)

    private class Child(val id: String, val entry: FileTree.Entry)

    @Synchronized
    override fun list(path: String): List<FileTree.Entry>? {
        val id=documentId(path) ?: return null
        return children(path, id)?.map { it.entry }
        }

    @Synchronized
    override fun read(path: String): ByteArray? {
        val id=documentId(path) ?: return null
        return guard(path) {
            resolver.openInputStream(documentUri(id))?.use { it.readBytes() }
            }
        }

    @Synchronized
    override fun write(path: String, data: ByteArray): FileTree.Entry {
        val parentPath=path.substringBeforeLast('/', "")
        val name=path.substringAfterLast('/')
        val parentId=directory(parentPath)

        return guard(path) {
            val existing=children(parentPath, parentId)?.firstOrNull { it.entry.name==name }
            val id=existing?.id
            ?: DocumentsContract.createDocument(resolver, documentUri(parentId), "application/octet-stream", name)?.let { DocumentsContract.getDocumentId(it) }
            ?: throw FileTreeException("Can't create $path")
            ids[path]=id

            // Truncated, so a shorter content leaves nothing of the old one
            resolver.openOutputStream(documentUri(id), "wt")?.use { it.write(data) }
            ?: throw FileTreeException("Can't write $path")

            children(parentPath, parentId)?.firstOrNull { it.entry.name==name }?.entry
            ?: FileTree.Entry(name, false, System.currentTimeMillis(), data.size.toLong())
            }
        }

    @Synchronized
    override fun delete(path: String) {
        val id=documentId(path) ?: return
        guard(path) {
            DocumentsContract.deleteDocument(resolver, documentUri(id))
            }
        ids.keys.removeAll { it==path || it.startsWith("$path/") }
        }

    // The id of an existing document, null when it doesn't exist
    private fun documentId(path: String): String? {
        ids[path]?.let { return it }

        val parentPath=path.substringBeforeLast('/', "")
        val parentId=documentId(parentPath) ?: return null
        val name=path.substringAfterLast('/')
        return children(parentPath, parentId)?.firstOrNull { it.entry.name==name }?.id
        }

    // The id of a directory, created when it doesn't exist
    private fun directory(path: String): String {
        documentId(path)?.let { return it }

        val parentPath=path.substringBeforeLast('/', "")
        val parentId=directory(parentPath)
        val name=path.substringAfterLast('/')
        val uri=guard(path) {
            DocumentsContract.createDocument(resolver, documentUri(parentId), Document.MIME_TYPE_DIR, name)
            } ?: throw FileTreeException("Can't create $path")

        val id=DocumentsContract.getDocumentId(uri)
        ids[path]=id
        return id
        }

    private fun children(path: String, id: String): List<Child>? {
        val uri=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        val projection=arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE)

        val cursor: Cursor=guard(path) { resolver.query(uri, projection, null, null, null) } ?: return null
        val result=mutableListOf<Child>()
        cursor.use {
            while (it.moveToNext()) {
                val childId=it.getString(0)
                val name=it.getString(1) ?: continue
                val isDirectory=it.getString(2)==Document.MIME_TYPE_DIR
                val lastModified=if (it.isNull(3)) 0L else it.getLong(3)
                val size=if (it.isNull(4)) 0L else it.getLong(4)

                ids[FileTree.join(path, name)]=childId
                result.add(Child(childId, FileTree.Entry(name, isDirectory, lastModified, size)))
                }
            }
        return result
        }

    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    // Permission losses and missing storage surface as exceptions of various types
    private inline fun <T> guard(path: String, block: () -> T): T {
        try {
            return block()
            }
        catch (e: FileTreeException) {
            throw e
            }
        catch (e: Exception) {
            throw FileTreeException("Can't access $path in the linked folder: ${e.message}")
            }
        }
    }
