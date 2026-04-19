package com.swordfish.lemuroid.metadata.libretrodb

import com.swordfish.lemuroid.common.kotlin.filterNullable
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDBManager
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDatabase
import com.swordfish.lemuroid.metadata.libretrodb.db.entity.LibretroRom
import timber.log.Timber
import java.util.Locale

class LibretroDBMetadataProvider(private val ovgdbManager: LibretroDBManager) :
    GameMetadataProvider {

    private val sortedSystemIds: List<String> by lazy {
        SystemID.values()
            .map { it.dbname }
            .sortedByDescending { it.length }
    }

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        val db = ovgdbManager.dbInstance

        Timber.d("Looking metadata for file: $storageFile")

        var metadata =
            runCatching {
                findByCRC(storageFile, db)
                    ?: findBySerial(storageFile, db)
                    ?: findByFilename(db, storageFile)
                    ?: findByPathAndFilename(db, storageFile)
                    ?: findByUniqueExtension(storageFile)
                    ?: findByKnownSystem(storageFile)
                    ?: findByPathAndSupportedExtension(storageFile)
                    ?: findByPathForCompressedFile(storageFile)  // Last resort: folder-only detection for archives
            }.getOrElse {
                Timber.e("Error in retrieving $storageFile metadata: $it... Skipping.")
                null
            }

        // V8.6: Post-processing - generate thumbnail by name if null
        if (metadata != null && metadata.thumbnail == null && metadata.system != null) {
            val system = GameSystem.findById(metadata.system!!)
            val thumbnailUrl = computeCoverUrl(system, metadata.name ?: storageFile.extensionlessName)
            metadata = metadata.copy(thumbnail = thumbnailUrl)
            Timber.d("V8.6: Generated thumbnail by name for ${storageFile.name}: $thumbnailUrl")
        }

        metadata?.let { Timber.d("Metadata retrieved for item: $it") }

        return metadata
    }
    
    /**
     * Last resort fallback: For compressed files (.zip, .7z, .rar), detect system purely by folder name.
     * This is less accurate but catches cases where extension detection fails for archives.
     */
    private fun findByPathForCompressedFile(file: StorageFile): GameMetadata? {
        val fileExtension = file.extension.lowercase()
        
        // Only apply to compressed files
        if (!StorageFile.ARCHIVE_EXTENSIONS.contains(fileExtension)) {
            return null
        }
        
        // Find system by folder name only (no extension check)
        val system = sortedSystemIds
            .filter { parentContainsSystem(file.path, it) }
            .map { GameSystem.findById(it) }
            .filter { it.scanOptions.scanByPathAndSupportedExtensions }
            .firstOrNull()
        
        return system?.let {
            Timber.d("Detected system by folder for compressed file: ${file.name} -> ${it.id.dbname}")
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = computeCoverUrl(it, file.extensionlessName), // Fallback: thumbnail by name
                system = it.id.dbname,
                developer = null,
            )
        }
    }

    private fun convertToGameMetadata(rom: LibretroRom): GameMetadata {
        val system = GameSystem.findById(rom.system!!)
        return GameMetadata(
            name = rom.name,
            romName = rom.romName,
            thumbnail = computeCoverUrl(system, rom.name),
            system = rom.system,
            developer = rom.developer,
        )
    }

    private suspend fun findByFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { extractGameSystem(it).scanOptions.scanByFilename }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findByPathAndFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { extractGameSystem(it).scanOptions.scanByPathAndFilename }
            .filterNullable { parentContainsSystem(file.path, extractGameSystem(it).id.dbname) }
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByPathAndSupportedExtension(file: StorageFile): GameMetadata? {
        // Compressed file extensions that can contain any ROM type
        val compressedExtensions = setOf("zip", "7z", "rar")
        
        val system =
            sortedSystemIds
                .filter { parentContainsSystem(file.path, it) }
                .map { GameSystem.findById(it) }
                .filter { it.scanOptions.scanByPathAndSupportedExtensions }
                .firstOrNull { 
                    it.supportedExtensions.contains(file.extension) || 
                    compressedExtensions.contains(file.extension?.lowercase())
                }

        return system?.let {
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = computeCoverUrl(it, file.extensionlessName), // Fallback: thumbnail by name
                system = it.id.dbname,
                developer = null,
            )
        }
    }

    private fun parentContainsSystem(
        parent: String?,
        dbname: String,
    ): Boolean {
        if (parent == null) return false
        val lowerParent = parent.toLowerCase(Locale.getDefault())
        
        // Check direct dbname match
        if (lowerParent.contains(dbname)) return true
        
        // Check folder aliases - common folder names that map to dbnames
        val aliases = FOLDER_ALIASES[dbname] ?: emptyList()
        return aliases.any { lowerParent.contains(it) }
    }
    
    companion object {
        private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")
        
        // Map dbname to common folder name aliases
        private val FOLDER_ALIASES = mapOf(
            "md" to listOf("genesis", "megadrive", "mega drive", "mega-drive"),
            "sms" to listOf("mastersystem", "master system", "master-system"),
            "gg" to listOf("gamegear", "game gear", "game-gear"),
            "pce" to listOf("pcengine", "pc engine", "pc-engine", "turbografx", "turbografx-16"),
            "scd" to listOf("segacd", "sega cd", "sega-cd", "megacd", "mega cd", "mega-cd"),
            "nes" to listOf("famicom", "nintendo"),
            "snes" to listOf("superfamicom", "super famicom", "super-famicom", "supernintendo", "super nintendo"),
            "gb" to listOf("gameboy", "game boy", "game-boy"),
            "gbc" to listOf("gameboycolor", "gameboy color", "game boy color"),
            "gba" to listOf("gameboyadvance", "gameboy advance", "game boy advance"),
            "n64" to listOf("nintendo64", "nintendo 64", "nintendo-64"),
            "nds" to listOf("nintendods", "nintendo ds", "ds"),
            "psx" to listOf("playstation", "ps1", "psone", "ps one"),
            "psp" to listOf("playstationportable", "playstation portable"),
            "fbneo" to listOf("neogeo", "neo geo", "neo-geo", "arcade", "fba", "fbalpha"),
            "mame2003plus" to listOf("mame", "mame2003"),
            "atari2600" to listOf("atari 2600", "atari-2600", "2600"),
            "atari7800" to listOf("atari 7800", "atari-7800", "7800"),
            "lynx" to listOf("atarilynx", "atari lynx"),
            "ngp" to listOf("neogeopocket", "neo geo pocket"),
            "ngc" to listOf("neogeopocketcolor", "neo geo pocket color"),
            "ws" to listOf("wonderswan"),
            "wsc" to listOf("wonderswancolor", "wonderswan color"),
            "3ds" to listOf("nintendo3ds", "nintendo 3ds")
        )
    }

    private suspend fun findByCRC(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.crc == null || file.crc == "0") return null
        return file.crc?.let { crc32 -> db.gameDao().findByCRC(crc32) }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findBySerial(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.serial == null) return null
        return db.gameDao().findBySerial(file.serial!!)
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByKnownSystem(file: StorageFile): GameMetadata? {
        if (file.systemID == null) return null
        
        val system = GameSystem.findById(file.systemID!!.dbname)

        return GameMetadata(
            name = file.extensionlessName,
            romName = file.name,
            thumbnail = computeCoverUrl(system, file.extensionlessName), // Fallback: thumbnail by name
            system = file.systemID!!.dbname,
            developer = null,
        )
    }

    private fun findByUniqueExtension(file: StorageFile): GameMetadata? {
        val system = GameSystem.findByUniqueFileExtension(file.extension)

        if (system?.scanOptions?.scanByUniqueExtension == false) {
            return null
        }

        val result =
            system?.let {
                GameMetadata(
                    name = file.extensionlessName,
                    romName = file.name,
                    thumbnail = computeCoverUrl(it, file.extensionlessName), // Fallback: thumbnail by name
                    system = it.id.dbname,
                    developer = null,
                )
            }

        return result
    }

    private fun extractGameSystem(rom: LibretroRom): GameSystem {
        return GameSystem.findById(rom.system!!)
    }

    private fun computeCoverUrl(
        system: GameSystem,
        name: String?,
    ): String? {
        var systemName = system.libretroFullName

        // Specific mame version don't have any thumbnails in Libretro database
        if (system.id == SystemID.MAME2003PLUS) {
            systemName = "MAME"
        }

        if (name == null) {
            return null
        }

        val imageType = "Named_Boxarts"

        val thumbGameName = name.replace(THUMB_REPLACE, "_")

        return "http://thumbnails.libretro.com/$systemName/$imageType/$thumbGameName.png"
    }
}
