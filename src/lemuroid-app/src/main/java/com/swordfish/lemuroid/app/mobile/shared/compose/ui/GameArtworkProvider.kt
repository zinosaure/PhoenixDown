package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.net.URLEncoder

/**
 * Provides artwork URLs from libretro-thumbnails
 * 
 * Structure:
 * - Named_Boxarts: Game covers (already used by coverFrontUrl)
 * - Named_Snaps: In-game screenshots
 * - Named_Titles: Title screens
 */
object GameArtworkProvider {
    
    private const val BASE_URL = "https://thumbnails.libretro.com"
    
    // Map systemId to libretro system folder name
    private val systemMapping = mapOf(
        "nes" to "Nintendo - Nintendo Entertainment System",
        "snes" to "Nintendo - Super Nintendo Entertainment System",
        "gb" to "Nintendo - Game Boy",
        "gbc" to "Nintendo - Game Boy Color",
        "gba" to "Nintendo - Game Boy Advance",
        "n64" to "Nintendo - Nintendo 64",
        "nds" to "Nintendo - Nintendo DS",
        "genesis" to "Sega - Mega Drive - Genesis",
        "sms" to "Sega - Master System - Mark III",
        "gg" to "Sega - Game Gear",
        "scd" to "Sega - Mega-CD - Sega CD",
        "psx" to "Sony - PlayStation",
        "psp" to "Sony - PlayStation Portable",
        "pce" to "NEC - PC Engine - TurboGrafx 16",
        "ngp" to "SNK - Neo Geo Pocket",
        "ngpc" to "SNK - Neo Geo Pocket Color",
        "ws" to "Bandai - WonderSwan",
        "wsc" to "Bandai - WonderSwan Color",
        "lynx" to "Atari - Lynx",
        "a2600" to "Atari - 2600",
        "a7800" to "Atari - 7800",
        "arcade" to "MAME",
        "fbneo" to "FBNeo - Arcade Games",
        "dos" to "DOS"
    )
    
    /**
     * Get screenshot URL (Named_Snaps) for background
     */
    fun getScreenshotUrl(game: Game): String? {
        val systemFolder = systemMapping[game.systemId] ?: return null
        val encodedName = encodeGameName(game.title)
        return "$BASE_URL/$systemFolder/Named_Snaps/$encodedName.png"
    }
    
    /**
     * Get title screen URL (Named_Titles) as fallback
     */
    fun getTitleScreenUrl(game: Game): String? {
        val systemFolder = systemMapping[game.systemId] ?: return null
        val encodedName = encodeGameName(game.title)
        return "$BASE_URL/$systemFolder/Named_Titles/$encodedName.png"
    }
    
    /**
     * Get boxart URL (Named_Boxarts) - same as coverFrontUrl but explicit
     */
    fun getBoxartUrl(game: Game): String? {
        // Use existing coverFrontUrl if available
        if (!game.coverFrontUrl.isNullOrEmpty()) {
            return game.coverFrontUrl
        }
        
        val systemFolder = systemMapping[game.systemId] ?: return null
        val encodedName = encodeGameName(game.title)
        return "$BASE_URL/$systemFolder/Named_Boxarts/$encodedName.png"
    }
    
    /**
     * Get list of URLs to try for background, in order of preference
     */
    fun getBackgroundUrls(game: Game): List<String> {
        return listOfNotNull(
            getScreenshotUrl(game),
            getTitleScreenUrl(game),
            getBoxartUrl(game)
        )
    }
    
    /**
     * Encode game name for URL (libretro naming convention)
     * 
     * Special characters are replaced:
     * - & -> _
     * - / -> _
     * - : -> (empty or _)
     */
    private fun encodeGameName(title: String): String {
        return URLEncoder.encode(
            title
                .replace("&", "_")
                .replace("/", "_")
                .replace(":", " -")
                .replace("?", "")
                .replace("*", "_")
                .replace("\"", "'")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_"),
            "UTF-8"
        ).replace("+", "%20")
    }
}
