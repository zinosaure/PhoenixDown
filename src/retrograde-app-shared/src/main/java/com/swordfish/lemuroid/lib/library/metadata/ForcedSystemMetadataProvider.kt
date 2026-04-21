package com.swordfish.lemuroid.lib.library.metadata

import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import timber.log.Timber

/**
 * Wraps another [GameMetadataProvider] and overrides the detected system with [forcedSystemId]
 * when the file is a compressed archive (.zip, .7z, .rar, .gz).
 *
 * Used when a source has a [platformHint]: the user explicitly declared the target platform,
 * so we skip the ambiguous folder/extension heuristics for archives.
 */
class ForcedSystemMetadataProvider(
    private val delegate: GameMetadataProvider,
    private val forcedSystemId: String,
) : GameMetadataProvider {

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        val metadata = delegate.retrieveMetadata(storageFile) ?: return null
        val ext = storageFile.extension.lowercase()
        return if (ext in StorageFile.ARCHIVE_EXTENSIONS && metadata.system != forcedSystemId) {
            Timber.d("ForcedSystem: overriding '${metadata.system}' → '$forcedSystemId' for ${storageFile.name}")
            metadata.copy(system = forcedSystemId)
        } else {
            metadata
        }
    }
}
