package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.storage.source.NetworkLoginProfile
import com.swordfish.lemuroid.lib.storage.source.NetworkLoginProfileRepository
import com.swordfish.lemuroid.lib.storage.source.RomSource
import com.swordfish.lemuroid.lib.storage.source.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val settingsInteractor: SettingsInteractor,
    saveSyncManager: SaveSyncManager,
    private val sharedPreferences: FlowSharedPreferences,
) : ViewModel() {
    class Factory(
        private val context: Context,
        private val settingsInteractor: SettingsInteractor,
        private val saveSyncManager: SaveSyncManager,
        val sharedPreferences: FlowSharedPreferences,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                context,
                settingsInteractor,
                saveSyncManager,
                sharedPreferences,
            ) as T
        }
    }

    data class State(
        val currentDirectory: String = "",
        val isSaveSyncSupported: Boolean = false,
    )

    val indexingInProgress = PendingOperationsMonitor(context).anyLibraryOperationInProgress()

    val directoryScanInProgress = PendingOperationsMonitor(context).isDirectoryScanInProgress()

    private val sourceRepository = SourceRepository(context)
    private val networkLoginProfileRepository = NetworkLoginProfileRepository(context)

    /** Live list of user-configured ROM sources, auto-updated via in-process SharedFlow. */
    val sources: StateFlow<List<RomSource>> = sourceRepository.sourcesFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sourceRepository.getSources())

    val networkLoginProfiles: StateFlow<List<NetworkLoginProfile>> = networkLoginProfileRepository.profilesFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), networkLoginProfileRepository.getProfiles())

    fun addSource(source: RomSource) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepository.addSource(source)
            LibraryIndexScheduler.scheduleLibrarySync(context)
        }
    }

    fun updateSource(source: RomSource) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepository.updateSource(source)
            LibraryIndexScheduler.scheduleLibrarySync(context)
        }
    }

    fun removeSource(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepository.removeSource(id)
            LibraryIndexScheduler.scheduleLibrarySync(context)
        }
    }

    val uiState =
        sharedPreferences.getString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_STORAGE_FOLDER_URI)
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")
            .map { State(it, saveSyncManager.isSupported()) }

    fun changeLocalStorageFolder() {
        settingsInteractor.changeLocalStorageFolder()
    }

    val theGamesDbApiKey =
        sharedPreferences.getString(context.getString(R.string.settings_title_thegamesdb_apikey), "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    fun setTheGamesDbApiKey(apiKey: String) {
        sharedPreferences.getString(context.getString(R.string.settings_title_thegamesdb_apikey), "").set(apiKey)
    }

    /** Save location (SAF URI). Empty string = use internal default. */
    val saveLocationUri: StateFlow<String> =
        sharedPreferences.getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val saveLocationUsername: StateFlow<String> =
        sharedPreferences.getString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val saveLocationPassword: StateFlow<String> =
        sharedPreferences.getString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val saveLocationProfileId: StateFlow<String> =
        sharedPreferences.getString(SharedPreferencesHelper.KEY_SAVE_NETWORK_PROFILE_ID, "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    fun setSaveLocation(uri: String, username: String = "", password: String = "", profileId: String? = null) {
        sharedPreferences.getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "").set(uri)
        sharedPreferences.getString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, "").set(username)
        sharedPreferences.getString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, "").set(password)
        sharedPreferences.getString(SharedPreferencesHelper.KEY_SAVE_NETWORK_PROFILE_ID, "").set(profileId.orEmpty())
    }

    /** Download location: RomSource ID. Empty = Android /Downloads. */
    val downloadSourceId: StateFlow<String> =
        sharedPreferences.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val downloadLocationUsername: StateFlow<String> =
        sharedPreferences.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_USERNAME, "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val downloadLocationPassword: StateFlow<String> =
        sharedPreferences.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_PASSWORD, "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val downloadLocationProfileId: StateFlow<String> =
        sharedPreferences.getString(SharedPreferencesHelper.KEY_DOWNLOAD_NETWORK_PROFILE_ID, "")
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, "")

    fun setDownloadSourceId(id: String, username: String = "", password: String = "", profileId: String? = null) {
        sharedPreferences.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "").set(id)
        sharedPreferences.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_USERNAME, "").set(username)
        sharedPreferences.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_PASSWORD, "").set(password)
        sharedPreferences.getString(SharedPreferencesHelper.KEY_DOWNLOAD_NETWORK_PROFILE_ID, "").set(profileId.orEmpty())
    }

    fun addOrUpdateNetworkLoginProfile(profile: NetworkLoginProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            networkLoginProfileRepository.addOrUpdateProfile(profile)
        }
    }

    fun removeNetworkLoginProfile(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            networkLoginProfileRepository.removeProfile(id)
        }
    }
}
