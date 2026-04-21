package com.swordfish.lemuroid.lib.saves

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SourceRepository
import com.swordfish.lemuroid.lib.storage.source.SourceType

/**
 * Resolves which [SavesStorage] implementation to use based on the user's configured
 * save location preference ([SharedPreferencesHelper.KEY_SAVE_LOCATION_URI]).
 *
 * - Empty / missing preference → [LocalSavesStorage] (default behaviour)
 * - Source of type [SourceType.LOCAL] → [SafSavesStorage] (SAF content:// URI)
 * - Source of type [SourceType.SMB] → [SmbSavesStorage]
 * - Anything else → [LocalSavesStorage] (fallback)
 *
 * [resolve] is intentionally called on every operation so that the result always
 * reflects the current preference without requiring a restart.
 */
class SavesStorageResolver(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
    private val sourceRepository: SourceRepository,
) {
    fun resolve(): SavesStorage {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val sourceId = prefs.getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "")
            ?.takeIf { it.isNotBlank() }
            ?: return LocalSavesStorage(directoriesManager)

        val source: RomSource = sourceRepository.getCustomSources()
            .firstOrNull { it.id == sourceId }
            ?: return LocalSavesStorage(directoriesManager)

        return when (source.type) {
            SourceType.LOCAL -> SafSavesStorage(context, Uri.parse(source.path))
            SourceType.SMB -> SmbSavesStorage(source)
            else -> LocalSavesStorage(directoriesManager)
        }
    }
}
