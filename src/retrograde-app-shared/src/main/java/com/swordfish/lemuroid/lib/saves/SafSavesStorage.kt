package com.swordfish.lemuroid.lib.saves

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SavesStorage] backed by a SAF (Storage Access Framework) tree URI.
 * Used when the user picks a local folder via the system folder picker.
 *
 * @param rootUri  The tree URI of the chosen folder (e.g. content://com.android.externalstorage…)
 */
class SafSavesStorage(
    private val context: Context,
    private val rootUri: Uri,
) : SavesStorage {

    private fun rootDoc(): DocumentFile =
        DocumentFile.fromTreeUri(context, rootUri)
            ?: throw IllegalStateException("Cannot access SAF tree: $rootUri")

    /** Resolve a DocumentFile for a logical path like "states/coreName/game.state". */
    private fun resolveDoc(savePath: String, create: Boolean): DocumentFile? {
        val parts = savePath.split("/").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        var current: DocumentFile = rootDoc()
        // Navigate/create intermediate directories
        for (i in 0 until parts.size - 1) {
            val segment = parts[i]
            current = current.findFile(segment)
                ?: if (create) (current.createDirectory(segment) ?: return null) else return null
        }
        // Last segment is the file name
        val fileName = parts.last()
        return if (create) {
            // Delete existing to overwrite
            current.findFile(fileName)?.delete()
            current.createFile("application/octet-stream", fileName)
        } else {
            current.findFile(fileName)
        }
    }

    override suspend fun readBytes(savePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val doc = resolveDoc(savePath, create = false) ?: return@withContext null
        if (!doc.exists() || doc.length() == 0L) return@withContext null
        context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
    }

    override suspend fun writeBytes(savePath: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val doc = resolveDoc(savePath, create = true)
            ?: throw IllegalStateException("Cannot create SAF file: $savePath")
        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("Cannot open output stream: $savePath")
        Unit
    }

    override suspend fun info(savePath: String): SaveInfo = withContext(Dispatchers.IO) {
        val doc = resolveDoc(savePath, create = false)
        if (doc != null && doc.exists() && doc.length() > 0L) {
            SaveInfo(exists = true, date = doc.lastModified())
        } else {
            SaveInfo(exists = false, date = 0L)
        }
    }

    override suspend fun delete(savePath: String) = withContext(Dispatchers.IO) {
        resolveDoc(savePath, create = false)?.delete()
        Unit
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val parts = directory.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile = rootDoc()
        for (segment in parts) {
            current = current.findFile(segment) ?: return@withContext emptyList()
        }
        current.listFiles().mapNotNull { it.name }
    }
}
