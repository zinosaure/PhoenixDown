package com.swordfish.lemuroid.lib.library.metadata

import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import timber.log.Timber

/**
 * Wraps another [GameMetadataProvider] and forces ALL discovered files to [forcedSystemId].
 *
 * Used when a source has a [platformHint]: the user explicitly declared the target platform,
 * so every file found in that source is assigned to that system regardless of extension or path.
 */
class ForcedSystemMetadataProvider(
    private val delegate: GameMetadataProvider,
    private val forcedSystemId: String,
) : GameMetadataProvider {

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        val metadata = delegate.retrieveMetadata(storageFile) ?: return null
        return if (metadata.system != forcedSystemId) {
            Timber.d("ForcedSystem: overriding '${metadata.system}' → '$forcedSystemId' for ${storageFile.name}")
            metadata.copy(system = forcedSystemId)
        } else {
            metadata
        }
    }
}
