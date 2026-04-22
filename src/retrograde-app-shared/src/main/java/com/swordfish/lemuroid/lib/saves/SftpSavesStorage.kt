package com.swordfish.lemuroid.lib.saves

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.swordfish.lemuroid.lib.storage.source.RomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Properties

class SftpSavesStorage(
    private val source: RomSource,
) : SavesStorage {

    private data class Coords(
        val host: String,
        val port: Int,
        val basePath: String,
        val username: String,
        val password: String,
    )

    private fun coords(): Coords {
        val uri = android.net.Uri.parse(source.path)
        val host = uri.host ?: throw IOException("Invalid SFTP host: ${source.path}")
        val port = if (uri.port > 0) uri.port else 22
        val basePath = normalizeAbsolutePath(uri.path.orEmpty())
        val username = source.credentials?.username?.trim().orEmpty()
        if (username.isBlank()) throw IOException("SFTP username is required")
        val password = source.credentials?.password.orEmpty()

        return Coords(host, port, basePath, username, password)
    }

    private fun remotePath(savePath: String): String {
        val c = coords()
        val normalizedSavePath = savePath.trimStart('/')
        return if (c.basePath == "/") {
            "/$normalizedSavePath"
        } else {
            "${c.basePath}/$normalizedSavePath"
        }
    }

    override suspend fun readBytes(savePath: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val c = coords()
            withSftpSession(c) { sftp ->
                val output = ByteArrayOutputStream()
                sftp.get(remotePath(savePath), output)
                output.toByteArray().takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    override suspend fun writeBytes(savePath: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val c = coords()
        withSftpSession(c) { sftp ->
            val targetPath = remotePath(savePath)
            ensureParentDirectories(sftp, targetPath)
            bytes.inputStream().use { input ->
                sftp.put(input, targetPath)
            }
        }
        Unit
    }

    override suspend fun info(savePath: String): SaveInfo = withContext(Dispatchers.IO) {
        val c = coords()
        runCatching {
            withSftpSession(c) { sftp ->
                val attrs = sftp.lstat(remotePath(savePath))
                SaveInfo(exists = true, date = attrs.mTime.toLong() * 1000L)
            }
        }.getOrElse {
            SaveInfo(exists = false, date = 0L)
        }
    }

    override suspend fun delete(savePath: String) = withContext(Dispatchers.IO) {
        val c = coords()
        runCatching {
            withSftpSession(c) { sftp ->
                sftp.rm(remotePath(savePath))
            }
        }
        Unit
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val c = coords()
        runCatching {
            withSftpSession(c) { sftp ->
                @Suppress("UNCHECKED_CAST")
                val entries = sftp.ls(remotePath(directory)) as java.util.Vector<ChannelSftp.LsEntry>
                entries
                    .map { it.filename }
                    .filter { it != "." && it != ".." }
            }
        }.getOrDefault(emptyList())
    }

    private fun ensureParentDirectories(sftp: ChannelSftp, targetPath: String) {
        val parent = targetPath.substringBeforeLast('/', "")
        if (parent.isBlank() || parent == "/") return

        var current = ""
        parent.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current += "/$segment"
            try {
                sftp.cd(current)
            } catch (_: SftpException) {
                runCatching { sftp.mkdir(current) }
            }
        }
    }

    private fun <T> withSftpSession(coords: Coords, block: (ChannelSftp) -> T): T {
        val jsch = JSch()
        val session = jsch.getSession(coords.username, coords.host, coords.port)
        session.setPassword(coords.password)
        val props = Properties()
        props["StrictHostKeyChecking"] = "no"
        session.setConfig(props)
        session.timeout = 20_000
        session.connect(20_000)

        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(20_000)

        return try {
            block(channel)
        } finally {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
        }
    }

    private fun normalizeAbsolutePath(path: String): String {
        if (path.isBlank() || path == "/") return "/"
        return if (path.startsWith('/')) path.trimEnd('/') else "/${path.trimEnd('/')}"
    }
}
