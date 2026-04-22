/*
 * GameLibrary.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library

import android.net.Uri
import com.swordfish.lemuroid.common.coroutines.batchWithSizeAndTime
import com.swordfish.lemuroid.lib.storage.source.SourceRepository
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.DataFile
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.ForcedSystemMetadataProvider
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.GroupedStorageFiles
import com.swordfish.lemuroid.lib.storage.RomFiles
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.lib.storage.StorageProvider
import com.swordfish.lemuroid.lib.storage.StorageProviderRegistry
import dagger.Lazy
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LemuroidLibrary(
    private val retrogradedb: RetrogradeDatabase,
    private val storageProviderRegistry: Lazy<StorageProviderRegistry>,
    private val gameMetadataProvider: Lazy<GameMetadataProvider>,
    private val biosManager: BiosManager,
    private val sourceRepository: SourceRepository,
) {
    suspend fun indexLibrary() {
        val startedAtMs = System.currentTimeMillis()

        try {
            indexProviders(startedAtMs)
        } catch (e: Throwable) {
            Timber.e("Library indexing stopped due to exception", e)
        } finally {
            cleanUp(startedAtMs)
        }

        val executionTime = System.currentTimeMillis() - startedAtMs
        Timber.i("Library indexing completed in: $executionTime ms")
    }

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun indexProviders(startedAtMs: Long) {
        val gameMetadata = gameMetadataProvider.get()
        val enabledProviders = storageProviderRegistry.get().enabledProviders
        enabledProviders.asFlow()
            .flatMapConcat { indexSingleProvider(it, startedAtMs, gameMetadata) }
            .collect()
    }

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun indexSingleProvider(
        provider: StorageProvider,
        startedAtMs: Long,
        gameMetadata: GameMetadataProvider,
    ): Flow<Unit> {
        val stats = ProviderScanStats()
        return provider.listBaseStorageFiles()
            .flatMapConcat { StorageFilesMerger.mergeDataFiles(provider, it).asFlow() }
            .batchWithSizeAndTime(MAX_BUFFER_SIZE, MAX_TIME)
            .flatMapMerge { processBatch(it, provider, startedAtMs, gameMetadata, stats) }
            .onCompletion {
                Timber.i(
                    "Scan summary for ${provider::class.java.simpleName}: added=${stats.addedGames.get()}, " +
                        "platformMatched=${stats.matchedGames.get()} across ${stats.matchedSystems.size} systems, " +
                        "unknown=${stats.unknownGames.get()}, ignoredUnsupported=${stats.ignoredFiles.get()}"
                )
            }
    }

    private suspend fun processBatch(
        batch: List<GroupedStorageFiles>,
        provider: StorageProvider,
        startedAtMs: Long,
        gameMetadata: GameMetadataProvider,
        stats: ProviderScanStats,
    ) = flow<Unit> {
        val entries =
            batch
                .map { classifyGroupedStorageFile(it) }
                .onEach { classified ->
                    when (classified.classification) {
                        FileClassification.IGNORED -> stats.ignoredFiles.incrementAndGet()
                        FileClassification.PRE_MATCHED -> {
                            stats.matchedGames.incrementAndGet()
                            classified.resolvedSystemId?.takeUnless { it == SystemID.UNKNOWN.dbname }?.let {
                                stats.matchedSystems.add(it)
                            }
                        }
                        FileClassification.FORCED_UNKNOWN -> stats.unknownGames.incrementAndGet()
                        FileClassification.PASSTHROUGH -> Unit
                    }
                }
                .filter { it.classification != FileClassification.IGNORED }
                .map { fetchEntriesFromDatabase(it.file, it.resolvedSystemId) }

        val existingEntries = entries.filterIsInstance<ScanEntry.GameFile>()
        handleExistingEntries(existingEntries, startedAtMs)

        val newEntries =
            entries.filterIsInstance<ScanEntry.File>()
                .map { buildEntryFromMetadata(it.file, provider, gameMetadata, startedAtMs, it.resolvedSystemId) }

        handleNewEntries(newEntries, startedAtMs, provider, stats)
    }

    private fun fetchEntriesFromDatabase(
        storageFile: GroupedStorageFiles,
        resolvedSystemId: String?,
    ): ScanEntry {
        Timber.d("Retrieving scan entry for uri: ${storageFile.primaryFile}")
        val game = retrogradedb.gameDao().selectByFileUri(storageFile.primaryFile.uri.toString())
        return buildScanEntry(storageFile, game, resolvedSystemId)
    }

    private fun buildScanEntry(
        storageFile: GroupedStorageFiles,
        game: Game?,
        resolvedSystemId: String?,
    ): ScanEntry {
        return if (game != null) {
            ScanEntry.GameFile(storageFile, game, resolvedSystemId)
        } else {
            ScanEntry.File(storageFile, resolvedSystemId)
        }
    }

    private fun handleExistingEntries(
        entries: List<ScanEntry.GameFile>,
        startedAtMs: Long,
    ) {
        updateGames(entries, startedAtMs)
        updateDataFiles(entries, startedAtMs)
    }

    private fun updateGames(
        entries: List<ScanEntry.GameFile>,
        startedAtMs: Long,
    ) {
        val updatedGames =
            entries
                .map { entry ->
                    val game = entry.game
                    val forcedSystemId = entry.resolvedSystemId ?: sourceRepository
                        .findSourceForUri(entry.file.primaryFile.uri.toString())
                        ?.platformHint

                    val effectiveSystemId = forcedSystemId ?: game.systemId
                    val needsCoverRefresh = game.coverFrontUrl == null || forcedSystemId != null
                    val updatedCoverUrl = if (needsCoverRefresh) {
                        generateCoverUrl(effectiveSystemId, game.title)
                    } else {
                        game.coverFrontUrl
                    }
                    game.copy(
                        systemId = effectiveSystemId,
                        lastIndexedAt = startedAtMs,
                        coverFrontUrl = updatedCoverUrl,
                    )
                }

        updatedGames
            .forEach { Timber.d("Updating game: $it") }

        retrogradedb.gameDao().update(updatedGames)
    }
    
    /**
     * V8.7: Generate cover URL based on system and game title.
     * Matches the format used by LibretroDB thumbnails.
     */
    private fun generateCoverUrl(systemId: String, title: String): String? {
        return try {
            val system = GameSystem.findById(systemId)
            var systemName = system.libretroFullName
            
            if (system.id == SystemID.MAME2003PLUS) {
                systemName = "MAME"
            }
            
            val thumbGameName = title.replace(Regex("[&*/:`<>?\\\\|]"), "_")
            "http://thumbnails.libretro.com/$systemName/Named_Boxarts/$thumbGameName.png"
        } catch (e: Exception) {
            Timber.w("V8.7: Could not generate cover URL for $title: ${e.message}")
            null
        }
    }

    private fun updateDataFiles(
        entries: List<ScanEntry.GameFile>,
        startedAtMs: Long,
    ) {
        val dataFiles =
            entries.flatMap { (storageFile, game) ->
                storageFile.dataFiles.map { convertIntoDataFile(game.id, it, startedAtMs) }
            }

        dataFiles
            .forEach { Timber.d("Updating data file: $it") }

        retrogradedb.dataFileDao().insert(dataFiles)
    }

    private fun convertIntoDataFile(
        gameId: Int,
        baseStorageFile: BaseStorageFile,
        startedAtMs: Long,
    ): DataFile {
        return DataFile(
            gameId = gameId,
            fileUri = baseStorageFile.uri.toString(),
            fileName = baseStorageFile.name,
            lastIndexedAt = startedAtMs,
            path = baseStorageFile.path,
        )
    }

    private fun handleNewEntries(
        entries: List<ScanEntry>,
        startedAtMs: Long,
        provider: StorageProvider,
        stats: ProviderScanStats,
    ) {
        val gameFiles =
            entries
                .filterIsInstance<ScanEntry.GameFile>()

        val unknownFiles =
            entries
                .filterIsInstance<ScanEntry.File>()
                .flatMap { it.file.allFiles() }

        handleNewGames(gameFiles, startedAtMs, stats)
        handleUnknownFiles(provider, unknownFiles, startedAtMs)
    }

    private fun handleNewGames(
        pairs: List<ScanEntry.GameFile>,
        startedAtMs: Long,
        stats: ProviderScanStats,
    ) {
        val games =
            pairs
                .map { it.game }

        games.forEach { Timber.d("Insert: $it") }

        stats.addedGames.addAndGet(games.size)

        val insertedUnknown = games.count { it.systemId == SystemID.UNKNOWN.dbname }
        val insertedMatched = games.size - insertedUnknown
        if (insertedUnknown > 0) {
            stats.unknownGames.addAndGet(insertedUnknown)
        }
        if (insertedMatched > 0) {
            stats.matchedGames.addAndGet(insertedMatched)
            games.asSequence()
                .map { it.systemId }
                .filter { it != SystemID.UNKNOWN.dbname }
                .forEach { stats.matchedSystems.add(it) }
        }

        val gameIds = retrogradedb.gameDao().insert(games)
        val dataFiles =
            pairs
                .map { it.file.dataFiles }
                .zip(gameIds)
                .flatMap { (files, gameId) ->
                    files.map {
                        convertIntoDataFile(gameId.toInt(), it, startedAtMs)
                    }
                }

        retrogradedb.dataFileDao().insert(dataFiles)
    }

    private fun handleUnknownFiles(
        provider: StorageProvider,
        files: List<BaseStorageFile>,
        startedAtMs: Long,
    ) {
        files.forEach { baseStorageFile ->
            val storageFile = safeStorageFile(provider, baseStorageFile)
            val inputStream = storageFile?.uri?.let { provider.getInputStream(it) }

            if (storageFile != null && inputStream != null) {
                biosManager.tryAddBiosAfter(storageFile, inputStream, startedAtMs)
            }
        }
    }

    private suspend fun buildEntryFromMetadata(
        groupedStorageFile: GroupedStorageFiles,
        provider: StorageProvider,
        metadataProvider: GameMetadataProvider,
        startedAtMs: Long,
        resolvedSystemId: String? = null,
    ): ScanEntry {
        val forcedSystemId = resolvedSystemId ?: sourceRepository
            .findSourceForUri(groupedStorageFile.primaryFile.uri.toString())
            ?.platformHint

        val effectiveMetadataProvider =
            if (!forcedSystemId.isNullOrBlank() && forcedSystemId != SystemID.UNKNOWN.dbname) {
                ForcedSystemMetadataProvider(metadataProvider, forcedSystemId)
            } else {
                metadataProvider
            }

        var game =
            sortedFilesForScanning(groupedStorageFile).asFlow()
                .mapNotNull { safeStorageFile(provider, it) }
                .mapNotNull { storageFile ->
                    try {
                        val metadata = effectiveMetadataProvider.retrieveMetadata(storageFile)
                        convertGameMetadataToGame(groupedStorageFile, storageFile, metadata, startedAtMs, forcedSystemId)
                    } catch (e: Exception) {
                        Timber.e(e, "Error indexing file: ${storageFile.name}")
                        null
                    }
                }
                .firstOrNull()

        // Guaranteed fallback: if standard metadata lookup failed but we have a platformHint,
        // create the game entry directly from the primary file name + forced system.
        // This covers edge cases in ZIP processing (streaming ZIPs, SMB ZIPs, corrupted entries).
        if (game == null && !forcedSystemId.isNullOrBlank()) {
            game = createForcedGameEntry(groupedStorageFile, forcedSystemId, startedAtMs)
            if (game != null) Timber.d("ForcedFallback: indexed '${game.title}' as $forcedSystemId")
        }

        // Deduplication: compare by fileName (same ROM on two sources).
        // Priority: LOCAL (content:// or file://) > SMB (smb://) > other.
        // If existing entry has lower priority than this one → promote it to the new URI.
        // Otherwise → skip, the higher-priority (or same) source already owns this game.
        if (game != null) {
            val existing = retrogradedb.gameDao().selectByFileNameAndSystem(game.fileName, game.systemId)
            if (existing != null) {
                val existingPriority = sourceUriPriority(existing.fileUri)
                val newPriority = sourceUriPriority(game.fileUri)
                if (newPriority > existingPriority) {
                    // Promote: LOCAL file found for a game previously known only via SMB.
                    Timber.d("Dedup: promoting '${game.title}' from ${existing.fileUri} → ${game.fileUri}")
                    retrogradedb.gameDao().updateFileUri(existing.id, game.fileUri, startedAtMs)
                } else {
                    Timber.d("Dedup: skipping '${game.title}' (${game.systemId}) — already indexed from ${existing.fileUri}")
                }
                return ScanEntry.File(groupedStorageFile, forcedSystemId)
            }
        }

        return buildScanEntry(groupedStorageFile, game, forcedSystemId)
    }

    /** Higher value = higher priority. LOCAL beats SMB. */
    private fun sourceUriPriority(fileUri: String): Int =
        when (Uri.parse(fileUri).scheme?.lowercase()) {
            "content", "file" -> 2  // local storage — fastest
            "smb" -> 1              // network share
            else -> 0               // archive.org / unknown
        }

    private fun safeStorageFile(
        provider: StorageProvider,
        baseStorageFile: BaseStorageFile,
    ): StorageFile? {
        return runCatching { provider.getStorageFile(baseStorageFile) }
            .getOrNull()
    }

    /**
     * Creates a minimal [Game] entry directly from [groupedStorageFile] without metadata lookup.
     * Used as a guaranteed fallback when platformHint is set but the metadata pipeline returned null.
     */
    private fun createForcedGameEntry(
        groupedStorageFile: GroupedStorageFiles,
        forcedSystemId: String,
        lastIndexedAt: Long,
    ): Game? {
        return try {
            val system = GameSystem.findById(forcedSystemId)
            val primaryFile = groupedStorageFile.primaryFile
            val title = primaryFile.name.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: primaryFile.name
            Game(
                fileName = primaryFile.name,
                fileUri = primaryFile.uri.toString(),
                title = title,
                systemId = system.id.dbname,
                developer = null,
                coverFrontUrl = generateCoverUrl(forcedSystemId, title),
                lastIndexedAt = lastIndexedAt,
                year = null,
                genre = null,
                description = null,
                publisher = null,
            )
        } catch (e: Exception) {
            Timber.e(e, "createForcedGameEntry failed for $forcedSystemId: ${groupedStorageFile.primaryFile.name}")
            null
        }
    }

    private fun cleanUp(startedAtMs: Long) {
        kotlin.runCatching {
            removeDeletedBios(startedAtMs)
        }
        kotlin.runCatching {
            removeDeletedGames(startedAtMs)
        }
        kotlin.runCatching {
            removeDeletedDataFiles(startedAtMs)
        }
    }

    private fun removeDeletedBios(startedAtMs: Long) {
        biosManager.deleteBiosBefore(startedAtMs)
    }

    private fun sortedFilesForScanning(groupedStorageFile: GroupedStorageFiles): List<BaseStorageFile> {
        return groupedStorageFile.dataFiles.sortedBy { it.name } + listOf(groupedStorageFile.primaryFile)
    }

    private fun classifyGroupedStorageFile(groupedStorageFile: GroupedStorageFiles): ClassifiedStorageFile {
        val primaryFile = groupedStorageFile.primaryFile
        val sourcePlatformHint = sourceRepository
            .findSourceForUri(primaryFile.uri.toString())
            ?.platformHint
            ?.takeIf { it.isNotBlank() }

        if (sourcePlatformHint != null) {
            return ClassifiedStorageFile(groupedStorageFile, sourcePlatformHint, FileClassification.PRE_MATCHED)
        }

        val extension = primaryFile.extension.lowercase(Locale.US)
        if (extension == "zip") {
            val matchedSystemId = findSystemIdInPath(primaryFile.path)
            return if (matchedSystemId != null) {
                ClassifiedStorageFile(groupedStorageFile, matchedSystemId, FileClassification.PRE_MATCHED)
            } else {
                ClassifiedStorageFile(groupedStorageFile, SystemID.UNKNOWN.dbname, FileClassification.FORCED_UNKNOWN)
            }
        }

        GameSystem.findByUniqueFileExtension(extension)?.let {
            return ClassifiedStorageFile(groupedStorageFile, it.id.dbname, FileClassification.PRE_MATCHED)
        }

        findSystemIdForPathAndSupportedExtension(primaryFile.path, extension)?.let {
            return ClassifiedStorageFile(groupedStorageFile, it, FileClassification.PRE_MATCHED)
        }

        return if (SUPPORTED_EXTENSIONS.contains(extension)) {
            ClassifiedStorageFile(groupedStorageFile, null, FileClassification.PASSTHROUGH)
        } else {
            ClassifiedStorageFile(groupedStorageFile, null, FileClassification.IGNORED)
        }
    }

    private fun findSystemIdForPathAndSupportedExtension(
        path: String?,
        extension: String,
    ): String? {
        return GameSystem.all()
            .asSequence()
            .filter { it.scanOptions.scanByPathAndSupportedExtensions }
            .filter { it.supportedExtensions.contains(extension) }
            .map { it.id.dbname }
            .firstOrNull { matchesPathSegment(path, it) }
    }

    private fun findSystemIdInPath(path: String?): String? {
        return GameSystem.all()
            .asSequence()
            .map { it.id.dbname }
            .firstOrNull { matchesPathSegment(path, it) }
    }

    private fun matchesPathSegment(path: String?, systemId: String): Boolean {
        if (path.isNullOrBlank()) {
            return false
        }

        val lowerPath = path.lowercase(Locale.getDefault())
        if (lowerPath.contains(systemId)) {
            return true
        }

        return FOLDER_ALIASES[systemId].orEmpty().any { lowerPath.contains(it) }
    }

    private fun convertGameMetadataToGame(
        groupedStorageFile: GroupedStorageFiles,
        storageFile: StorageFile,
        gameMetadata: GameMetadata?,
        lastIndexedAt: Long,
        forcedSystemId: String? = null,
    ): Game? {
        if (gameMetadata == null) {
            return null
        }

        val effectiveSystemId = when {
            forcedSystemId.isNullOrBlank() -> gameMetadata.system!!
            gameMetadata.system.isNullOrBlank() -> forcedSystemId
            gameMetadata.system == SystemID.UNKNOWN.dbname -> forcedSystemId
            else -> forcedSystemId
        }

        val gameSystem = GameSystem.findById(effectiveSystemId)

        // If the databased matched a data file (as with bin/cue) we force link the primary filename
        val fileName =
            if (groupedStorageFile.dataFiles.isNotEmpty()) {
                groupedStorageFile.primaryFile.name
            } else {
                storageFile.name
            }

        return Game(
            fileName = fileName,
            fileUri = groupedStorageFile.primaryFile.uri.toString(),
            title = gameMetadata.name ?: groupedStorageFile.primaryFile.name,
            systemId = gameSystem.id.dbname,
            developer = gameMetadata.developer,
            coverFrontUrl = gameMetadata.thumbnail,
            lastIndexedAt = lastIndexedAt,
            year = gameMetadata.year,
            genre = gameMetadata.genre,
            description = gameMetadata.description,
            publisher = gameMetadata.publisher,
        )
    }

    private fun removeDeletedDataFiles(startedAtMs: Long) {
        Timber.d("Deleting data files from db before: $startedAtMs")
        val dataFiles = retrogradedb.dataFileDao().selectByLastIndexedAtLessThan(startedAtMs)
        retrogradedb.dataFileDao().delete(dataFiles)
    }

    private fun removeDeletedGames(startedAtMs: Long) {
        Timber.d("Deleting games from db before: $startedAtMs")
        val games = retrogradedb.gameDao().selectByLastIndexedAtLessThan(startedAtMs)
        retrogradedb.gameDao().delete(games)
    }

    fun getGameFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val provider = storageProviderRegistry.get()
        return provider.getProvider(game).getGameRomFiles(game, dataFiles, allowVirtualFiles)
    }

    suspend fun deleteGame(game: Game) {
        try {
            val provider = storageProviderRegistry.get().getProvider(game)
            if (provider.delete(game)) {
                Timber.i("Physical file deleted for game: ${game.title}")
            } else {
                Timber.w("Failed to delete physical file for game: ${game.title}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during file deletion for game: ${game.title}")
        }
        
        retrogradedb.gameDao().delete(listOf(game))
    }

    private sealed class ScanEntry {
        data class GameFile(
            val file: GroupedStorageFiles,
            val game: Game,
            val resolvedSystemId: String?,
        ) : ScanEntry()

        data class File(
            val file: GroupedStorageFiles,
            val resolvedSystemId: String?,
        ) : ScanEntry()
    }

    private enum class FileClassification {
        PRE_MATCHED,
        FORCED_UNKNOWN,
        PASSTHROUGH,
        IGNORED,
    }

    private data class ClassifiedStorageFile(
        val file: GroupedStorageFiles,
        val resolvedSystemId: String?,
        val classification: FileClassification,
    )

    private class ProviderScanStats {
        val addedGames = AtomicInteger(0)
        val matchedGames = AtomicInteger(0)
        val unknownGames = AtomicInteger(0)
        val ignoredFiles = AtomicInteger(0)
        val matchedSystems = ConcurrentHashMap.newKeySet<String>()
    }

    companion object {
        // We batch database updates to avoid unnecessary UI updates.
        const val MAX_BUFFER_SIZE = 200
        const val MAX_TIME = 5000

        private val SUPPORTED_EXTENSIONS = GameSystem.getSupportedExtensions().map { it.lowercase(Locale.US) }.toSet()

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
            "3ds" to listOf("nintendo3ds", "nintendo 3ds"),
        )
    }
}
