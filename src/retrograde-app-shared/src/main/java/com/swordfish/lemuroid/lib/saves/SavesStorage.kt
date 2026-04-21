package com.swordfish.lemuroid.lib.saves

/**
 * Abstraction over the physical storage used to persist game saves (SRAM) and
 * save-states (slots, autosave, previews).
 *
 * Implementations:
 *  - [LocalSavesStorage]  — writes to an internal `java.io.File` directory (default)
 *  - [SafSavesStorage]    — writes to a SAF content:// tree (local folder chosen by user)
 *  - SmbSavesStorage      — in lemuroid-app, writes to an SMB share via jCIFS
 */
interface SavesStorage {
    /** Read bytes from [savePath]. Returns null if the file doesn't exist or is empty. */
    suspend fun readBytes(savePath: String): ByteArray?

    /** Write [bytes] to [savePath], creating parent directories as needed. */
    suspend fun writeBytes(savePath: String, bytes: ByteArray)

    /** Returns (exists, lastModified millis) for [savePath]. */
    suspend fun info(savePath: String): SaveInfo

    /** Delete [savePath]. No-op if it doesn't exist. */
    suspend fun delete(savePath: String)

    /** List all file names under the given [directory]. Returns empty list if none. */
    suspend fun list(directory: String): List<String>
}
