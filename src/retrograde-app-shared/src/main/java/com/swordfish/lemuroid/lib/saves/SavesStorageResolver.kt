package com.swordfish.lemuroid.lib.saves

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials
import com.swordfish.lemuroid.lib.storage.source.SourceType

/**
 * Resolves which [SavesStorage] implementation to use based on the value of
 * [SharedPreferencesHelper.KEY_SAVE_LOCATION_URI]:
 *
 *  - `""` (empty)          → [LocalSavesStorage] (default — internal app storage)
 *  - `"content://…"`       → [SafSavesStorage]  (SAF folder chosen by the user)
 *  - `"smb://…"`           → [SmbSavesStorage]  (SMB share)
 *  - anything else         → [LocalSavesStorage] (graceful fallback for old RomSource IDs)
 *
 * [resolve] is called on every save/state operation so that a preference change is
 * immediately effective without requiring a restart.
 */
class SavesStorageResolver(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    fun resolve(): SavesStorage {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val loc = prefs
            .getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "")
            ?.takeIf { it.isNotBlank() }
            ?: return LocalSavesStorage(directoriesManager)

        return when {
            loc.startsWith("content://") -> SafSavesStorage(context, Uri.parse(loc))
            loc.startsWith("smb://") -> {
                val username = prefs.getString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, "") ?: ""
                val password = prefs.getString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, "") ?: ""
                val credentials = if (username.isNotBlank()) SourceCredentials(username, password) else null
                SmbSavesStorage(
                    RomSource(type = SourceType.SMB, name = "Saves", path = loc, id = "_save", credentials = credentials),
                )
            }
            else -> LocalSavesStorage(directoriesManager) // old RomSource.id → graceful fallback
        }
    }
}
