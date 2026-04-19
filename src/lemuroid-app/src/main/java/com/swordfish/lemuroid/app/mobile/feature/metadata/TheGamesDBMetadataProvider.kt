package com.swordfish.lemuroid.app.mobile.feature.metadata

import com.google.gson.Gson
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import com.swordfish.lemuroid.BuildConfig

class TheGamesDBMetadataProvider(
    private val client: OkHttpClient,
    private val settingsManager: SettingsManager,
    private val gson: Gson = Gson()
) : GameMetadataProvider {

    companion object {
        private const val BASE_URL = "https://api.thegamesdb.net/v1/Games/ByGameName"
        private const val FIELDS = "overview,genres,publishers,developers,release_date"
    }

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? = withContext(Dispatchers.IO) {
        // Use key from local.properties (injected via BuildConfig), or fallback if empty
        val userKey = settingsManager.theGamesDbApiKey()
        val apiKey = if (userKey.isNotBlank()) userKey else BuildConfig.THEGAMESDB_API_KEY

        val searchName = cleanRomName(storageFile.extensionlessName)
        Timber.d("Searching TheGamesDB for: $searchName")
        
        return@withContext searchByName(searchName, apiKey)
    }

    private fun searchByName(name: String, apiKey: String): GameMetadata? {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        val url = "$BASE_URL?apikey=$apiKey&name=$encodedName&fields=$FIELDS"
        
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Retromul/1.0")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("TheGamesDB request failed: ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            val apiResponse = gson.fromJson(body, TGDBResponse::class.java)
            
            val game = apiResponse.data?.games?.firstOrNull() ?: return null
            
            // Map TGDB data to GameMetadata
            // Note: TGDB returns images in a separate "include" structure usually, but the basic endpoint might return basic image URLs is allowed?
            // Wait, v1 API usually separates images. For now let's focus on texted metadata.
            // Actually, we need to check if we can get cover.
            // The curl example implies basic data. Let's try to map what we have.
            // If images are missing, we might need a second call or 'include=boxart' parameter.
            // User instruction said "ByGameName?name=..." returns "IDs, names, platforms...".
            // Let's implement text fields first.
            
            return GameMetadata(
                name = game.game_title,
                romName = null,
                system = null,
                developer = game.developers?.firstOrNull(),
                publisher = game.publishers?.firstOrNull(),
                year = game.release_date?.take(4)?.toIntOrNull(),
                genre = game.genres?.firstOrNull(),
                description = game.overview,
                thumbnail = null // Images require complexity with TGDB (CDN base url + path). Skipping for now.
            )

        } catch (e: Exception) {
            Timber.e(e, "Error searching TheGamesDB")
            return null
        }
    }

    private fun cleanRomName(name: String): String {
        return name
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]*\\]"), "")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
    }

    // JSON Data Structures
    data class TGDBResponse(val data: TGDBData?)
    data class TGDBData(val games: List<TGDBGame>?)
    data class TGDBGame(
        val game_title: String?,
        val release_date: String?,
        val overview: String?,
        val developers: List<String>?,
        val publishers: List<String>?,
        val genres: List<String>?
    )
}
