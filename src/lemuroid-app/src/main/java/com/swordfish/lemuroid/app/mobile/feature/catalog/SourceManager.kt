package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Manages ROM sources: Archive.org (cloud), Local folders, and SMB/NAS shares
 */
class SourceManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "rom_sources"
        private const val KEY_SOURCES = "sources_list"
    }
    
    /**
     * Get all configured sources (always includes Archive.org)
     */
    fun getSources(): List<RomSource> {
        val customSources = getCustomSources()
        // Archive.org is always the first source
        return listOf(RomSource.archiveOrg()) + customSources
    }
    
    /**
     * Get only custom sources (Local + SMB)
     */
    fun getCustomSources(): List<RomSource> {
        val json = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<RomSource>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Add a new source
     */
    fun addSource(source: RomSource) {
        val sources = getCustomSources().toMutableList()
        sources.add(source)
        saveSources(sources)
    }
    
    /**
     * Update an existing source
     */
    fun updateSource(source: RomSource) {
        val sources = getCustomSources().toMutableList()
        val index = sources.indexOfFirst { it.id == source.id }
        if (index >= 0) {
            sources[index] = source
            saveSources(sources)
        }
    }
    
    /**
     * Remove a source by ID
     */
    fun removeSource(sourceId: String) {
        val sources = getCustomSources().toMutableList()
        sources.removeAll { it.id == sourceId }
        saveSources(sources)
    }
    
    private fun saveSources(sources: List<RomSource>) {
        val json = gson.toJson(sources)
        prefs.edit().putString(KEY_SOURCES, json).apply()
    }
}

/**
 * Represents a ROM source (Archive.org, Local folder, or SMB share)
 */
data class RomSource(
    val id: String,
    val type: SourceType,
    val name: String,
    val path: String,
    val credentials: SmbCredentials? = null
) {
    companion object {
        private fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID()}"

        fun archiveOrg() = RomSource(
            id = "archive_org",
            type = SourceType.ARCHIVE_ORG,
            name = "Archive.org",
            path = "https://archive.org"
        )
        
        fun local(name: String, uri: String) = RomSource(
            id = newId("local"),
            type = SourceType.LOCAL,
            name = name,
            path = uri
        )
        
        fun smb(name: String, server: String, path: String, credentials: SmbCredentials? = null): RomSource {
            // Normalizar path: asegurar que empiece con / pero sin duplicados
            val normalizedPath = "/" + path.trimStart('/')
            return RomSource(
                id = newId("smb"),
                type = SourceType.SMB,
                name = name,
                path = "smb://$server$normalizedPath",
                credentials = credentials
            )
        }
        
        fun webdav(name: String, url: String, credentials: SmbCredentials? = null): RomSource {
            val normalizedUrl = if (url.startsWith("http")) url else "http://$url"
            return RomSource(
                id = newId("webdav"),
                type = SourceType.WEBDAV,
                name = name,
                path = normalizedUrl,
                credentials = credentials
            )
        }
        
        fun sftp(name: String, server: String, path: String, credentials: SmbCredentials? = null): RomSource {
            val normalizedPath = "/" + path.trimStart('/')
            return RomSource(
                id = newId("sftp"),
                type = SourceType.SFTP,
                name = name,
                path = "sftp://$server$normalizedPath",
                credentials = credentials
            )
        }
    }
}

/**
 * SMB authentication credentials
 */
data class SmbCredentials(
    val username: String,
    val password: String
)

/**
 * Type of ROM source
 */
enum class SourceType {
    ARCHIVE_ORG,  // ☁️ Cloud
    LOCAL,        // 📁 Local folder
    SMB,          // 🖥️ SMB/NAS share
    WEBDAV,       // 🌐 WebDAV share
    SFTP          // 🔐 SFTP share
}

/**
 * State of a downloadable file
 */
enum class DownloadState {
    NOT_DOWNLOADED,     // ⬇️ Download button
    ALREADY_DOWNLOADED, // 🔄 Update/refresh button (overwrites)
    IN_LIBRARY          // ✅ Already in library
}
