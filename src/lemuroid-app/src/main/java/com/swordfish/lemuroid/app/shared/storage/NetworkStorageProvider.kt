package com.swordfish.lemuroid.app.shared.storage

import android.content.Context
import android.net.Uri
import androidx.leanback.preference.LeanbackPreferenceFragment
import com.swordfish.lemuroid.app.mobile.feature.catalog.NetworkClient
import com.swordfish.lemuroid.app.mobile.feature.catalog.SmbClient
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.library.db.entity.DataFile
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.RomFiles
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.lib.storage.StorageProvider
import com.swordfish.lemuroid.lib.storage.source.NetworkProtocol
import com.swordfish.lemuroid.lib.storage.source.SourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File

class NetworkStorageProvider(
    private val context: Context,
    private val sourceRepository: SourceRepository,
) : StorageProvider {
    override val id: String = "network"

    override val name: String = context.getString(R.string.smb_storage)

    override val uriSchemes: List<String> = listOf("sftp", "dav", "davs")

    override val prefsFragmentClass: Class<LeanbackPreferenceFragment>? = null

    override val enabledByDefault: Boolean = true

    private val networkClient = NetworkClient(SmbClient())

    override fun listBaseStorageFiles(): Flow<List<BaseStorageFile>> = flow {
        val files = mutableListOf<BaseStorageFile>()
        getNetworkSources().forEach { source ->
            val result =
                runBlocking {
                    networkClient.listFiles(source.protocol, source.server, source.basePath, source.credentials)
                }

            result.onSuccess { remoteFiles ->
                files += remoteFiles.map {
                    BaseStorageFile(
                        name = it.name,
                        size = it.size,
                        uri = buildUri(source.protocol, source.server, it.path),
                        path = it.relativePath,
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "NETWORK scan failed for ${source.protocol}://${source.server}${source.basePath}")
            }
        }
        emit(files)
    }

    override fun getInputStream(uri: Uri): ByteArrayInputStream? {
        val config = findConfigForUri(uri) ?: return null
        val path = uri.path ?: return null

        val bytes =
            runBlocking {
                networkClient.readFileBytes(config.protocol, config.server, path, config.credentials)
                    .getOrNull()
            } ?: return null

        return ByteArrayInputStream(bytes)
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

    override fun getGameRomFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val uri = Uri.parse(game.fileUri)
        val config = findConfigForUri(uri) ?: return RomFiles.Standard(emptyList())
        val path = uri.path ?: return RomFiles.Standard(emptyList())

        val cacheDir = File(context.cacheDir, NETWORK_CACHE_SUBFOLDER).also { it.mkdirs() }
        val cacheFile = File(cacheDir, "game_${game.id}_${game.fileName}")

        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            val bytes =
                runBlocking {
                    networkClient.readFileBytes(config.protocol, config.server, path, config.credentials)
                        .getOrNull()
                } ?: return RomFiles.Standard(emptyList())
            cacheFile.outputStream().use { it.write(bytes) }
        }

        return RomFiles.Standard(listOf(cacheFile))
    }

    override suspend fun delete(game: Game): Boolean {
        val uri = Uri.parse(game.fileUri)
        val config = findConfigForUri(uri) ?: return false
        val path = uri.path ?: return false

        val deleted = networkClient.deleteFile(config.protocol, config.server, path, config.credentials).isSuccess

        val cacheFile = File(File(context.cacheDir, NETWORK_CACHE_SUBFOLDER), "game_${game.id}_${game.fileName}")
        if (cacheFile.exists()) {
            cacheFile.delete()
        }

        return deleted
    }

    private fun getNetworkSources(): List<NetworkSourceConfig> {
        return sourceRepository.getCustomSources()
            .asSequence()
            .filter { it.type == com.swordfish.lemuroid.lib.storage.source.SourceType.SMB }
            .mapNotNull { source ->
                val uri = runCatching { Uri.parse(source.path) }.getOrNull() ?: return@mapNotNull null
                val protocol =
                    when (uri.scheme?.lowercase()) {
                        "sftp" -> NetworkProtocol.SFTP
                        "davs" -> NetworkProtocol.WEBDAV
                        "dav" -> NetworkProtocol.WEBDAV_HTTP
                        else -> null
                    } ?: return@mapNotNull null

                val authority = uri.authority.orEmpty().ifBlank { return@mapNotNull null }
                val basePath = uri.path.orEmpty().ifBlank { "/" }
                NetworkSourceConfig(protocol, authority, basePath, source.credentials)
            }
            .toList()
    }

    private fun findConfigForUri(uri: Uri): NetworkSourceConfig? {
        val protocol =
            when (uri.scheme?.lowercase()) {
                "sftp" -> NetworkProtocol.SFTP
                "davs" -> NetworkProtocol.WEBDAV
                "dav" -> NetworkProtocol.WEBDAV_HTTP
                else -> return null
            }
        val authority = uri.authority ?: return null
        val path = uri.path.orEmpty()

        return getNetworkSources()
            .filter { it.protocol == protocol && it.server.equals(authority, ignoreCase = true) }
            .maxByOrNull { config ->
                val base = config.basePath.trimEnd('/')
                if (path.startsWith(base)) base.length else -1
            }
            ?.takeIf { config ->
                val base = config.basePath.trimEnd('/')
                base.isEmpty() || path.startsWith(base)
            }
    }

    private fun buildUri(protocol: NetworkProtocol, server: String, path: String): Uri {
        val scheme =
            when (protocol) {
                NetworkProtocol.SMB -> "smb"
                NetworkProtocol.SFTP -> "sftp"
                NetworkProtocol.WEBDAV -> "davs"
                NetworkProtocol.WEBDAV_HTTP -> "dav"
            }

        return Uri.Builder()
            .scheme(scheme)
            .authority(server)
            .path(if (path.startsWith("/")) path else "/$path")
            .build()
    }

    private data class NetworkSourceConfig(
        val protocol: NetworkProtocol,
        val server: String,
        val basePath: String,
        val credentials: com.swordfish.lemuroid.lib.storage.source.SourceCredentials?,
    )

    companion object {
        const val NETWORK_CACHE_SUBFOLDER = "network-storage-games"
    }
}
