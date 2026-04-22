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
        // Inject forcedSystemId as a virtual subfolder prefix in the path so that
        // parentContainsSystem() reliably detects the correct platform even when
        // the real path (e.g. SMB relative path or content:// URI) has no folder context.
        val augmentedFile = storageFile.copy(
            path = "$forcedSystemId/${storageFile.path ?: storageFile.name}",
        )
        val metadata = delegate.retrieveMetadata(augmentedFile)
        return when {
            metadata == null -> {
                // File has no recognized extension/database match, but source has platformHint.
                // Index it anyway under the forced system so the user's choice is respected.
                Timber.d("ForcedSystem: no delegate metadata for ${storageFile.name}, indexing as '$forcedSystemId'")
                GameMetadata(
                    name = storageFile.name.substringBeforeLast(".").takeIf { it.isNotBlank() } ?: storageFile.name,
                    system = forcedSystemId,
                    romName = null,
                    developer = null,
                    thumbnail = null,
                )
            }
            metadata.system != forcedSystemId -> {
                Timber.d("ForcedSystem: overriding '${metadata.system}' → '$forcedSystemId' for ${storageFile.name}")
                metadata.copy(system = forcedSystemId)
            }
            else -> metadata
        }
    }
}
