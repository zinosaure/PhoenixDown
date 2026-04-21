package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomSource
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
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

    // -----------------------------------------------------------------------
    // ROM Sources — reactive StateFlow backed by SharedPreferences listener
    // -----------------------------------------------------------------------

    private val sourceRepository = SourceRepository(context)

    /**
     * Emits the full source list whenever ANY code path writes to rom_sources prefs:
     * DI migration, StorageFrameworkPickerLauncher, UI add/update/remove.
     */
    val sources: StateFlow<List<RomSource>> = sourceRepository.sourcesFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            sourceRepository.getSources(),
        )

    /** Force a list refresh (e.g. after returning to screen). */
    fun reloadSources() { /* StateFlow updates automatically via SharedPreferences listener */ }

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

    fun removeSource(sourceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sourceRepository.removeSource(sourceId)
            LibraryIndexScheduler.scheduleLibrarySync(context)
        }
    }
}
