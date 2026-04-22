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

    // Longest prefix match across all sources — works for both network and local paths.
    val match = sources
        .filter { src -> src.path.isNotBlank() }
        .mapNotNull { src ->
            val srcPath = src.path.trimEnd('/')
            val normalizedUri = fileUri.trimEnd('/')
            // Check exact match or path boundary (avoids /roms matching /roms2)
            if (normalizedUri == srcPath || normalizedUri.startsWith("$srcPath/")) {
                src to srcPath.length
            } else {
                null
            }
        }
        .maxByOrNull { (_, len) -> len }
        ?.first

    if (match != null) return match.name

    // Fallback for SAF content:// URIs where the stored path may differ from fileUri encoding
    val uri = try { Uri.parse(fileUri) } catch (_: Exception) { return null }
    if (uri.scheme == "content") {
        val decodedGame = Uri.decode(uri.encodedPath ?: "")
        val gameDocId = decodedGame.substringAfter("/document/").substringAfter("/tree/")
        val contentMatch = sources.filter { it.type == SourceType.LOCAL }.maxByOrNull { src ->
            val srcUri = runCatching { Uri.parse(src.path) }.getOrNull() ?: return@maxByOrNull -1
            if (srcUri.scheme != "content") return@maxByOrNull -1
            val srcDocId = Uri.decode(srcUri.encodedPath ?: "")
                .substringAfter("/tree/").substringAfter("/document/")
            if (gameDocId.startsWith(srcDocId)) srcDocId.length else -1
        }
        if (contentMatch != null) {
            val srcDocId = runCatching {
                val srcUri = Uri.parse(contentMatch.path)
                Uri.decode(srcUri.encodedPath ?: "").substringAfter("/tree/").substringAfter("/document/")
            }.getOrDefault("")
            if (gameDocId.startsWith(srcDocId) && srcDocId.isNotBlank()) return contentMatch.name
        }
    }

    return sourceBadgeFor(fileUri)
}

