package com.swordfish.lemuroid.lib.storage.smb

import android.content.Context
import android.net.Uri
import androidx.leanback.preference.LeanbackPreferenceFragment
import com.swordfish.lemuroid.common.kotlin.extractEntryToFile
import com.swordfish.lemuroid.common.kotlin.isZipped
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.library.db.entity.DataFile
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.RomFiles
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.lib.storage.StorageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Storage provider for SMB/NAS shares.
 * Allows scanning and playing ROMs from network shares.
 */
class SmbStorageProvider(
    private val context: Context,
) : StorageProvider {
    
    private val smbClient = SmbClient()
    
    override val id: String = "smb"

    override val name: String = context.getString(R.string.smb_storage)

    override val uriSchemes = listOf("smb")

    override val prefsFragmentClass: Class<LeanbackPreferenceFragment>? = null

    override val enabledByDefault = false

    /**
     * List all ROM files from the configured SMB share.
     * Returns an error state if connection fails (no silent fallback).
     */
    override fun listBaseStorageFiles(): Flow<List<BaseStorageFile>> = flow {
        val config = getSmbConfig()
        if (config == null) {
            Timber.w("SMB not configured, returning empty list")
            emit(emptyList())
            return@flow
        }
        
        Timber.d("Scanning SMB: ${config.server}/${config.share}/${config.path}")
        
        val result = smbClient.listFilesRaw(
            server = config.server,
            share = config.share,
            path = config.path,
            credentials = config.credentials
        )
        
        result.onSuccess { files ->
            val baseFiles = files.map { smbFile ->
                BaseStorageFile(
                    name = smbFile.name,
                    size = smbFile.size,
                    uri = buildSmbUri(config, smbFile.path),
                    path = smbFile.relativePath
                )
            }
            emit(baseFiles)
        }.onFailure { error ->
            Timber.e(error, "Failed to list SMB files")
            // Emit empty with error flag - UI layer will detect and show error
            throw SmbConnectionException(
                "Could not connect to ${config.server}: ${error.message}",
                error
            )
        }
    }

    override fun getStorageFile(baseStorageFile: BaseStorageFile): StorageFile? {
        // Note: ZIP content inspection is NOT done here - it's too expensive.
        // It's only done as last resort fallback in LibretroDBMetadataProvider
        return StorageFile(
            name = baseStorageFile.name,
            size = baseStorageFile.size,
            crc = null, // SMB files don't have pre-computed CRC
            uri = baseStorageFile.uri,
            path = baseStorageFile.path
        )
    }
    
    /**
     * Try to read the first file entry from a ZIP archive to get its extension.
     * This helps detect the ROM type for compressed files.
     * Should only be called as a last resort when other detection methods fail.
     */
    fun getArchiveInternalFileName(baseStorageFile: BaseStorageFile): String? {
        return getArchiveInfo(baseStorageFile)?.internalFileName
    }
    
    /**
     * Extract CRC32 and internal filename from a ZIP archive.
     * Decompresses in memory if file size < MAX_ARCHIVE_SIZE_FOR_CRC, skips CRC otherwise.
     * 
     * @return ArchiveInfo with internal filename and optionally CRC32
     */
    fun getArchiveInfo(baseStorageFile: BaseStorageFile): ArchiveInfo? {
        val config = getSmbConfig() ?: return null
        val uri = baseStorageFile.uri
        val smbPath = uri.path?.removePrefix("/") ?: return null
        
        return try {
            runBlocking {
                val result = smbClient.getInputStream(
                    server = config.server,
                    share = config.share,
                    remotePath = smbPath,
                    credentials = config.credentials
                )
                
                result.getOrNull()?.inputStream?.use { inputStream ->
                    java.util.zip.ZipInputStream(inputStream).use { zipStream ->
                        // Get first entry (usually the ROM file)
                        val entry = zipStream.nextEntry
                        if (entry != null && !entry.isDirectory) {
                            val internalFileName = entry.name
                            
                            // Only calculate CRC if file is small enough for memory
                            val crc = if (baseStorageFile.size <= MAX_ARCHIVE_SIZE_FOR_CRC) {
                                calculateCRC32(zipStream)
                            } else {
                                timber.log.Timber.d("Skipping CRC for large archive: ${baseStorageFile.name} (${baseStorageFile.size} bytes)")
                                null
                            }
                            
                            ArchiveInfo(internalFileName, crc)
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to read archive contents for: ${baseStorageFile.name}")
            null
        }
    }
    
    /**
     * Calculate CRC32 of a stream (decompressed ZIP entry content)
     */
    private fun calculateCRC32(inputStream: java.io.InputStream): String {
        val crc = java.util.zip.CRC32()
        val buffer = ByteArray(8192)
        var bytesRead: Int
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            crc.update(buffer, 0, bytesRead)
        }
        
        // Return CRC as uppercase hex string (format used by LibretroDB)
        return String.format("%08X", crc.value)
    }
    
    /**
     * Information extracted from an archive file
     */
    data class ArchiveInfo(
        val internalFileName: String,
        val crc: String?  // CRC32 as hex string, or null if file too large
    )
    
    companion object {
        const val SMB_CACHE_SUBFOLDER = "smb-storage-games"
        
        // Max archive size for in-memory CRC calculation (500 MB)
        const val MAX_ARCHIVE_SIZE_FOR_CRC = 500L * 1024 * 1024
    }

    override fun getInputStream(uri: Uri): InputStream? {
        val config = getSmbConfig() ?: return null
        
        // Extract path from smb:// URI
        val smbPath = uri.path?.removePrefix("/") ?: return null
        
        return runBlocking {
            smbClient.getInputStream(
                server = config.server,
                share = config.share,
                remotePath = smbPath,
                credentials = config.credentials
            ).getOrNull()?.inputStream
        }
    }

    override fun getGameRomFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val config = getSmbConfig() ?: run {
            Timber.e("SMB_ROM: getSmbConfig() returned null!")
            return RomFiles.Standard(emptyList())
        }
        
        // For SMB, we need to download the file to cache first
        val cacheFile = getCacheFileForGame(game)
        Timber.d("SMB_ROM: cacheFile=${cacheFile.absolutePath}, exists=${cacheFile.exists()}, size=${if (cacheFile.exists()) cacheFile.length() else 0}")
        
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            // Download from SMB to cache (also re-download if file is empty/corrupt)
            Timber.d("SMB_ROM: Downloading from SMB...")
            runBlocking {
                val uri = Uri.parse(game.fileUri)
                // Normalize backslashes to forward slashes for robust path handling
                val fullPath = uri.path?.removePrefix("/")?.replace("\\", "/") ?: run {
                    Timber.e("SMB_ROM: Could not parse path from URI: ${game.fileUri}")
                    return@runBlocking
                }
                
                // The URI path includes the share (e.g., "almacen/juegos/file.zip")
                // But downloadFile already uses config.share, so we need to remove it from the path
                val remotePath = if (fullPath.startsWith(config.share + "/")) {
                    fullPath.removePrefix(config.share + "/")
                } else {
                    fullPath
                }
                
                Timber.d("SMB_ROM: server=${config.server}, share=${config.share}, remotePath=$remotePath")
                
                cacheFile.parentFile?.mkdirs()
                try {
                    cacheFile.outputStream().use { output ->
                        smbClient.downloadFile(
                            server = config.server,
                            share = config.share,
                            remotePath = remotePath,
                            outputStream = output,
                            credentials = config.credentials
                        )
                    }
                    Timber.d("SMB_ROM: Download complete, file size=${cacheFile.length()}")
                } catch (e: Exception) {
                    Timber.e(e, "SMB_ROM: Download failed")
                    cacheFile.delete() // Delete corrupt/empty file
                }
            }
        } else {
            Timber.d("SMB_ROM: Using cached file, size=${cacheFile.length()}")
        }
        
        // Handle zipped files - Extract if needed
        // Some cores might not handle ZIP paths directly, or we might need to extract the ROM
        if (cacheFile.exists() && cacheFile.extension.equals("zip", ignoreCase = true)) {
            // We'll determine the extracted file based on the entry found
            var extractedFile: File? = null
            
            try {
                // First pass: find the entry and extract it
                ZipInputStream(cacheFile.inputStream()).use { stream ->
                    var entry = stream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Use the entry name for the extracted file
                            // But keep it in the cache directory
                            // Sanitize name to avoid path traversal AND remove spaces/special chars
                            // Some cores fail with spaces in paths
                            val entryExtension = File(entry.name).extension.lowercase(Locale.US)
                            val extension = if (entryExtension.isBlank()) "rom" else entryExtension
                            val safeName = buildExtractedFileName(game, extension)
                            extractedFile = File(cacheFile.parentFile, safeName)
                            
                            // Only extract if not already extracted or if extracted file is empty
                            if (!extractedFile!!.exists() || extractedFile!!.length() == 0L) {
                                Timber.d("SMB_ROM: Found entry: ${entry.name}, extracting to ${extractedFile!!.name}...")
                                // Manual extraction to avoid stream conflict
                                extractedFile!!.outputStream().use { output ->
                                    stream.copyTo(output)
                                }
                                Timber.d("SMB_ROM: Extraction complete")
                            } else {
                                Timber.d("SMB_ROM: File already extracted: ${extractedFile!!.name}")
                            }
                            break
                        }
                        entry = stream.nextEntry
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "SMB_ROM: Extraction failed")
            }
            
            if (extractedFile != null && extractedFile!!.exists() && extractedFile!!.length() > 0) {
                Timber.d("SMB_ROM: Returning extracted file: ${extractedFile!!.absolutePath}")
                return RomFiles.Standard(listOf(extractedFile!!))
            }
        }
        
        // Return cached file (fallback)
        Timber.d("SMB_ROM: Returning cacheFile, exists=${cacheFile.exists()}, size=${if (cacheFile.exists()) cacheFile.length() else 0}")
        return RomFiles.Standard(listOf(cacheFile))
    }
    
    private fun getCacheFileForGame(game: Game): File {
        val cacheDir = File(context.cacheDir, SMB_CACHE_SUBFOLDER)
        cacheDir.mkdirs()
        return File(cacheDir, "${game.id}_${game.fileName}")
    }

    private fun buildExtractedFileName(game: Game, extension: String): String {
        val uriHash = game.fileUri.hashCode().toUInt().toString(16)
        return "game_${game.id}_$uriHash.$extension"
    }

    private fun deleteExtractedFilesForGame(game: Game, cacheDir: File?) {
        if (cacheDir == null || !cacheDir.exists()) {
            return
        }

        val prefix = "game_${game.id}_"
        cacheDir.listFiles()
            ?.filter { file -> file.isFile && file.name.startsWith(prefix) }
            ?.forEach { file ->
                val deleted = file.delete()
                Timber.d("SMB_ROM: Deleted extracted file: ${file.name}, success=$deleted")
            }
    }
    
    private fun buildSmbUri(config: SmbLibraryConfig, smbPath: String): Uri {
        return Uri.Builder()
            .scheme("smb")
            .authority(config.server)
            .path("/${config.share}/$smbPath")
            .build()
    }
    
    override suspend fun delete(game: Game): Boolean {
        val config = getSmbConfig() ?: return false
        
        // 1. Delete from SMB
        val uri = Uri.parse(game.fileUri)
        val fullPath = uri.path?.removePrefix("/")?.replace("\\", "/") ?: return false
        
        val remotePath = if (fullPath.startsWith(config.share + "/")) {
            fullPath.removePrefix(config.share + "/")
        } else {
            fullPath
        }
        
        Timber.i("SMB_ROM: Deleting remote file: $remotePath")
        val result = smbClient.deleteFile(
            server = config.server,
            share = config.share,
            remotePath = remotePath,
            credentials = config.credentials
        )
        
        if (result.isFailure) {
            Timber.e(result.exceptionOrNull(), "SMB_ROM: Failed to delete remote file")
            return false
        }
        
        // 2. Delete from local cache
        val cacheFile = getCacheFileForGame(game)
        if (cacheFile.exists()) {
            val deleted = cacheFile.delete()
            Timber.d("SMB_ROM: Deleted cache file: ${cacheFile.name}, success=$deleted")
        }
        
        deleteExtractedFilesForGame(game, cacheFile.parentFile)
        
        return true
    }

    /**
     * Get SMB configuration from SharedPreferences
     */
    private fun getSmbConfig(): SmbLibraryConfig? {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        
        val server = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, null)
        val share = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, null)
        val path = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, "") ?: ""
        val username = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, null)
        val password = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, null)
        
        if (server.isNullOrBlank() || share.isNullOrBlank()) {
            return null
        }
        
        val credentials = if (!username.isNullOrBlank()) {
            SmbCredentials(username, password ?: "")
        } else {
            null
        }
        
        return SmbLibraryConfig(server, share, path, credentials)
    }
}

/**
 * SMB Library configuration
 */
data class SmbLibraryConfig(
    val server: String,
    val share: String,
    val path: String,
    val credentials: SmbCredentials?
)

/**
 * Exception thrown when SMB connection fails.
 * UI layer should catch this and show an error dialog.
 */
class SmbConnectionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
