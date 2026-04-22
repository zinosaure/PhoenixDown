package com.swordfish.lemuroid.lib.saves

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.source.NetworkProtocol
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SmbLoginProfileRepository
import com.swordfish.lemuroid.lib.storage.source.SmbLoginProfile
import com.swordfish.lemuroid.lib.storage.source.SourceCredentials
import com.swordfish.lemuroid.lib.storage.source.SourceType

/**
 * Resolves which [SavesStorage] implementation to use based on the value of
 * [SharedPreferencesHelper.KEY_SAVE_LOCATION_URI]:
 *
 *  - `""` (empty)          → [LocalSavesStorage] (default — internal app storage)
 *  - `"content://…"`       → [SafSavesStorage]  (SAF folder chosen by the user)
 *  - `"smb://…"`           → [SmbSavesStorage]  (SMB share)
 *  - `"sftp://…"`          → [SftpSavesStorage] (SFTP server)
 *  - anything else         → [LocalSavesStorage] (graceful fallback for old RomSource IDs)
 *
 * [resolve] is called on every save/state operation so that a preference change is
 * immediately effective without requiring a restart.
 */
class SavesStorageResolver(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    private val profileRepository by lazy { SmbLoginProfileRepository(context) }

    private fun resolveProfile(profileKey: String): SmbLoginProfile? {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val profileId = prefs.getString(profileKey, "")?.takeIf { it.isNotBlank() } ?: return null
        return profileRepository.getProfiles().firstOrNull { it.id == profileId }
    }

    private fun schemeForProtocol(protocol: NetworkProtocol): String = when (protocol) {
        NetworkProtocol.SMB -> "smb"
        NetworkProtocol.SFTP -> "sftp"
        NetworkProtocol.WEBDAV -> "davs"
        NetworkProtocol.WEBDAV_HTTP -> "dav"
    }

    private fun normalizeUriForProtocol(uri: String, protocol: NetworkProtocol): String {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return uri
        val authority = parsed.authority ?: return uri
        val path = parsed.path.orEmpty().ifBlank { "/" }
        return "${schemeForProtocol(protocol)}://$authority$path"
    }

    fun resolve(): SavesStorage {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val loc = prefs
            .getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "")
            ?.takeIf { it.isNotBlank() }
            ?: return LocalSavesStorage(directoriesManager)

        return when {
            loc.startsWith("content://") -> SafSavesStorage(context, Uri.parse(loc))
            else -> {
                // For network saves, profileId is authoritative for protocol/credentials.
                val profile = resolveProfile(SharedPreferencesHelper.KEY_SAVE_NETWORK_PROFILE_ID)
                    ?: throw IllegalStateException("Network save location requires a valid networkProfileId")
                val protocol = profile.protocol
                val credentials = if (profile.username.isNotBlank()) {
                    SourceCredentials(profile.username, profile.password)
                } else {
                    null
                }

                when (protocol) {
                    NetworkProtocol.SMB -> {
                        val smbUri = normalizeUriForProtocol(loc, NetworkProtocol.SMB)
                        SmbSavesStorage(
                            RomSource(type = SourceType.SMB, name = "Saves", path = smbUri, id = "_save", credentials = credentials),
                        )
                    }

                    NetworkProtocol.SFTP -> {
                        val sftpUri = normalizeUriForProtocol(loc, NetworkProtocol.SFTP)
                        SftpSavesStorage(
                            RomSource(type = SourceType.SMB, name = "Saves", path = sftpUri, id = "_save", credentials = credentials),
                        )
                    }

                    else -> LocalSavesStorage(directoriesManager)
                }
            }
        }
    }
}
