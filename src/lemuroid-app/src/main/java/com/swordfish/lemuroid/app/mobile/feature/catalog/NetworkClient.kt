package com.swordfish.lemuroid.app.mobile.feature.catalog

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.swordfish.lemuroid.lib.storage.source.NetworkProtocol
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials as SmbCredentials
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
    suspend fun testConnection(
        protocol: NetworkProtocol,
        server: String,
        path: String,
        credentials: SmbCredentials?,
    ): Result<Unit> = when (protocol) {
        NetworkProtocol.SMB -> {
            val shareName = path.removePrefix("/").substringBefore("/")
            if (shareName.isBlank()) Result.failure(IllegalArgumentException("Missing SMB share name in path"))
            else smbClient.testConnection(server, shareName, credentials).map { Unit }
        }
        NetworkProtocol.SFTP -> testSftpConnection(server, credentials)
        NetworkProtocol.WEBDAV -> testWebDavConnection(server, path, credentials)
    }

    suspend fun listDirectories(
        protocol: NetworkProtocol,
        server: String,
        path: String,
        credentials: SmbCredentials?,
    ): Result<List<SmbDirectoryEntry>> = when (protocol) {
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

    private suspend fun testSftpConnection(server: String, credentials: SmbCredentials?): Result<Unit> = withContext(Dispatchers.IO) {
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
        credentials: SmbCredentials?,
    ): Result<List<SmbDirectoryEntry>> = withContext(Dispatchers.IO) {
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
                        SmbDirectoryEntry(name = it.filename, path = fullPath)
                    }
                    .sortedBy { it.name.lowercase() }
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
        credentials: SmbCredentials?,
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
        credentials: SmbCredentials?,
    ): Result<List<SmbDirectoryEntry>> = withContext(Dispatchers.IO) {
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
                parseWebDavDirectories(fullUrl, body)
            }
        }
    }

    private fun parseWebDavDirectories(requestUrl: String, body: ByteArray): List<SmbDirectoryEntry> {
        if (body.isEmpty()) return emptyList()

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(body))
        val responseNodes = doc.getElementsByTagNameNS("*", "response")
        val basePath = URI(requestUrl).path.trimEnd('/').ifBlank { "/" }

        val results = mutableListOf<SmbDirectoryEntry>()
        for (i in 0 until responseNodes.length) {
            val responseNode = responseNodes.item(i)
            val hrefNode = findFirstDescendant(responseNode, "href") ?: continue
            val href = hrefNode.textContent.orEmpty().trim()
            if (href.isBlank()) continue

            val decodedPath = runCatching { URI(href).path }.getOrDefault(href)
            if (decodedPath.isBlank() || decodedPath.trimEnd('/') == basePath.trimEnd('/')) continue

            val collectionNode = findFirstDescendant(responseNode, "collection")
            if (collectionNode == null) continue

            val normalized = decodedPath.trimEnd('/')
            val name = normalized.substringAfterLast('/').ifBlank { normalized }
            if (name.isBlank()) continue
            results += SmbDirectoryEntry(name = name, path = normalized)
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
}
