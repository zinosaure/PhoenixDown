package com.swordfish.lemuroid.app.mobile.feature.catalog

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.swordfish.lemuroid.lib.storage.source.NetworkProtocol
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials as NetworkCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Properties
import java.util.Vector
import javax.xml.parsers.DocumentBuilderFactory

class NetworkClient(
    private val smbClient: SmbClient = SmbClient(),
) {
    data class NetworkScannedFile(
        val name: String,
        val path: String,
        val relativePath: String,
        val size: Long,
    )

    suspend fun testConnection(
        protocol: NetworkProtocol,
        server: String,
        path: String,
        credentials: NetworkCredentials?,
    ): Result<Unit> = when (protocol) {
        NetworkProtocol.SMB -> {
            val shareName = path.removePrefix("/").substringBefore("/")
            if (shareName.isBlank()) smbClient.testServerConnection(server, credentials)
            else smbClient.testConnection(server, shareName, credentials).map { Unit }
        }
        NetworkProtocol.SFTP -> testSftpConnection(server, credentials)
        NetworkProtocol.WEBDAV -> testWebDavConnection(server, path, credentials)
    }

    suspend fun listDirectories(
        protocol: NetworkProtocol,
        server: String,
        path: String,
        credentials: NetworkCredentials?,
    ): Result<List<NetworkDirectoryEntry>> = when (protocol) {
        NetworkProtocol.SMB -> {
            val segments = path.removePrefix("/").split('/').filter { it.isNotBlank() }
            val shareName = segments.firstOrNull()
            val subPath = segments.drop(1).joinToString("/")
            if (shareName == null) {
                smbClient.listShares(server, credentials)
            } else {
                smbClient.listDirectories(server, shareName, subPath, credentials)
            }
        }
        NetworkProtocol.SFTP -> listSftpDirectories(server, path, credentials)
        NetworkProtocol.WEBDAV -> listWebDavDirectories(server, path, credentials)
    }

    suspend fun listFiles(
        protocol: NetworkProtocol,
        server: String,
        path: String,
        credentials: NetworkCredentials?,
    ): Result<List<NetworkScannedFile>> = when (protocol) {
        NetworkProtocol.SMB -> {
            val segments = path.removePrefix("/").split('/').filter { it.isNotBlank() }
            val shareName = segments.firstOrNull()
            val subPath = segments.drop(1).joinToString("/")
            if (shareName == null) {
                Result.success(emptyList())
            } else {
                smbClient.listFiles(server, shareName, subPath, credentials).map { files ->
                    files.map {
                        NetworkScannedFile(
                            name = it.name,
                            path = "/$shareName/${it.path.replace('\\', '/').trimStart('/')}",
                            relativePath = it.relativePath,
                            size = it.size,
                        )
                    }
                }
            }
        }
        NetworkProtocol.SFTP -> listSftpFiles(server, path, credentials)
        NetworkProtocol.WEBDAV -> listWebDavFiles(server, path, credentials)
    }

    private suspend fun testSftpConnection(server: String, credentials: NetworkCredentials?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = parseEndpoint(server, 22)
            val user = credentials?.username?.trim().orEmpty()
            if (user.isBlank()) {
                throw IllegalArgumentException("SFTP requires username/password")
            }
            val password = credentials?.password.orEmpty()
            withSftpSession(endpoint.host, endpoint.port, user, password) { _, _ -> Unit }
        }
    }

    private suspend fun listSftpDirectories(
        server: String,
        path: String,
        credentials: NetworkCredentials?,
    ): Result<List<NetworkDirectoryEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = parseEndpoint(server, 22)
            val user = credentials?.username?.trim().orEmpty()
            if (user.isBlank()) {
                throw IllegalArgumentException("SFTP requires username/password")
            }
            val password = credentials?.password.orEmpty()
            val targetPath = if (path.isBlank()) "/" else path

            withSftpSession(endpoint.host, endpoint.port, user, password) { _, sftp ->
                @Suppress("UNCHECKED_CAST")
                val entries = sftp.ls(targetPath) as Vector<ChannelSftp.LsEntry>
                entries
                    .filter { it.filename != "." && it.filename != ".." && it.attrs.isDir }
                    .map {
                        val fullPath = if (targetPath == "/") "/${it.filename}" else "$targetPath/${it.filename}"
                        NetworkDirectoryEntry(name = it.filename, path = fullPath)
                    }
                    .sortedBy { it.name.lowercase() }
            }
        }
    }

    private suspend fun listSftpFiles(
        server: String,
        path: String,
        credentials: NetworkCredentials?,
    ): Result<List<NetworkScannedFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = parseEndpoint(server, 22)
            val user = credentials?.username?.trim().orEmpty()
            if (user.isBlank()) {
                throw IllegalArgumentException("SFTP requires username/password")
            }
            val password = credentials?.password.orEmpty()
            val startPath = normalizeAbsolutePath(path)
            val files = mutableListOf<NetworkScannedFile>()

            withSftpSession(endpoint.host, endpoint.port, user, password) { _, sftp ->
                scanSftpDirectory(sftp, startPath, startPath, files, 0)
            }

            files.sortedBy { it.name.lowercase() }
        }
    }

    private fun scanSftpDirectory(
        sftp: ChannelSftp,
        rootPath: String,
        currentPath: String,
        files: MutableList<NetworkScannedFile>,
        depth: Int,
    ) {
        if (depth > MAX_NETWORK_SCAN_DEPTH) return

        @Suppress("UNCHECKED_CAST")
        val entries = sftp.ls(currentPath) as Vector<ChannelSftp.LsEntry>
        entries
            .filter { it.filename != "." && it.filename != ".." }
            .forEach { entry ->
                val childPath = if (currentPath == "/") "/${entry.filename}" else "$currentPath/${entry.filename}"
                if (entry.attrs.isDir) {
                    scanSftpDirectory(sftp, rootPath, childPath, files, depth + 1)
                } else {
                    val relativePath = childPath.removePrefix(rootPath).trimStart('/').ifBlank { entry.filename }
                    files += NetworkScannedFile(
                        name = entry.filename,
                        path = childPath,
                        relativePath = relativePath,
                        size = entry.attrs.size,
                    )
                }
            }
    }

    private fun <T> withSftpSession(
        host: String,
        port: Int,
        username: String,
        password: String,
        block: (Session, ChannelSftp) -> T,
    ): T {
        val jsch = JSch()
        val session = jsch.getSession(username, host, port)
        session.setPassword(password)
        val props = Properties()
        props["StrictHostKeyChecking"] = "no"
        session.setConfig(props)
        session.timeout = 20_000
        session.connect(20_000)

        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(20_000)

        return try {
            block(session, channel)
        } finally {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
        }
    }

    private suspend fun testWebDavConnection(
        server: String,
        path: String,
        credentials: NetworkCredentials?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeWebDavBaseUrl(server)
            val fullUrl = joinUrl(baseUrl, path)
            val response = buildWebDavClient()
                .newCall(
                    Request.Builder()
                        .url(fullUrl)
                        .method("PROPFIND", "".toRequestBody(null))
                        .header("Depth", "0")
                        .apply {
                            val user = credentials?.username?.trim().orEmpty()
                            if (user.isNotBlank()) {
                                header("Authorization", Credentials.basic(user, credentials?.password.orEmpty()))
                            }
                        }
                        .build(),
                )
                .execute()

            response.use {
                if (!it.isSuccessful && it.code !in 200..299 && it.code != 207) {
                    throw IllegalStateException("WebDAV request failed (${it.code})")
                }
            }
        }
    }

    private suspend fun listWebDavDirectories(
        server: String,
        path: String,
        credentials: NetworkCredentials?,
    ): Result<List<NetworkDirectoryEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeWebDavBaseUrl(server)
            val fullUrl = joinUrl(baseUrl, path)
            val request = Request.Builder()
                .url(fullUrl)
                .method("PROPFIND", "".toRequestBody(null))
                .header("Depth", "1")
                .apply {
                    val user = credentials?.username?.trim().orEmpty()
                    if (user.isNotBlank()) {
                        header("Authorization", Credentials.basic(user, credentials?.password.orEmpty()))
                    }
                }
                .build()

            val response = buildWebDavClient().newCall(request).execute()
            response.use {
                if (!it.isSuccessful && it.code != 207) {
                    throw IllegalStateException("WebDAV request failed (${it.code})")
                }

                val body = it.body?.bytes() ?: ByteArray(0)
                parseWebDavEntries(fullUrl, body)
                    .filter { it.isDirectory }
                    .map { NetworkDirectoryEntry(name = it.name, path = it.path) }
            }
        }
    }

    private suspend fun listWebDavFiles(
        server: String,
        path: String,
        credentials: NetworkCredentials?,
    ): Result<List<NetworkScannedFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeWebDavBaseUrl(server)
            val rootPath = normalizeAbsolutePath(path)
            val files = mutableListOf<NetworkScannedFile>()

            scanWebDavDirectory(
                baseUrl = baseUrl,
                rootPath = rootPath,
                currentPath = rootPath,
                credentials = credentials,
                files = files,
                depth = 0,
            )

            files.sortedBy { it.name.lowercase() }
        }
    }

    private fun scanWebDavDirectory(
        baseUrl: String,
        rootPath: String,
        currentPath: String,
        credentials: NetworkCredentials?,
        files: MutableList<NetworkScannedFile>,
        depth: Int,
    ) {
        if (depth > MAX_NETWORK_SCAN_DEPTH) return

        val fullUrl = joinUrl(baseUrl, currentPath)
        val request = Request.Builder()
            .url(fullUrl)
            .method("PROPFIND", "".toRequestBody(null))
            .header("Depth", "1")
            .apply {
                val user = credentials?.username?.trim().orEmpty()
                if (user.isNotBlank()) {
                    header("Authorization", Credentials.basic(user, credentials?.password.orEmpty()))
                }
            }
            .build()

        buildWebDavClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) {
                throw IllegalStateException("WebDAV request failed (${response.code})")
            }

            val body = response.body?.bytes() ?: ByteArray(0)
            parseWebDavEntries(fullUrl, body).forEach { entry ->
                if (entry.path.trimEnd('/') == currentPath.trimEnd('/')) return@forEach

                if (entry.isDirectory) {
                    scanWebDavDirectory(baseUrl, rootPath, entry.path, credentials, files, depth + 1)
                } else {
                    val relativePath = entry.path.removePrefix(rootPath).trimStart('/').ifBlank { entry.name }
                    files += NetworkScannedFile(
                        name = entry.name,
                        path = entry.path,
                        relativePath = relativePath,
                        size = entry.size,
                    )
                }
            }
        }
    }

    private fun parseWebDavEntries(requestUrl: String, body: ByteArray): List<WebDavEntry> {
        if (body.isEmpty()) return emptyList()

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(body))
        val responseNodes = doc.getElementsByTagNameNS("*", "response")
        val basePath = URI(requestUrl).path.trimEnd('/').ifBlank { "/" }

        val results = mutableListOf<WebDavEntry>()
        for (i in 0 until responseNodes.length) {
            val responseNode = responseNodes.item(i)
            val hrefNode = findFirstDescendant(responseNode, "href") ?: continue
            val href = hrefNode.textContent.orEmpty().trim()
            if (href.isBlank()) continue

            val decodedPath = runCatching { URI(requestUrl).resolve(href).path }
                .getOrElse { runCatching { URI(href).path }.getOrDefault(href) }
            if (decodedPath.isBlank() || decodedPath.trimEnd('/') == basePath.trimEnd('/')) continue

            val collectionNode = findFirstDescendant(responseNode, "collection")
            val isDirectory = collectionNode != null

            val normalized = decodedPath.trimEnd('/')
            val name = normalized.substringAfterLast('/').ifBlank { normalized }
            if (name.isBlank()) continue

            val size = findFirstDescendant(responseNode, "getcontentlength")
                ?.textContent
                ?.trim()
                ?.toLongOrNull()
                ?: 0L

            results += WebDavEntry(name = name, path = normalized, isDirectory = isDirectory, size = size)
        }

        return results.distinctBy { it.path }.sortedBy { it.name.lowercase() }
    }

    private fun findFirstDescendant(node: Node, localName: String): Node? {
        val children = node.childNodes ?: return null
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.localName == localName) return child
            val nested = findFirstDescendant(child, localName)
            if (nested != null) return nested
        }
        return null
    }

    private fun normalizeWebDavBaseUrl(server: String): String {
        val raw = server.trim().removeSuffix("/")
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> "https://$raw"
        }
    }

    private fun joinUrl(base: String, path: String): String {
        val normalizedPath = if (path.isBlank() || path == "/") "" else path.removePrefix("/")
        return if (normalizedPath.isBlank()) base else "$base/$normalizedPath"
    }

    private fun normalizeAbsolutePath(path: String): String {
        if (path.isBlank() || path == "/") return "/"
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun parseEndpoint(server: String, defaultPort: Int): Endpoint {
        val value = server.trim()
        val idx = value.lastIndexOf(':')
        if (idx <= 0 || idx == value.lastIndex) {
            return Endpoint(value, defaultPort)
        }

        val host = value.substring(0, idx)
        val port = value.substring(idx + 1).toIntOrNull()
        return if (port != null && port in 1..65535) Endpoint(host, port) else Endpoint(value, defaultPort)
    }

    private fun buildWebDavClient(): OkHttpClient = OkHttpClient.Builder().build()

    private data class Endpoint(val host: String, val port: Int)

    private data class WebDavEntry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
    )

    companion object {
        private const val MAX_NETWORK_SCAN_DEPTH = 20
    }
}
