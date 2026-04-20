package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * ArchiveOrgClient - Cliente para la API de Internet Archive
 */
class ArchiveOrgClient {

    companion object {
        private const val TAG = "Catalog.ArchiveOrg"
        private const val SEARCH_URL = "https://archive.org/advancedsearch.php"
        
        val SYSTEM_TERMS = mapOf(
            "nes" to "nes nintendo famicom",
            "snes" to "snes super nintendo sfc",
            "gba" to "gba gameboy advance",
            "gbc" to "gameboy gb gbc",
            "genesis" to "genesis megadrive sega",
            "n64" to "n64 nintendo64",
            "psx" to "psx playstation ps1",
            "psp" to "psp playstation portable",
            "arcade" to "arcade mame"
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun searchPacks(
        systemId: String,
        query: String = "",
        page: Int = 1,
        pageSize: Int = 50
    ): SearchResult = withContext(Dispatchers.IO) {
        
        val systemTerms = SYSTEM_TERMS[systemId] ?: return@withContext SearchResult.empty()
        
        val searchParts = mutableListOf<String>()
        val systemWords = systemTerms.split(" ")
        val systemQuery = systemWords.joinToString(" OR ") { word -> "title:$word" }
        searchParts.add("($systemQuery)")
        searchParts.add("(title:rom OR subject:rom)")
        searchParts.add("format:zip")
        
        if (query.isNotBlank()) {
            searchParts.add("title:*${query}*")
        }
        
        val fullQuery = searchParts.joinToString(" AND ")
        
        val url = buildString {
            append(SEARCH_URL)
            append("?q=").append(java.net.URLEncoder.encode(fullQuery, "UTF-8"))
            append("&fl[]=identifier,title,description,downloads,item_size")
            append("&sort[]=downloads+desc")
            append("&rows=$pageSize")
            append("&page=$page")
            append("&output=json")
        }

        Log.d(TAG, "Searching: $url")

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PhoenixDown/1.0")
                .build()

            Log.d(TAG, "Making HTTP request...")
            val httpResponse = httpClient.newCall(request).execute()
            Log.d(TAG, "HTTP response received: ${httpResponse.code}")
            
            if (!httpResponse.isSuccessful) {
                Log.e(TAG, "HTTP Error: ${httpResponse.code}")
                throw java.io.IOException("HTTP Error: ${httpResponse.code} - ${httpResponse.message}")
            }

            Log.d(TAG, "Reading response body...")
            val body = httpResponse.body?.string() ?: throw java.io.IOException("Empty response body from Archive.org")
            Log.d(TAG, "Body received, length: ${body.length}")
            
            val jsonResponse = try {
                gson.fromJson(body, JsonSearchResponse::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Parse error: ${e.message}")
                throw java.io.IOException("Failed to parse Archive.org response: ${e.message}", e)
            }
            
            val apiResults = jsonResponse.response
            Log.i(TAG, "Found ${apiResults.numFound} results")
            
            val packs = mutableListOf<RomPack>()
            for (doc in apiResults.docs) {
                val title = doc.title ?: continue
                packs.add(RomPack(
                    id = doc.identifier,
                    name = cleanTitle(title),
                    fullTitle = title,
                    systemId = systemId,
                    region = extractRegion(title),
                    downloads = doc.downloads ?: 0,
                    sizeBytes = doc.item_size ?: 0,
                    description = doc.description,
                    archiveIdentifier = doc.identifier
                ))
            }

            SearchResult(
                packs = packs,
                totalResults = apiResults.numFound,
                page = page,
                pageSize = pageSize,
                hasMore = (page * pageSize) < apiResults.numFound
            )

        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}")
            throw e // Rethrow to let ViewModel handle it
        }
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]*\\]"), "")
            .replace(Regex("(?i)\\b(rom|zip|snes|nes|gba|sfc|smc)\\b"), "")
            .trim()
            .take(50)
    }

    private fun extractRegion(title: String): String {
        val t = title.lowercase()
        return when {
            "usa" in t || "(u)" in t -> "USA"
            "europe" in t || "eur" in t || "(e)" in t -> "EUR"
            "japan" in t || "jpn" in t || "(j)" in t -> "JPN"
            "spain" in t || "spanish" in t || "esp" in t -> "ESP"
            "france" in t || "french" in t || "fra" in t -> "FRA"
            "germany" in t || "german" in t || "ger" in t -> "GER"
            "italy" in t || "italian" in t || "ita" in t -> "ITA"
            "brazil" in t || "portuguese" in t || "bra" in t -> "BRA"
            "korea" in t || "korean" in t || "kor" in t -> "KOR"
            "china" in t || "chinese" in t || "chn" in t -> "CHN"
            "australia" in t || "aus" in t -> "AUS"
            else -> ""
        }
    }

    /**
     * Obtiene los archivos descargables de un item de Archive.org
     */
    suspend fun getItemFiles(identifier: String): List<DownloadableFile> = withContext(Dispatchers.IO) {
        val url = "https://archive.org/metadata/$identifier"
        Log.d(TAG, "Fetching metadata: $url")

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PhoenixDown/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Metadata HTTP Error: ${response.code}")
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            val metadata = gson.fromJson(body, JsonMetadataResponse::class.java)

            // Filtrar solo archivos ROM (zip, 7z, y extensiones de ROM comunes)
            val romExtensions = listOf("zip", "7z", "nes", "sfc", "smc", "gba", "gbc", "gb", "n64", "z64", "v64", "iso", "bin", "cue", "pbp", "cso", "chd")
            
            metadata.files
                .filter { file ->
                    val ext = file.name.substringAfterLast('.', "").lowercase()
                    ext in romExtensions && file.size != null
                }
                .map { file ->
                    val size = file.size?.toLongOrNull() ?: 0
                    DownloadableFile(
                        name = file.name,
                        size = size,
                        url = "https://archive.org/download/$identifier/${java.net.URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")}",
                        format = file.format ?: file.name.substringAfterLast('.', "")
                    )
                }
                .sortedBy { it.name }

        } catch (e: Exception) {
            Log.e(TAG, "Metadata error: ${e.message}")
            emptyList()
        }
    }

    private fun isRomFile(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in listOf("zip", "7z", "nes", "sfc", "smc", "gba", "gbc", "gb", "n64", "z64", "v64", "iso", "bin", "cue", "pbp", "cso", "chd", "pce", "md", "gen", "sms", "gg")
    }



    // JSON response models
    @Keep
    data class JsonSearchResponse(val response: JsonSearchResults)
    @Keep
    data class JsonSearchResults(val numFound: Int, val docs: List<JsonDoc>)
    @Keep
    data class JsonDoc(val identifier: String, val title: String?, val description: String?, val downloads: Int?, val item_size: Long?)
    
    // Metadata response
    @Keep
    data class JsonMetadataResponse(val files: List<JsonFile> = emptyList())
    @Keep
    data class JsonFile(val name: String, val size: String?, val format: String?)
    
    // Archivo descargable
    data class DownloadableFile(
        val name: String,
        val size: Long,
        val url: String,
        val format: String
    ) {
        val sizeFormatted: String get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    // App models - RomPack representa un paquete/colección de ROMs de Archive.org
    data class RomPack(
        val id: String,
        val name: String,
        val fullTitle: String,
        val systemId: String,
        val region: String,
        val downloads: Int,
        val sizeBytes: Long,
        val description: String?,
        val archiveIdentifier: String
    ) {
        val flag: String get() = when (region) {
            "USA" -> "🇺🇸"
            "EUR" -> "🇪🇺"
            "JPN" -> "🇯🇵"
            "ESP" -> "🇪🇸"
            else -> "🏳️"
        }
        
        val sizeFormatted: String get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> "${sizeBytes / (1024 * 1024)} MB"
            else -> "${sizeBytes / (1024 * 1024 * 1024)} GB"
        }
    }

    data class SearchResult(
        val packs: List<RomPack>,
        val totalResults: Int,
        val page: Int,
        val pageSize: Int,
        val hasMore: Boolean
    ) {
        companion object {
            fun empty() = SearchResult(emptyList(), 0, 1, 50, false)
        }
    }
}
