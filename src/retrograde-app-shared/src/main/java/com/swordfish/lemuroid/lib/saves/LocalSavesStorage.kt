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
    private fun legacySavesDir() = directoriesManager.getLegacySavesDirectory()
    private fun legacyStatesDir() = directoriesManager.getLegacyStatesDirectory()
    private fun legacyStatesPreviewDir() = directoriesManager.getLegacyStatesPreviewDirectory()

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

    private fun resolveLegacy(savePath: String): File {
        val parts = savePath.split("/", limit = 2)
        return when (parts[0]) {
            "states" -> File(legacyStatesDir(), parts.getOrElse(1) { "" })
            "previews" -> File(legacyStatesPreviewDir(), parts.getOrElse(1) { "" })
            else -> File(legacySavesDir(), parts.getOrElse(1) { "" })
        }
    }

    override suspend fun readBytes(savePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolve(savePath)
        when {
            file.exists() && file.length() > 0 -> file.readBytes()
            else -> {
                val legacy = resolveLegacy(savePath)
                if (legacy.exists() && legacy.length() > 0) legacy.readBytes() else null
            }
        }
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
            val legacy = resolveLegacy(savePath)
            SaveInfo(legacy.exists() && legacy.length() > 0, legacy.lastModified())
        }
    }

    override suspend fun delete(savePath: String) = withContext(Dispatchers.IO) {
        resolve(savePath).delete()
        resolveLegacy(savePath).delete()
        Unit
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val dir = resolve(directory)
        val legacyDir = resolveLegacy(directory)
        val current = if (dir.exists() && dir.isDirectory) dir.list()?.toList() ?: emptyList() else emptyList()
        val legacy = if (legacyDir.exists() && legacyDir.isDirectory) legacyDir.list()?.toList() ?: emptyList() else emptyList()
        (current + legacy).distinct()
    }
}
