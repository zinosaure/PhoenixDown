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
 * Matching rules:
 *  - SMB: match by host
 *  - LOCAL (content://): decode and compare tree document ID prefix
 *  - file://: compare path prefix
 *
 * Falls back to [sourceBadgeFor] if no named source matches.
 */
fun resolveSourceName(fileUri: String, sources: List<RomSource>): String? {
    if (sources.isEmpty()) return sourceBadgeFor(fileUri)
    val uri = try { Uri.parse(fileUri) } catch (_: Exception) { return null }
    return when (uri.scheme?.lowercase()) {
        "smb" -> {
            val host = uri.host ?: return "SMB"
            sources.firstOrNull {
                it.type == SourceType.SMB &&
                    Uri.parse(it.path).host?.equals(host, ignoreCase = true) == true
            }?.name ?: host
        }
        "content" -> {
            // SAF document URIs look like: content://authority/document/primary%3AFOLDER%2Ffile.zip
            // SAF tree URIs look like:     content://authority/tree/primary%3AFOLDER
            // We compare the decoded document ID (after /document/ or /tree/) as a prefix.
            val decodedGame = Uri.decode(uri.encodedPath ?: "") // e.g. /document/primary:FOLDER/file.zip
            val gameDocId = decodedGame.substringAfter("/document/").substringAfter("/tree/")

            sources.firstOrNull { src ->
                if (src.type != SourceType.LOCAL) return@firstOrNull false
                val srcUri = try { Uri.parse(src.path) } catch (_: Exception) { return@firstOrNull false }
                if (srcUri.scheme != "content") return@firstOrNull false
                val decodedSrc = Uri.decode(srcUri.encodedPath ?: "")
                val srcDocId = decodedSrc.substringAfter("/tree/").substringAfter("/document/")
                gameDocId.startsWith(srcDocId)
            }?.name ?: sources.firstOrNull { it.type == SourceType.LOCAL }?.name ?: "Local"
        }
        "file" -> {
            val filePath = uri.path ?: return "Local"
            sources.firstOrNull {
                it.type == SourceType.LOCAL && filePath.startsWith(
                    it.path.removePrefix("file://")
                )
            }?.name ?: "Local"
        }
        else -> null
    }
}

