package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.lib.storage.smb.SmbClient
import com.swordfish.lemuroid.lib.storage.smb.SmbCredentials
import com.swordfish.lemuroid.lib.storage.source.RomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * [SavesStorage] backed by an SMB share (a [RomSource] of type SMB).
 *
 * Logical paths like "states/coreName/game.state" are stored under
 * a "saves/" directory in the configured SMB share.
 *
 * @param source  The SMB [RomSource] to write saves into.
 */
class SmbSavesStorage(private val source: RomSource) : SavesStorage {

    private val smbClient = SmbClient()

    private val smbCredentials: SmbCredentials? = source.credentials?.let {
        SmbCredentials(it.username, it.password)
    }

    companion object {
        private const val TAG = "SmbSavesStorage"
    }

    /** Parse the [RomSource] path (smb://server/share/subpath) into components. */
    private data class SmbCoords(val server: String, val share: String, val basePath: String)

    private fun coords(): SmbCoords {
        val raw = source.path.removePrefix("smb://")
        val slashIdx = raw.indexOf('/')
        if (slashIdx <= 0) throw IOException("Invalid SMB path: ${source.path}")
        val server = raw.substring(0, slashIdx)
        val rest = raw.substring(slashIdx + 1)
        val parts = rest.split("/", limit = 2)
        val share = parts[0]
        val basePath = if (parts.size > 1) parts[1].trimEnd('/') else ""
        return SmbCoords(server, share, basePath)
    }

    /** Prepend the SMB base path to a logical save path. */
    private fun remotePath(savePath: String): String {
        val c = coords()
        return if (c.basePath.isEmpty()) "saves/$savePath" else "${c.basePath}/saves/$savePath"
    }

    override suspend fun readBytes(savePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val c = coords()
        val remote = remotePath(savePath)
        val buffer = ByteArrayOutputStream()
        val result = smbClient.downloadFile(c.server, c.share, remote, buffer, smbCredentials)
        if (result.isFailure) {
            Timber.tag(TAG).w(result.exceptionOrNull(), "readBytes failed for smb://%s/%s/%s", c.server, c.share, remote)
        }
        if (result.isSuccess && buffer.size() > 0) buffer.toByteArray() else null
    }

    override suspend fun writeBytes(savePath: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val c = coords()
        val remote = remotePath(savePath)
        bytes.inputStream().use { input ->
            smbClient.uploadFile(c.server, c.share, remote, input, smbCredentials)
                .onFailure {
                    Timber.tag(TAG).w(it, "writeBytes failed for smb://%s/%s/%s", c.server, c.share, remote)
                }
                .getOrThrow()
        }
        Unit
    }

    override suspend fun info(savePath: String): SaveInfo = withContext(Dispatchers.IO) {
        val c = coords()
        val remote = remotePath(savePath)
        val exists = smbClient.fileExists(c.server, c.share, remote, smbCredentials)
        SaveInfo(exists, if (exists) System.currentTimeMillis() else 0L)
    }

    override suspend fun delete(savePath: String) = withContext(Dispatchers.IO) {
        val c = coords()
        smbClient.deleteFile(c.server, c.share, remotePath(savePath), smbCredentials)
            .onFailure {
                Timber.tag(TAG).w(it, "delete failed for smb://%s/%s/%s", c.server, c.share, remotePath(savePath))
            }
        Unit
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val c = coords()
        val remote = remotePath(directory).trimEnd('/')
        smbClient.listFilesRaw(c.server, c.share, remote, smbCredentials)
            .onFailure {
                Timber.tag(TAG).w(it, "list failed for smb://%s/%s/%s", c.server, c.share, remote)
            }
            .getOrDefault(emptyList())
            .map { it.name }
    }
}
