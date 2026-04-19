package com.swordfish.lemuroid.lib.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.frybits.harmony.getHarmonySharedPreferences
import com.swordfish.lemuroid.common.preferences.SharedPreferencesDataStore
import com.swordfish.lemuroid.lib.R

object SharedPreferencesHelper {
    /** Key for SAF storage folder URI (content:// scheme) */
    const val KEY_STORAGE_FOLDER_URI = "storage_folder_uri"
    const val KEY_TV_CUSTOM_ROMS_PATH = "tv_custom_roms_path"
    
    /** SMB Library keys */
    const val KEY_LIBRARY_TYPE = "library_type" // "local" or "smb"
    const val KEY_SMB_LIBRARY_SERVER = "smb_library_server"
    const val KEY_SMB_LIBRARY_SHARE = "smb_library_share"
    const val KEY_SMB_LIBRARY_PATH = "smb_library_path"
    const val KEY_SMB_LIBRARY_USERNAME = "smb_library_username"
    const val KEY_SMB_LIBRARY_PASSWORD = "smb_library_password"

    fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getHarmonySharedPreferences(context.getString(R.string.pref_file_harmony_options))
    }

    fun getSharedPreferencesDataStore(context: Context): SharedPreferencesDataStore {
        return SharedPreferencesDataStore(getSharedPreferences(context))
    }

    /** Default shared preferences does not work with multi-process. It's currently used only for
     *  stored directory which are only read in the main process.*/
    @Deprecated("Uses standard preference manager. This is not supported in multi-processes.")
    fun getLegacySharedPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * Returns the SAF URI if configured, null otherwise.
     */
    fun getSAFUri(context: Context): String? {
        return getSharedPreferences(context).getString(KEY_STORAGE_FOLDER_URI, null)
    }

    /**
     * Returns the legacy local folder path if configured, null otherwise.
     */
    fun getLegacyFolderPath(context: Context): String? {
        val legacyKey = context.getString(R.string.pref_key_legacy_external_folder)
        return getLegacySharedPreferences(context).getString(legacyKey, null)
    }

    /**
     * Checks if SAF storage is configured.
     */
    fun isSAFConfigured(context: Context): Boolean {
        return getSAFUri(context) != null
    }

    /**
     * Checks if legacy local storage is configured.
     */
    fun isLegacyConfigured(context: Context): Boolean {
        return getLegacyFolderPath(context) != null
    }

    /**
     * Returns true if any storage (SAF or legacy) is configured.
     */
    fun isStorageConfigured(context: Context): Boolean {
        return isSAFConfigured(context) || isLegacyConfigured(context)
    }
}
