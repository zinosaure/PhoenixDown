package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SourceType

/**
 * Carries the user-configured ROM sources through the composition tree.
 * Screens that have access to a ViewModel providing sources should wrap their
 * content with `CompositionLocalProvider(LocalRomSources provides sources)`.
 */
val LocalRomSources = compositionLocalOf<List<RomSource>> { emptyList() }

/**
 * Returns the user-configured source name for a given fileUri, by matching it
 * against the known sources provided via [LocalRomSources].
 *
 * For network sources (SMB/SFTP), the source path is stored as a plain string
 * (e.g. "smb://192.168.1.1/share/gba"), so a simple startsWith check suffices.
 * The longest matching source path wins (handles two shares on the same NAS).
 *
 * For SAF content:// URIs, we compare decoded document IDs as a prefix.
 *
 * Falls back to [sourceBadgeFor] if no named source matches.
 */
fun resolveSourceName(fileUri: String, sources: List<RomSource>): String? {
    if (sources.isEmpty()) return sourceBadgeFor(fileUri)
    val uri = try { Uri.parse(fileUri) } catch (_: Exception) { return null }
    return when (uri.scheme?.lowercase()) {
        "smb", "sftp" -> {
            val expectedScheme = uri.scheme?.lowercase()
            val gamePathCandidates = normalizedNetworkPathCandidates(uri)
            sources
                .filter { it.type == SourceType.SMB }
                .mapNotNull { src ->
                    val srcUri = try { Uri.parse(src.path) } catch (_: Exception) { return@mapNotNull null }
                    if (!isEquivalentNetworkScheme(srcUri.scheme, expectedScheme)) return@mapNotNull null
                    if (!hasSameNetworkAuthority(srcUri, uri)) return@mapNotNull null

                    val srcPathCandidates = normalizedNetworkPathCandidates(srcUri)
                    val bestMatchLength = gamePathCandidates
                        .asSequence()
                        .flatMap { gamePath ->
                            srcPathCandidates.asSequence().mapNotNull { srcPath ->
                                prefixMatchLength(gamePath, srcPath)
                            }
                        }
                        .maxOrNull() ?: return@mapNotNull null

                    src to bestMatchLength
                }
                .maxByOrNull { (_, matchLen) -> matchLen }
                ?.first?.name
                ?: sourceBadgeFor(fileUri)
        }
        "content" -> {
            val decodedGame = Uri.decode(uri.encodedPath ?: "")
            val gameDocId = decodedGame.substringAfter("/document/").substringAfter("/tree/")
            sources
                .filter { it.type == SourceType.LOCAL }
                .mapNotNull { src ->
                    val srcUri = runCatching { Uri.parse(src.path) }.getOrNull() ?: return@mapNotNull null
                    if (srcUri.scheme != "content") return@mapNotNull null
                    val srcDocId = Uri.decode(srcUri.encodedPath ?: "")
                        .substringAfter("/tree/").substringAfter("/document/")
                    if (gameDocId.startsWith(srcDocId)) src to srcDocId.length else null
                }
                .maxByOrNull { (_, len) -> len }
                ?.first?.name
                ?: sources.firstOrNull { it.type == SourceType.LOCAL }?.name
                ?: "Local"
        }
        "file" -> {
            val filePath = uri.path ?: return "Local"
            sources
                .filter { it.type == SourceType.LOCAL }
                .mapNotNull { src ->
                    val srcPath = src.path.removePrefix("file://")
                    if (filePath.startsWith(srcPath)) src to srcPath.length else null
                }
                .maxByOrNull { (_, len) -> len }
                ?.first?.name
                ?: "Local"
        }
        else -> null
    }
}

private fun isEquivalentNetworkScheme(sourceScheme: String?, targetScheme: String?): Boolean {
    val src = sourceScheme?.lowercase().orEmpty()
    val dst = targetScheme?.lowercase().orEmpty()
    if (src == dst) return true
    return (src == "https" && dst == "davs") ||
        (src == "davs" && dst == "https") ||
        (src == "http" && dst == "dav") ||
        (src == "dav" && dst == "http")
}

private fun hasSameNetworkAuthority(source: Uri, target: Uri): Boolean {
    val sourceAuthority = parseAuthority(source)
    val targetAuthority = parseAuthority(target)

    if (sourceAuthority.host.isBlank() || targetAuthority.host.isBlank()) return false
    if (!sourceAuthority.host.equals(targetAuthority.host, ignoreCase = true)) return false

    return sourceAuthority.port == null ||
        targetAuthority.port == null ||
        sourceAuthority.port == targetAuthority.port
}

private data class AuthorityParts(val host: String, val port: Int?)

private fun parseAuthority(uri: Uri): AuthorityParts {
    val hostFromApi = uri.host?.trim().orEmpty()
    val portFromApi = uri.port.takeIf { it >= 0 }
    if (hostFromApi.isNotBlank()) {
        return AuthorityParts(hostFromApi, portFromApi)
    }

    val raw = uri.authority?.trim().orEmpty()
    if (raw.isBlank()) return AuthorityParts("", null)

    val noUserInfo = raw.substringAfterLast('@').trim()
    if (noUserInfo.startsWith("[") && noUserInfo.contains("]")) {
        val closing = noUserInfo.indexOf(']')
        val host = noUserInfo.substring(1, closing)
        val port = noUserInfo.substring(closing + 1).removePrefix(":").toIntOrNull()
        return AuthorityParts(host, port)
    }

    val colon = noUserInfo.lastIndexOf(':')
    if (colon > 0) {
        val host = noUserInfo.substring(0, colon)
        val port = noUserInfo.substring(colon + 1).toIntOrNull()
        return AuthorityParts(host, port)
    }

    return AuthorityParts(noUserInfo, null)
}

private fun normalizedNetworkPathCandidates(uri: Uri): List<String> {
    val decoded = normalizePathForMatching(uri.path.orEmpty())
    val encodedDecoded = normalizePathForMatching(Uri.decode(uri.encodedPath.orEmpty()))
    return listOf(decoded, encodedDecoded).distinct()
}

private fun normalizePathForMatching(path: String): String {
    val withLeadingSlash = if (path.startsWith("/")) path else "/$path"
    val collapsed = withLeadingSlash.replace('\\', '/').replace(Regex("/+"), "/")
    return collapsed.trimEnd('/').ifBlank { "/" }
}

private fun prefixMatchLength(gamePath: String, sourcePath: String): Int? {
    if (sourcePath == "/") return 1
    if (!gamePath.startsWith(sourcePath)) return null
    val boundary = gamePath.length == sourcePath.length || gamePath[sourcePath.length] == '/'
    return if (boundary) sourcePath.length else null
}

