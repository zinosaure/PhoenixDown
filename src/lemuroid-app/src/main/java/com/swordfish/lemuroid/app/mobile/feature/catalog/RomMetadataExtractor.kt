package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.util.Log

/**
 * Smart ROM detector that extracts metadata from file paths and names
 */
object RomMetadataExtractor {
    
    private const val TAG = "RomMetadataExtractor"
    
    // Known system folder names mapped to system IDs
    private val SYSTEM_FOLDER_NAMES = mapOf(
        // Nintendo
        "nes" to "nes", "famicom" to "nes", "nintendo" to "nes",
        "snes" to "snes", "superfamicom" to "snes", "super nintendo" to "snes", "supernintendo" to "snes",
        "n64" to "n64", "nintendo64" to "n64", "nintendo 64" to "n64",
        "gb" to "gb", "gameboy" to "gb", "game boy" to "gb",
        "gbc" to "gbc", "gameboycolor" to "gbc", "game boy color" to "gbc",
        "gba" to "gba", "gameboyadvance" to "gba", "game boy advance" to "gba",
        "nds" to "nds", "ds" to "nds", "nintendods" to "nds", "nintendo ds" to "nds",
        "virtualboy" to "vb", "virtual boy" to "vb",
        
        // Sega
        "genesis" to "genesis", "megadrive" to "genesis", "mega drive" to "genesis", "md" to "genesis",
        "sms" to "sms", "mastersystem" to "sms", "master system" to "sms", "segamastersystem" to "sms",
        "gamegear" to "gg", "game gear" to "gg", "gg" to "gg",
        "segacd" to "segacd", "sega cd" to "segacd", "megacd" to "segacd",
        "32x" to "32x", "sega32x" to "32x",
        "saturn" to "saturn", "segasaturn" to "saturn",
        "dreamcast" to "dc", "dc" to "dc",
        
        // Sony
        "psx" to "psx", "ps1" to "psx", "playstation" to "psx", "psone" to "psx", "ps one" to "psx",
        "psp" to "psp", "playstationportable" to "psp",
        
        // Atari
        "atari2600" to "atari2600", "2600" to "atari2600", "a2600" to "atari2600",
        "atari7800" to "atari7800", "7800" to "atari7800", "a7800" to "atari7800",
        "lynx" to "lynx", "atarilynx" to "lynx",
        "jaguar" to "jaguar", "atarijaguar" to "jaguar",
        
        // NEC
        "pce" to "pce", "pcengine" to "pce", "pc engine" to "pce", "turbografx" to "pce", "tg16" to "pce",
        "pcfx" to "pcfx",
        
        // SNK
        "neogeo" to "neogeo", "neo geo" to "neogeo", "ng" to "neogeo",
        "ngp" to "ngp", "neogeopocket" to "ngp", "neo geo pocket" to "ngp",
        "ngpc" to "ngpc", "neogeopocketcolor" to "ngpc",
        
        // Other
        "arcade" to "arcade", "mame" to "arcade", "fba" to "arcade", "fbneo" to "arcade",
        "wonderswan" to "ws", "ws" to "ws",
        "wonderswancolor" to "wsc", "wsc" to "wsc",
        "coleco" to "coleco", "colecovision" to "coleco"
    )
    
    // Region patterns in filenames
    private val REGION_PATTERNS = mapOf(
        Regex("\\(USA\\)", RegexOption.IGNORE_CASE) to "USA",
        Regex("\\(U\\)", RegexOption.IGNORE_CASE) to "USA",
        Regex("\\(US\\)", RegexOption.IGNORE_CASE) to "USA",
        Regex("\\(Europe\\)", RegexOption.IGNORE_CASE) to "EUR",
        Regex("\\(EUR\\)", RegexOption.IGNORE_CASE) to "EUR",
        Regex("\\(E\\)", RegexOption.IGNORE_CASE) to "EUR",
        Regex("\\(Japan\\)", RegexOption.IGNORE_CASE) to "JPN",
        Regex("\\(JPN\\)", RegexOption.IGNORE_CASE) to "JPN",
        Regex("\\(J\\)", RegexOption.IGNORE_CASE) to "JPN",
        Regex("\\(Spain\\)", RegexOption.IGNORE_CASE) to "ESP",
        Regex("\\(ESP\\)", RegexOption.IGNORE_CASE) to "ESP",
        Regex("\\(Es\\)", RegexOption.IGNORE_CASE) to "ESP",
        Regex("\\(France\\)", RegexOption.IGNORE_CASE) to "FRA",
        Regex("\\(FRA\\)", RegexOption.IGNORE_CASE) to "FRA",
        Regex("\\(Fr\\)", RegexOption.IGNORE_CASE) to "FRA",
        Regex("\\(Germany\\)", RegexOption.IGNORE_CASE) to "GER",
        Regex("\\(GER\\)", RegexOption.IGNORE_CASE) to "GER",
        Regex("\\(De\\)", RegexOption.IGNORE_CASE) to "GER",
        Regex("\\(Italy\\)", RegexOption.IGNORE_CASE) to "ITA",
        Regex("\\(ITA\\)", RegexOption.IGNORE_CASE) to "ITA",
        Regex("\\(It\\)", RegexOption.IGNORE_CASE) to "ITA",
        Regex("\\(World\\)", RegexOption.IGNORE_CASE) to "World",
        Regex("\\(W\\)", RegexOption.IGNORE_CASE) to "World",
        Regex("\\(Korea\\)", RegexOption.IGNORE_CASE) to "KOR",
        Regex("\\(KOR\\)", RegexOption.IGNORE_CASE) to "KOR",
        Regex("\\(Brazil\\)", RegexOption.IGNORE_CASE) to "BRA",
        Regex("\\(BRA\\)", RegexOption.IGNORE_CASE) to "BRA"
    )
    
    // Extension to system mapping
    private val EXTENSION_TO_SYSTEM = mapOf(
        // Nintendo
        "nes" to "nes", "fds" to "nes", "unf" to "nes",
        "sfc" to "snes", "smc" to "snes", "fig" to "snes", "swc" to "snes",
        "n64" to "n64", "z64" to "n64", "v64" to "n64",
        "gb" to "gb",
        "gbc" to "gbc",
        "gba" to "gba",
        "nds" to "nds", "dsi" to "nds",
        
        // Sega
        "md" to "genesis", "gen" to "genesis", "smd" to "genesis", "bin" to "genesis",
        "gg" to "gg",
        "sms" to "sms", "sg" to "sms",
        
        // Sony
        "pbp" to "psp", "iso" to "psx", "cue" to "psx", "chd" to "psx",
        
        // NEC
        "pce" to "pce", "sgx" to "pce",
        
        // Atari
        "a26" to "atari2600",
        "a78" to "atari7800",
        "lnx" to "lynx",
        
        // SNK
        "ngp" to "ngp", "ngc" to "ngpc",
        
        // Other
        "ws" to "ws", "wsc" to "wsc",
        "col" to "coleco",
        "vec" to "vectrex",
        "int" to "intellivision"
    )
    
    /**
     * Extract metadata from a file path
     * @param fullPath The complete path including directories (e.g., "SNES/RPG/Chrono Trigger (USA).zip")
     * @param fileName The file name only (e.g., "Chrono Trigger (USA).zip")
     * @param extension The file extension without dot (e.g., "zip")
     */
    fun extractMetadata(fullPath: String, fileName: String, extension: String): RomMetadata {
        // Detect system from parent folders
        val detectedSystem = detectSystemFromPath(fullPath)
        
        // Detect system from extension if not found in path
        val systemFromExt = if (detectedSystem == null) {
            EXTENSION_TO_SYSTEM[extension.lowercase()]
        } else null
        
        val finalSystem = detectedSystem ?: systemFromExt
        
        // Detect region from filename
        val region = detectRegion(fileName)
        
        // Extract clean game name
        val cleanName = extractCleanName(fileName)
        
        // Get region flag emoji
        val flag = getRegionFlag(region)
        
        Log.d(TAG, "Extracted: name='$cleanName', system='$finalSystem', region='$region' from '$fileName'")
        
        return RomMetadata(
            cleanName = cleanName,
            originalFileName = fileName,
            system = finalSystem,
            region = region,
            flag = flag,
            extension = extension
        )
    }
    
    /**
     * Detect system from folder names in the path
     */
    private fun detectSystemFromPath(path: String): String? {
        // Split path into parts and check each folder name
        val parts = path.replace("\\", "/").split("/")
        
        // Check from most specific (deepest) to least specific
        for (part in parts.reversed()) {
            val normalized = part.lowercase().replace(" ", "").replace("-", "").replace("_", "")
            SYSTEM_FOLDER_NAMES[normalized]?.let { return it }
            
            // Also check the original lowercase
            SYSTEM_FOLDER_NAMES[part.lowercase()]?.let { return it }
        }
        
        return null
    }
    
    /**
     * Detect region from filename patterns
     */
    private fun detectRegion(fileName: String): String? {
        for ((pattern, region) in REGION_PATTERNS) {
            if (pattern.containsMatchIn(fileName)) {
                return region
            }
        }
        return null
    }
    
    /**
     * Extract clean game name by removing tags and extension
     */
    private fun extractCleanName(fileName: String): String {
        var name = fileName
        
        // Remove extension
        val lastDot = name.lastIndexOf('.')
        if (lastDot > 0) {
            name = name.substring(0, lastDot)
        }
        
        // Remove common tags in parentheses and brackets
        name = name.replace(Regex("\\([^)]*\\)"), "") // (USA), (Rev 1), etc.
        name = name.replace(Regex("\\[[^]]*\\]"), "") // [!], [b], etc.
        
        // Clean up multiple spaces and trim
        name = name.replace(Regex("\\s+"), " ").trim()
        
        return name.ifEmpty { fileName }
    }
    
    /**
     * Get flag emoji for region
     */
    private fun getRegionFlag(region: String?): String {
        return when (region) {
            "USA" -> "🇺🇸"
            "EUR" -> "🇪🇺"
            "JPN" -> "🇯🇵"
            "ESP" -> "🇪🇸"
            "FRA" -> "🇫🇷"
            "GER" -> "🇩🇪"
            "ITA" -> "🇮🇹"
            "KOR" -> "🇰🇷"
            "BRA" -> "🇧🇷"
            "World" -> "🌍"
            else -> "🎮"
        }
    }
    
    /**
     * Get system display name
     */
    fun getSystemDisplayName(systemId: String?): String {
        return when (systemId) {
            "nes" -> "NES"
            "snes" -> "SNES"
            "n64" -> "N64"
            "gb" -> "Game Boy"
            "gbc" -> "GBC"
            "gba" -> "GBA"
            "nds" -> "NDS"
            "genesis" -> "Genesis/MD"
            "sms" -> "Master System"
            "gg" -> "Game Gear"
            "psx" -> "PlayStation"
            "psp" -> "PSP"
            "pce" -> "PC Engine"
            "arcade" -> "Arcade"
            "neogeo" -> "Neo Geo"
            "atari2600" -> "Atari 2600"
            "lynx" -> "Lynx"
            "ws" -> "WonderSwan"
            else -> systemId?.uppercase() ?: "Unknown"
        }
    }
}

/**
 * Extracted ROM metadata
 */
data class RomMetadata(
    val cleanName: String,
    val originalFileName: String,
    val system: String?,
    val region: String?,
    val flag: String,
    val extension: String
)
