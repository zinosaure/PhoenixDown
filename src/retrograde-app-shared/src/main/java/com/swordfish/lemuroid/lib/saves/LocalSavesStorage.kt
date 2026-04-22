package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Default [SavesStorage] implementation using the local file-system via [DirectoriesManager].
 * This is the original behavior: saves go to Android's external files directory.
 */
class LocalSavesStorage(private val directoriesManager: DirectoriesManager) : SavesStorage {

    private fun savesDir() = directoriesManager.getSavesDirectory()
    private fun statesDir() = directoriesManager.getStatesDirectory()
    private fun statsPreviewDir() = directoriesManager.getStatesPreviewDirectory()

    /** Resolve the absolute [File] for a given logical [savePath].
     *  Paths starting with "states/" go into the states directory; others go into saves. */
    private fun resolve(savePath: String): File {
        val parts = savePath.split("/", limit = 2)
        return when (parts[0]) {
            "states" -> File(statesDir(), parts.getOrElse(1) { "" })
            "previews" -> File(statsPreviewDir(), parts.getOrElse(1) { "" })
            else -> File(savesDir(), parts.getOrElse(1) { "" })
        }
    }

    override suspend fun readBytes(savePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolve(savePath)
        if (file.exists() && file.length() > 0) file.readBytes() else null
    }

    override suspend fun writeBytes(savePath: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = resolve(savePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override suspend fun info(savePath: String): SaveInfo = withContext(Dispatchers.IO) {
        val file = resolve(savePath)
        if (file.exists() && file.length() > 0) {
            SaveInfo(true, file.lastModified())
        } else {
            SaveInfo(false, 0L)
        }
    }

    override suspend fun delete(savePath: String) = withContext(Dispatchers.IO) {
        resolve(savePath).delete()
        Unit
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val dir = resolve(directory)
        if (dir.exists() && dir.isDirectory) dir.list()?.toList() ?: emptyList() else emptyList()
    }
}
