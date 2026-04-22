package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.lib.storage.source.RomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import android.net.Uri

class WebDavSavesStorage(
    private val source: RomSource,
    private val useSsl: Boolean = true,
) : SavesStorage {

    private val client = OkHttpClient()

    private val TAG = "WebDavSavesStorage"

    private fun baseUrl(): String {
        val raw = source.path.trim().removeSuffix("/")
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw
        }

        val parsed = runCatching { Uri.parse(raw) }.getOrNull()
        if (parsed != null && !parsed.scheme.isNullOrBlank() && !parsed.authority.isNullOrBlank()) {
            val scheme = when (parsed.scheme?.lowercase()) {
                "davs" -> "https"
                "dav" -> "http"
                else -> if (useSsl) "https" else "http"
            }
            val basePath = parsed.path.orEmpty().trimEnd('/')
            return if (basePath.isBlank()) {
                "$scheme://${parsed.authority}"
            } else {
                "$scheme://${parsed.authority}$basePath"
            }
        }

        return if (useSsl) "https://$raw" else "http://$raw"
    }

    private fun fullUrl(savePath: String): String {
        val normalized = savePath.trimStart('/')
        return "${baseUrl()}/$normalized"
    }

    private fun authHeader(): String? {
        val user = source.credentials?.username?.trim().orEmpty()
        return if (user.isNotBlank()) Credentials.basic(user, source.credentials?.password.orEmpty()) else null
    }

    private fun Request.Builder.withAuth(): Request.Builder = apply {
        authHeader()?.let { header("Authorization", it) }
    }

    override suspend fun readBytes(savePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(fullUrl(savePath)).get().withAuth().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext null
                if (!response.isSuccessful) throw IOException("WebDAV GET failed (${response.code}): $savePath")
                response.body?.bytes()?.takeIf { it.isNotEmpty() }
            }
        }.onFailure { Timber.tag(TAG).w(it, "readBytes failed: %s", savePath) }.getOrNull()
    }

    override suspend fun writeBytes(savePath: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // Ensure parent collection exists via MKCOL chain
        ensureParentCollections(savePath)

        val body = bytes.toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder().url(fullUrl(savePath)).put(body).withAuth().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("WebDAV PUT failed (${response.code}): $savePath")
        }
        Unit
    }

    override suspend fun info(savePath: String): SaveInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(fullUrl(savePath)).head().withAuth().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) SaveInfo(exists = true, date = System.currentTimeMillis())
                else SaveInfo(exists = false, date = 0L)
            }
        }.getOrElse { SaveInfo(exists = false, date = 0L) }
    }

    override suspend fun delete(savePath: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(fullUrl(savePath)).delete().withAuth().build()
        runCatching {
            client.newCall(request).execute().use { /* consume */ }
        }.onFailure { Timber.tag(TAG).w(it, "delete failed: %s", savePath) }
        Unit
    }

    override suspend fun list(directory: String): List<String> = withContext(Dispatchers.IO) {
        val body = "".toRequestBody(null)
        val request = Request.Builder()
            .url(fullUrl(directory))
            .method("PROPFIND", body)
            .header("Depth", "1")
            .withAuth()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) return@withContext emptyList()
                val xml = response.body?.string() ?: return@withContext emptyList()
                parseHrefNames(xml, directory)
            }
        }.getOrDefault(emptyList())
    }

    /** Parse `<D:href>` entries from a WebDAV PROPFIND response, returning file names only. */
    private fun parseHrefNames(xml: String, baseDirectory: String): List<String> {
        val base = fullUrl(baseDirectory).trimEnd('/')
        return Regex("""<[^:>]*:href[^>]*>([^<]+)</[^:>]*:href>""")
            .findAll(xml)
            .mapNotNull { it.groupValues[1].trim().removeSuffix("/").let { href ->
                val decoded = java.net.URLDecoder.decode(href, "UTF-8")
                val name = decoded.substringAfterLast('/')
                // Skip the directory itself
                if (decoded.trimEnd('/') == base.substringAfter("://").substringAfter("/").let { "/" + it } ||
                    decoded.trimEnd('/') == base) null
                else name.ifBlank { null }
            }}
            .toList()
    }

    private fun ensureParentCollections(savePath: String) {
        val segments = savePath.trimStart('/').split("/").dropLast(1)
        var current = baseUrl()
        for (segment in segments) {
            current += "/$segment"
            val body = "".toRequestBody(null)
            val request = Request.Builder().url(current).method("MKCOL", body).withAuth().build()
            runCatching { client.newCall(request).execute().use { /* 201 created or 405 already exists — both OK */ } }
        }
    }
}
