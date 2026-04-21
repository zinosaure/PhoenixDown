package com.swordfish.lemuroid.lib.storage.source

import android.content.Context
import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Single source of truth for ROM source configuration.
 *
 * Persists sources as a JSON array in the "rom_sources" SharedPreferences file.
 * Both the UI layer (via SourceManager which extends this) and the storage providers
 * (via DI injection) read from the same store.
 *
 * Includes one-time migration from the legacy SharedPreferences keys.
 */
open class SourceRepository(protected val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -----------------------------------------------------------------------
    // Public read API
    // -----------------------------------------------------------------------

    /** Returns all sources including the built-in Archive.org source. */
    fun getSources(): List<RomSource> = listOf(RomSource.archiveOrg()) + getCustomSources()

    /** Returns only user-configured sources (LOCAL, SMB, etc.). */
    fun getCustomSources(): List<RomSource> {
        val json = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return RomSource.listFromJson(json)
    }

    /**
     * Cold flow that emits the full source list (including Archive.org) whenever the underlying
     * SharedPreferences change, and immediately on collection.
     * Use this in ViewModels so the UI reacts to writes from ANY code path (migration, DI,
     * StorageFrameworkPickerLauncher, etc.).
     */
    fun sourcesFlow(): Flow<List<RomSource>> =
        _sourcesChanged.map { getSources() }.distinctUntilChanged()

    // -----------------------------------------------------------------------
    // Public write API
    // -----------------------------------------------------------------------

    fun addSource(source: RomSource) {
        val sources = getCustomSources().toMutableList()
        sources.add(source)
        saveSources(sources)
    }

    fun updateSource(source: RomSource) {
        val sources = getCustomSources().toMutableList()
        val index = sources.indexOfFirst { it.id == source.id }
        if (index >= 0) {
            sources[index] = source
            saveSources(sources)
        }
    }

    fun removeSource(sourceId: String) {
        val sources = getCustomSources().toMutableList()
        sources.removeAll { it.id == sourceId }
        saveSources(sources)
    }

    /**
     * Adds [source] only if no existing source has the same type and normalized path.
     */
    fun upsertByPath(source: RomSource) {
        val normalizedPath = source.path.trimEnd('/')
        val exists = getCustomSources().any {
            it.type == source.type && it.path.trimEnd('/') == normalizedPath
        }
        if (!exists) addSource(source)
    }

    // -----------------------------------------------------------------------
    // Provider helpers — convenience methods for StorageProviders
    // -----------------------------------------------------------------------

    /** Returns content:// URIs for all LOCAL SAF sources. */
    fun getLocalContentUris(): List<String> =
        getCustomSources()
            .filter { it.type == SourceType.LOCAL && it.path.startsWith("content://") }
            .map { it.path }
            .distinct()

    /** Returns file system paths for all LOCAL (file:// or absolute) sources. */
    fun getLocalFilePaths(): List<String> =
        getCustomSources()
            .filter {
                it.type == SourceType.LOCAL &&
                    (it.path.startsWith("file://") || it.path.startsWith("/"))
            }
            .map { it.path }
            .distinct()

    /** Returns parsed configs for all SMB sources. */
    fun getSmbSourceConfigs(): List<SmbSourceConfig> =
        getCustomSources()
            .filter { it.type == SourceType.SMB }
            .mapNotNull { source ->
                parseSmbPath(source.path)?.let { parsed ->
                    SmbSourceConfig(
                        sourceId = source.id,
                        server = parsed.server,
                        share = parsed.share,
                        path = parsed.path,
                        credentials = source.credentials,
                    )
                }
            }

    /** Returns true if any SMB source is configured. */
    fun hasAnySmbSource(): Boolean =
        getCustomSources().any { it.type == SourceType.SMB }

    // -----------------------------------------------------------------------
    // One-time migration from legacy SharedPreferences
    // -----------------------------------------------------------------------

    /**
     * Migrates old storage configuration from SharedPreferences keys into this repository.
     * Safe to call multiple times — runs only once (guarded by a flag).
     */
    fun migrateOldPrefsIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) return

        val existing = getCustomSources()
        val harmonyPrefs = SharedPreferencesHelper.getSharedPreferences(context)

        // Legacy SAF URI
        harmonyPrefs.getString(SharedPreferencesHelper.KEY_STORAGE_FOLDER_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { uri ->
                if (existing.none { it.type == SourceType.LOCAL && it.path.trimEnd('/') == uri.trimEnd('/') }) {
                    Timber.i("SourceRepository: migrating SAF URI $uri")
                    addSource(RomSource.local("Primary ROMs", uri))
                }
            }

        // TV custom path
        harmonyPrefs.getString(SharedPreferencesHelper.KEY_TV_CUSTOM_ROMS_PATH, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                if (existing.none { it.type == SourceType.LOCAL && it.path.trimEnd('/') == path.trimEnd('/') }) {
                    Timber.i("SourceRepository: migrating TV path $path")
                    addSource(RomSource.local("TV ROMs", path))
                }
            }

        // Legacy external folder (standard prefs)
        @Suppress("DEPRECATION")
        val legacyPrefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val legacyKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)
        legacyPrefs.getString(legacyKey, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                if (existing.none { it.type == SourceType.LOCAL && it.path.trimEnd('/') == path.trimEnd('/') }) {
                    Timber.i("SourceRepository: migrating legacy path $path")
                    addSource(RomSource.local("ROMs", path))
                }
            }

        // Legacy SMB config
        val server = harmonyPrefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, null)
        val share = harmonyPrefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, null)
        if (!server.isNullOrBlank() && !share.isNullOrBlank()) {
            val subPath = harmonyPrefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, "") ?: ""
            val username = harmonyPrefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, null)
            val password = harmonyPrefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, null)
            val smbUri = "smb://$server/$share$subPath"
            if (existing.none { it.type == SourceType.SMB && it.path == smbUri }) {
                Timber.i("SourceRepository: migrating SMB $smbUri")
                val credentials = if (!username.isNullOrBlank()) SourceCredentials(username, password ?: "") else null
                addSource(RomSource.smb("SMB Library", server, "/$share$subPath", credentials))
            }
        }

        prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun saveSources(sources: List<RomSource>) {
        prefs.edit().putString(KEY_SOURCES, RomSource.listToJson(sources)).apply()
        _sourcesChanged.tryEmit(Unit)
    }

    private fun parseSmbPath(path: String): ParsedSmbPath? {
        if (!path.startsWith("smb://")) return null
        val withoutScheme = path.removePrefix("smb://")
        val slashIndex = withoutScheme.indexOf('/')
        if (slashIndex <= 0) return null
        val server = withoutScheme.substring(0, slashIndex)
        val remainder = withoutScheme.substring(slashIndex).trimStart('/')
        if (remainder.isBlank()) return null
        val firstSlash = remainder.indexOf('/')
        val share = if (firstSlash >= 0) remainder.substring(0, firstSlash) else remainder
        val subPath = if (firstSlash >= 0) "/" + remainder.substring(firstSlash + 1) else ""
        return ParsedSmbPath(server, share, subPath)
    }

    private data class ParsedSmbPath(val server: String, val share: String, val path: String)

    companion object {
        const val PREFS_NAME = "rom_sources"
        const val KEY_SOURCES = "sources_list"
        private const val KEY_MIGRATION_DONE = "prefs_migration_v1_done"

        // In-process broadcast: fires whenever ANY SourceRepository instance saves.
        // replay=1 ensures new collectors immediately get the latest emission.
        internal val _sourcesChanged = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
    }
}

/**
 * Parsed configuration for a single SMB source, ready for use by [SmbStorageProvider].
 */
data class SmbSourceConfig(
    val sourceId: String,
    val server: String,
    val share: String,
    val path: String,
    val credentials: SourceCredentials?,
)
