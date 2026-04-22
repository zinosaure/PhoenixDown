package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SourceType

/**
 * Manages ROM sources: Archive.org (cloud), local folders, and network shares
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
     * Get only custom sources (local + network)
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
 * State of a downloadable file
 */
enum class DownloadState {
    NOT_DOWNLOADED,     // ⬇️ Download button
    ALREADY_DOWNLOADED, // 🔄 Update/refresh button (overwrites)
    IN_LIBRARY          // ✅ Already in library
}
