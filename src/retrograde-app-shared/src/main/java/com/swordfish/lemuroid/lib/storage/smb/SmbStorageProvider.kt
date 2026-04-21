package com.swordfish.lemuroid.lib.storage.smb

import android.content.Context
import android.net.Uri
import androidx.leanback.preference.LeanbackPreferenceFragment
import com.swordfish.lemuroid.common.kotlin.extractEntryToFile
import com.swordfish.lemuroid.common.kotlin.isZipped
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.library.db.entity.DataFile
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.RomFiles
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.lib.storage.StorageProvider
import com.swordfish.lemuroid.lib.storage.source.SourceRepository
import com.swordfish.lemuroid.lib.storage.source.SmbSourceConfig
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
 * Reads all SMB source configurations from [SourceRepository] — no SharedPreferences reads.
 */
class SmbStorageProvider(
    private val context: Context,
    private val sourceRepository: SourceRepository,
) : StorageProvider {

    private val smbClient = SmbClient()

    override val id: String = "smb"
    override val name: String = context.getString(R.string.smb_storage)
    override val uriSchemes = listOf("smb")
    override val prefsFragmentClass: Class<LeanbackPreferenceFragment>? = null
    override val enabledByDefault = false

    override fun listBaseStorageFiles(): Flow<List<BaseStorageFile>> = flow {
        val configs = sourceRepository.getSmbSourceConfigs()
        if (configs.isEmpty()) {
            Timber.w("SMB: no sources configured")
            return@flow
        }

        configs.forEach { config ->
            Timber.d("SMB: scanning ${config.server}/${config.share}/${config.path}")
            val result = smbClient.listFilesRaw(
                server = config.server,
                share = config.share,
                path = config.path,
                credentials = config.toSmbCredentials(),
            )
            result.onSuccess { files ->
                emit(files.map { f ->
                    BaseStorageFile(
                        name = f.name,
                        size = f.size,
                        uri = buildSmbUri(config, f.path),
                        path = f.relativePath,
                    )
                })
            }.onFailure { e ->
                Timber.e(e, "SMB: failed to list ${config.server}/${config.share}")
            }
        }
    }

    override fun getStorageFile(baseStorageFile: BaseStorageFile): StorageFile? {
        return StorageFile(
            name = baseStorageFile.name,
            size = baseStorageFile.size,
            crc = null,
            uri = baseStorageFile.uri,
            path = baseStorageFile.path,
        )
    }

    override fun getInputStream(uri: Uri): InputStream? {
        val config = resolveConfigForUri(uri) ?: return null
        val smbPath = uri.path?.removePrefix("/") ?: return null
        return runBlocking {
            smbClient.getInputStream(
                server = config.server,
                share = config.share,
                remotePath = smbPath,
                credentials = config.toSmbCredentials(),
            ).getOrNull()?.inputStream
        }
    }

    override fun getGameRomFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val config = resolveConfigForUri(Uri.parse(game.fileUri)) ?: run {
            Timber.e("SMB_ROM: no config resolved for ${game.fileUri}")
            return RomFiles.Standard(emptyList())
        }

        val cacheFile = getCacheFileForGame(game)
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            runBlocking {
                downloadGameToCache(game, config, cacheFile)
            }
        }

        if (cacheFile.exists() && cacheFile.extension.equals("zip", ignoreCase = true)) {
            val extracted = extractZip(game, cacheFile)
            if (extracted != null) return RomFiles.Standard(listOf(extracted))
        }

        return RomFiles.Standard(listOf(cacheFile))
    }

    override suspend fun delete(game: Game): Boolean {
        val config = resolveConfigForUri(Uri.parse(game.fileUri)) ?: return false
        val uri = Uri.parse(game.fileUri)
        val fullPath = uri.path?.removePrefix("/")?.replace("\\", "/") ?: return false
        val remotePath = if (fullPath.startsWith(config.share + "/")) {
            fullPath.removePrefix(config.share + "/")
        } else {
            fullPath
        }
        val result = smbClient.deleteFile(
            server = config.server,
            share = config.share,
            remotePath = remotePath,
            credentials = config.toSmbCredentials(),
        )
        if (result.isFailure) {
            Timber.e(result.exceptionOrNull(), "SMB: delete failed for $remotePath")
            return false
        }
        getCacheFileForGame(game).delete()
        deleteExtractedFilesForGame(game)
        return true
    }

    // -----------------------------------------------------------------------
    // Archive helpers
    // -----------------------------------------------------------------------

    fun getArchiveInfo(baseStorageFile: BaseStorageFile): ArchiveInfo? {
        val config = resolveConfigForUri(baseStorageFile.uri) ?: return null
        val smbPath = baseStorageFile.uri.path?.removePrefix("/") ?: return null
        return try {
            runBlocking {
                smbClient.getInputStream(
                    server = config.server,
                    share = config.share,
                    remotePath = smbPath,
                    credentials = config.toSmbCredentials(),
                ).getOrNull()?.inputStream?.use { inputStream ->
                    java.util.zip.ZipInputStream(inputStream).use { zipStream ->
                        val entry = zipStream.nextEntry
                        if (entry != null && !entry.isDirectory) {
                            val crc = if (baseStorageFile.size <= MAX_ARCHIVE_SIZE_FOR_CRC) {
                                calculateCRC32(zipStream)
                            } else null
                            ArchiveInfo(entry.name, crc)
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "SMB: failed to read archive ${baseStorageFile.name}")
            null
        }
    }

    fun getArchiveInternalFileName(baseStorageFile: BaseStorageFile): String? =
        getArchiveInfo(baseStorageFile)?.internalFileName

    data class ArchiveInfo(val internalFileName: String, val crc: String?)

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun resolveConfigForUri(uri: Uri): SmbSourceConfig? {
        val configs = sourceRepository.getSmbSourceConfigs()
        if (configs.isEmpty()) return null
        val server = uri.authority ?: return configs.firstOrNull()
        val segments = uri.pathSegments
        val share = segments.firstOrNull() ?: return configs.firstOrNull { it.server == server }
        val pathAfterShare = if (segments.size > 1) "/" + segments.drop(1).joinToString("/") else ""
        return configs.firstOrNull {
            it.server == server && it.share == share &&
                (it.path.isBlank() || pathAfterShare.startsWith(it.path.trimEnd('/')))
        } ?: configs.firstOrNull { it.server == server && it.share == share }
    }

    private fun buildSmbUri(config: SmbSourceConfig, smbPath: String): Uri =
        Uri.Builder()
            .scheme("smb")
            .authority(config.server)
            .path("/${config.share}/$smbPath")
            .build()

    private fun getCacheFileForGame(game: Game): File {
        val dir = File(context.cacheDir, SMB_CACHE_SUBFOLDER).also { it.mkdirs() }
        return File(dir, "${game.id}_${game.fileName}")
    }

    private fun deleteExtractedFilesForGame(game: Game) {
        val dir = File(context.cacheDir, SMB_CACHE_SUBFOLDER)
        if (!dir.exists()) return
        val prefix = "game_${game.id}_"
        dir.listFiles()?.filter { it.isFile && it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    private suspend fun downloadGameToCache(game: Game, config: SmbSourceConfig, cacheFile: File) {
        val uri = Uri.parse(game.fileUri)
        val fullPath = uri.path?.removePrefix("/")?.replace("\\", "/") ?: return
        val remotePath = if (fullPath.startsWith(config.share + "/")) {
            fullPath.removePrefix(config.share + "/")
        } else {
            fullPath
        }
        cacheFile.parentFile?.mkdirs()
        try {
            cacheFile.outputStream().use { out ->
                smbClient.downloadFile(
                    server = config.server,
                    share = config.share,
                    remotePath = remotePath,
                    outputStream = out,
                    credentials = config.toSmbCredentials(),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB: download failed for ${game.fileName}")
            cacheFile.delete()
        }
    }

    private fun extractZip(game: Game, cacheFile: File): File? {
        return try {
            var result: File? = null
            ZipInputStream(cacheFile.inputStream()).use { stream ->
                val entry = stream.nextEntry
                if (entry != null && !entry.isDirectory) {
                    val ext = File(entry.name).extension.lowercase(Locale.US).ifBlank { "rom" }
                    val uriHash = game.fileUri.hashCode().toUInt().toString(16)
                    val extractedFile = File(cacheFile.parentFile, "game_${game.id}_$uriHash.$ext")
                    if (!extractedFile.exists() || extractedFile.length() == 0L) {
                        extractedFile.outputStream().use { out -> stream.copyTo(out) }
                    }
                    result = extractedFile.takeIf { it.exists() && it.length() > 0 }
                }
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "SMB: extraction failed for ${game.fileName}")
            null
        }
    }

    private fun calculateCRC32(inputStream: java.io.InputStream): String {
        val crc = java.util.zip.CRC32()
        val buffer = ByteArray(8192)
        var n: Int
        while (inputStream.read(buffer).also { n = it } != -1) crc.update(buffer, 0, n)
        return String.format("%08X", crc.value)
    }

    companion object {
        const val SMB_CACHE_SUBFOLDER = "smb-storage-games"
        const val MAX_ARCHIVE_SIZE_FOR_CRC = 500L * 1024 * 1024
    }
}

// Keep SmbLibraryConfig for backward compat with any callers outside this file
data class SmbLibraryConfig(
    val server: String,
    val share: String,
    val path: String,
    val credentials: SmbCredentials?,
)

class SmbConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Extension to convert SourceCredentials -> SmbCredentials (from SmbClient.kt)
private fun SmbSourceConfig.toSmbCredentials(): SmbCredentials? =
    credentials?.let { SmbCredentials(it.username, it.password) }
