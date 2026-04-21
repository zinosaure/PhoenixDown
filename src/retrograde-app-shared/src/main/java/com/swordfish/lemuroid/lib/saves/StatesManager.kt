package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.compressBytesGzip
import com.swordfish.lemuroid.common.kotlin.readBytesUncompressed
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

// TODO Since states are core related we should not put them in the same folder. This break previous versions states
// so I decided to manage a transition phase reading also the old directory. We should safely remove it in a few weeks.

class StatesManager(
    private val storageResolver: SavesStorageResolver,
    private val directoriesManager: DirectoriesManager,
) {
    suspend fun getSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int,
    ): SaveState? =
        withContext(Dispatchers.IO) {
            assert(index in 0 until MAX_STATES)
            getSaveState(getSlotSaveFileName(game, index), coreID.coreName)
        }

    suspend fun setSlotSave(
        game: Game,
        saveState: SaveState,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        setSaveState(getSlotSaveFileName(game, index), coreID.coreName, saveState)
    }

    suspend fun getAutoSaveInfo(
        game: Game,
        coreID: CoreID,
    ): SaveInfo =
        withContext(Dispatchers.IO) {
            val storage = storageResolver.resolve()
            storage.info("states/${coreID.coreName}/${getAutoSaveFileName(game)}")
        }

    suspend fun getAutoSave(
        game: Game,
        coreID: CoreID,
    ) = withContext(Dispatchers.IO) {
        getSaveState(getAutoSaveFileName(game), coreID.coreName)
    }

    suspend fun setAutoSave(
        game: Game,
        coreID: CoreID,
        saveState: SaveState,
    ) = withContext(Dispatchers.IO) {
        setSaveState(getAutoSaveFileName(game), coreID.coreName, saveState)
    }

    suspend fun getSavedSlotsInfo(
        game: Game,
        coreID: CoreID,
    ): List<SaveInfo> =
        withContext(Dispatchers.IO) {
            val storage = storageResolver.resolve()
            (0 until MAX_STATES)
                .map { index ->
                    val path = "states/${coreID.coreName}/${getSlotSaveFileName(game, index)}"
                    storage.info(path)
                }
                .toList()
        }

    private suspend fun getSaveState(
        fileName: String,
        coreName: String,
    ): SaveState? {
        return runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val storage = storageResolver.resolve()
            val statePath = "states/$coreName/$fileName"
            val metadataPath = "states/$coreName/$fileName.metadata"

            // Try reading from configured storage first, fall back to deprecated local path
            val stateBytes = storage.readBytes(statePath)
                ?: readDeprecatedLocalBytes(fileName)
                ?: return@runCatchingWithRetry null

            if (stateBytes.isEmpty()) return@runCatchingWithRetry null

            val decompressed = stateBytes.readBytesUncompressed()
            val metadataBytes = storage.readBytes(metadataPath)
            val stateMetadata = runCatching {
                metadataBytes?.let {
                    Json.Default.decodeFromString(SaveState.Metadata.serializer(), it.decodeToString())
                }
            }
            SaveState(decompressed, stateMetadata.getOrNull() ?: SaveState.Metadata())
        }.getOrNull()
    }

    private fun readDeprecatedLocalBytes(fileName: String): ByteArray? {
        val deprecatedFile = File(directoriesManager.getInternalStatesDirectory(), fileName)
        return if (deprecatedFile.exists()) deprecatedFile.readBytes() else null
    }

    private suspend fun setSaveState(
        fileName: String,
        coreName: String,
        saveState: SaveState,
    ) {
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val storage = storageResolver.resolve()
            val statePath = "states/$coreName/$fileName"
            val metadataPath = "states/$coreName/$fileName.metadata"
            val compressed = compressBytesGzip(saveState.state)
            storage.writeBytes(statePath, compressed)
            val metadataJson = Json.encodeToString(SaveState.Metadata.serializer(), saveState.metadata)
            storage.writeBytes(metadataPath, metadataJson.encodeToByteArray())
        }
    }

    private fun getAutoSaveFileName(game: Game) = "${game.fileName}.state"

    private fun getSlotSaveFileName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}"

    companion object {
        const val MAX_STATES = 4
        private const val FILE_ACCESS_RETRIES = 3
    }
}
