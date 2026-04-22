package com.swordfish.lemuroid.lib.storage.source

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Single source of truth for all ROM source configuration.
 *
 * Persists sources as a JSON array in a dedicated "rom_sources" SharedPreferences file.
 * Uses an in-process [MutableSharedFlow] so any write (from ANY instance) immediately
 * notifies all active collectors — no cross-process listener needed.
 *
 * Call [migrateOldPrefsIfNeeded] once on app startup to carry over legacy config.
 */
class SourceRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -----------------------------------------------------------------------
    // Read API
    // -----------------------------------------------------------------------

    /** All sources including the built-in Archive.org entry. */
    fun getSources(): List<RomSource> = listOf(RomSource.archiveOrg()) + getCustomSources()

    /** Only user-configured sources (LOCAL, SMB). */
    fun getCustomSources(): List<RomSource> {
        val json = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return RomSource.listFromJson(json)
    }

    /**
     * Hot flow that emits whenever any [SourceRepository] instance saves.
     * Immediately replays the latest emission to new collectors (replay=1).
     */
    fun sourcesFlow(): Flow<List<RomSource>> =
        _bus.map { getSources() }.distinctUntilChanged()

    // -----------------------------------------------------------------------
    // Write API
    // -----------------------------------------------------------------------

    fun addSource(source: RomSource) {
        val list = getCustomSources().toMutableList()
        list.add(source)
        save(list)
    }

    fun updateSource(source: RomSource) {
        val list = getCustomSources().toMutableList()
        val idx = list.indexOfFirst { it.id == source.id }
        if (idx >= 0) { list[idx] = source; save(list) }
    }

    fun removeSource(id: String) {
        val list = getCustomSources().toMutableList()
        list.removeAll { it.id == id }
        save(list)
    }

    /** Adds [source] only if no existing source has the same type + normalized path. */
    fun upsertByPath(source: RomSource) {
        val norm = source.path.trimEnd('/')
        val exists = getCustomSources().any { it.type == source.type && it.path.trimEnd('/') == norm }
        if (!exists) addSource(source)
    }

    // -----------------------------------------------------------------------
    // Convenience getters for StorageProviders
    // -----------------------------------------------------------------------

    /** content:// URIs for SAF (local) sources. */
    fun getLocalContentUris(): List<String> =
        getCustomSources()
            .filter { it.type == SourceType.LOCAL && it.path.startsWith("content://") }
            .map { it.path }

    /** file:// or absolute paths for file-based local sources. */
    fun getLocalFilePaths(): List<String> =
        getCustomSources()
            .filter { it.type == SourceType.LOCAL && (it.path.startsWith("file://") || it.path.startsWith("/")) }
            .map { it.path }

    /** Parsed configs for all SMB sources. */
    fun getSmbSourceConfigs(): List<SmbSourceConfig> =
        getCustomSources()
            .filter { it.type == SourceType.SMB }
            .mapNotNull { source ->
                parseSmbPath(source.path)?.let { p ->
                    SmbSourceConfig(source.id, p.server, p.share, p.subPath, source.credentials)
                }
            }

    /**
     * Returns the [RomSource] that owns [fileUri], using the same logic as [resolveSourceName].
     * Used to retrieve [RomSource.platformHint] during library indexing.
     */
    fun findSourceForUri(fileUri: String): RomSource? {
        val uri = try { Uri.parse(fileUri) } catch (_: Exception) { return null }
        return when (uri.scheme?.lowercase()) {
            "smb" -> {
                val host = uri.host ?: return null
                val gamePath = (uri.path ?: "").replace('\\', '/')
                getCustomSources()
                    .filter { it.type == SourceType.SMB }
                    .mapNotNull { src ->
                        val srcUri = try { Uri.parse(src.path) } catch (_: Exception) { return@mapNotNull null }
                        if (!srcUri.host.equals(host, ignoreCase = true)) return@mapNotNull null
                        val srcPath = (srcUri.path ?: "").replace('\\', '/')
                        if (gamePath.startsWith(srcPath)) src to srcPath.length else null
                    }
                    .maxByOrNull { (_, len) -> len }
                    ?.first
            }
            "content" -> {
                val gameDocId = runCatching { DocumentsContract.getDocumentId(uri) }
                    .getOrElse {
                        runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                    }
                    ?: return null

                getCustomSources()
                    .filter { it.type == SourceType.LOCAL }
                    .mapNotNull { src ->
                        val srcUri = try { Uri.parse(src.path) } catch (_: Exception) { return@mapNotNull null }
                        if (srcUri.scheme != "content") return@mapNotNull null
                        val srcDocId = runCatching { DocumentsContract.getTreeDocumentId(srcUri) }
                            .getOrElse { runCatching { DocumentsContract.getDocumentId(srcUri) }.getOrNull() }
                            ?: return@mapNotNull null
                        if (gameDocId.startsWith(srcDocId)) src to srcDocId.length else null
                    }
                    .maxByOrNull { (_, len) -> len }
                    ?.first
            }
            "file" -> {
                val filePath = uri.path ?: return null
                getCustomSources()
                    .filter { it.type == SourceType.LOCAL }
                    .mapNotNull { src ->
                        val srcPath = src.path.removePrefix("file://")
                        if (filePath.startsWith(srcPath)) src to srcPath.length else null
                    }
                    .maxByOrNull { (_, len) -> len }
                    ?.first
            }
            else -> null
        }
    }

    // -----------------------------------------------------------------------
    // One-time migration from legacy SharedPreferences
    // -----------------------------------------------------------------------

    fun migrateOldPrefsIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) return

        val existing = getCustomSources()
        val harmony = SharedPreferencesHelper.getSharedPreferences(context)

        // Legacy SAF URI (content://)
        SharedPreferencesHelper.getSAFUri(context)
            ?.takeIf { it.isNotBlank() }
            ?.let { uri ->
                if (existing.none { it.type == SourceType.LOCAL && it.path.trimEnd('/') == uri.trimEnd('/') }) {
                    Timber.i("Migration: SAF URI $uri")
                    addSource(RomSource.local("ROMs", uri))
                }
            }

        // Legacy TV file path
        harmony.getString(SharedPreferencesHelper.KEY_TV_CUSTOM_ROMS_PATH, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                if (existing.none { it.type == SourceType.LOCAL && it.path.trimEnd('/') == path.trimEnd('/') }) {
                    Timber.i("Migration: TV path $path")
                    addSource(RomSource.local("ROMs (TV)", path))
                }
            }

        // Legacy SMB config
        val server = harmony.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, null)
        val share  = harmony.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, null)
        if (!server.isNullOrBlank() && !share.isNullOrBlank()) {
            val sub      = harmony.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, "") ?: ""
            val username = harmony.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, null)
            val password = harmony.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, null)
            val smbPath  = "smb://$server/$share$sub"
            if (existing.none { it.type == SourceType.SMB && it.path == smbPath }) {
                Timber.i("Migration: SMB $smbPath")
                val creds = if (!username.isNullOrBlank()) SourceCredentials(username, password ?: "") else null
                addSource(RomSource.smb("SMB Library", server, "/$share$sub", creds))
            }
        }

        prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
    }

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    private fun save(sources: List<RomSource>) {
        prefs.edit().putString(KEY_SOURCES, RomSource.listToJson(sources)).apply()
        _bus.tryEmit(Unit)
    }

    private fun parseSmbPath(path: String): ParsedSmb? {
        if (!path.startsWith("smb://")) return null
        val body = path.removePrefix("smb://")
        val slash = body.indexOf('/')
        if (slash <= 0) return null
        val server = body.substring(0, slash)
        val rest   = body.substring(slash + 1).trimStart('/')
        val slash2 = rest.indexOf('/')
        val share  = if (slash2 >= 0) rest.substring(0, slash2) else rest
        val sub    = if (slash2 >= 0) "/" + rest.substring(slash2 + 1) else ""
        return ParsedSmb(server, share, sub)
    }

    private data class ParsedSmb(val server: String, val share: String, val subPath: String)

    companion object {
        const val PREFS_NAME = "rom_sources"
        const val KEY_SOURCES = "sources_list"
        private const val KEY_MIGRATION_DONE = "migration_v1_done"

        /** In-process bus: fires on every save, regardless of which instance wrote. */
        private val _bus = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
    }
}

/** Parsed SMB source ready for use by [SmbStorageProvider]. */
data class SmbSourceConfig(
    val sourceId: String,
    val server: String,
    val share: String,
    val path: String,
    val credentials: SourceCredentials?,
)
