package com.swordfish.lemuroid.app.tv.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.InputDevice
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.SaveSyncPreferences
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.app.shared.storage.cache.StorageCleanupManager
import com.swordfish.lemuroid.app.shared.storage.saves.SaveGamesBackupManager
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.common.kotlin.NTuple2
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.smb.SmbClient
import com.swordfish.lemuroid.lib.storage.smb.SmbCredentials
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class TVSettingsFragment : LeanbackPreferenceFragmentCompat() {
    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var biosPreferences: BiosPreferences

    @Inject
    lateinit var gamePadPreferencesHelper: GamePadPreferencesHelper

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var coresSelectionPreferences: CoresSelectionPreferences

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    lateinit var saveSyncPreferences: SaveSyncPreferences

    private val exportSavesLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { SaveGamesBackupManager.exportToZip(requireContext(), uri) }
                .onSuccess { requireActivity().displayToast(getString(R.string.settings_savegames_export_success, it.filesCount)) }
                .onFailure { requireActivity().displayToast(getString(R.string.settings_savegames_backup_failed, it.message ?: "error")) }
        }
    }

    private val importSavesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { SaveGamesBackupManager.importFromZip(requireContext(), uri) }
                .onSuccess { requireActivity().displayToast(getString(R.string.settings_savegames_import_success, it.filesCount)) }
                .onFailure { requireActivity().displayToast(getString(R.string.settings_savegames_backup_failed, it.message ?: "error")) }
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)

        saveSyncPreferences = SaveSyncPreferences(saveSyncManager)

        super.onAttach(context)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        launchOnState(Lifecycle.State.CREATED) {
            val gamePadStatus =
                combine(
                    inputDeviceManager.getGamePadsObservable(),
                    inputDeviceManager.getEnabledInputsObservable(),
                    ::NTuple2,
                )

            gamePadStatus
                .distinctUntilChanged()
                .collect { (pads, enabledPads) -> addGamePadBindingsScreen(pads, enabledPads) }
        }

        launchOnState(Lifecycle.State.RESUMED) {
            inputDeviceManager.getEnabledInputsObservable()
                .distinctUntilChanged()
                .collect { refreshGamePadBindingsScreen(it) }
        }

        launchOnState(Lifecycle.State.RESUMED) {
            getSaveSyncScreen()?.let { screen ->
                PendingOperationsMonitor(requireContext())
                    .anySaveOperationInProgress()
                    .safeCollect { syncInProgress ->
                        saveSyncPreferences.updatePreferences(screen, syncInProgress)
                    }
            }
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceManager.preferenceDataStore =
            SharedPreferencesHelper.getSharedPreferencesDataStore(requireContext())
        setPreferencesFromResource(R.xml.tv_settings, rootKey)

        getCoresSelectionScreen()?.let {
            coresSelectionPreferences.addCoresSelectionPreferences(it)
        }

        getBiosInfoPreferenceScreen()?.let {
            biosPreferences.addBiosPreferences(it)
        }

        getAdvancedSettingsPreferenceScreen()?.let {
            AdvancedSettingsPreferences.updateCachePreferences(it)
        }

        getSaveSyncScreen()?.let {
            if (saveSyncManager.isSupported()) {
                saveSyncPreferences.addSaveSyncPreferences(it)
            }
            it.isVisible = saveSyncManager.isSupported()
        }

        lifecycleScope.launch {
            refreshCleanupPreferenceSummaries()
            refreshCoverStorageSummary()
            refreshMetadataSummary()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSaveSyncScreen()
        lifecycleScope.launch {
            refreshCleanupPreferenceSummaries()
            refreshCoverStorageSummary()
            refreshMetadataSummary()
        }
    }

    private fun getGamePadPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_gamepad_settings))
    }

    private fun getSaveSyncScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_save_sync_settings))
    }

    private fun getCoresSelectionScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_cores_selection))
    }

    private fun getBiosInfoPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_display_bios_info))
    }

    private fun getAdvancedSettingsPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_advanced_settings))
    }

    private fun addGamePadBindingsScreen(
        gamePads: List<InputDevice>,
        enabledGamePads: List<InputDevice>,
    ) {
        lifecycleScope.launch {
            getGamePadPreferenceScreen()?.let {
                it.removeAll()
                gamePadPreferencesHelper.addGamePadsPreferencesToScreen(
                    it.context,
                    it,
                    gamePads,
                    enabledGamePads,
                )
            }
        }
    }

    private fun refreshGamePadBindingsScreen(enabledGamePads: List<InputDevice>) {
        lifecycleScope.launch {
            getGamePadPreferenceScreen()?.let {
                gamePadPreferencesHelper.refreshGamePadsPreferencesToScreen(it, enabledGamePads)
            }
        }
    }

    private fun refreshSaveSyncScreen() {
        getSaveSyncScreen()?.let {
            saveSyncPreferences.updatePreferences(it, false)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (saveSyncPreferences.onPreferenceTreeClick(activity, preference)) {
            return true
        }

        val cleanupType = resolveCleanupTypeFromPreferenceKey(preference.key)
        if (cleanupType != null) {
            lifecycleScope.launch {
                confirmAndCleanBucket(cleanupType)
            }
            return true
        }

        when (preference.key) {
            getString(R.string.pref_key_reset_gamepad_bindings) ->
                lifecycleScope.launch {
                    handleResetGamePadBindings()
                }
            getString(R.string.pref_key_reset_settings) -> confirmResetSettings()
            getString(R.string.pref_key_choose_directory) -> launchFolderPicker()
            getString(R.string.pref_key_edit_thegamesdb_apikey) -> showApiKeyDialog()
            getString(R.string.pref_key_export_save_games) ->
                exportSavesLauncher.launch("retromul-savegames-backup.zip")
            getString(R.string.pref_key_import_save_games) ->
                importSavesLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            getString(R.string.pref_key_open_manual) ->
                startActivity(Intent(requireContext(), com.swordfish.lemuroid.app.tv.settings.manual.TVManualActivity::class.java))
            getString(R.string.pref_key_open_about) ->
                startActivity(Intent(requireContext(), com.swordfish.lemuroid.app.tv.settings.about.TVAboutActivity::class.java))
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun launchFolderPicker() {
        // Show dialog to choose library source
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(com.swordfish.lemuroid.lib.R.string.library_source_dialog_title)
            .setItems(arrayOf(
                getString(com.swordfish.lemuroid.lib.R.string.library_source_local),
                getString(com.swordfish.lemuroid.lib.R.string.library_source_smb)
            )) { _, which ->
                when (which) {
                    0 -> launchLocalFolderPicker()
                    1 -> launchSmbConfigActivity()
                }
            }
            .show()
    }
    
    private fun launchLocalFolderPicker() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(requireContext())
        prefs.edit().putString(SharedPreferencesHelper.KEY_LIBRARY_TYPE, "local").apply()
        
        if (com.swordfish.lemuroid.app.tv.shared.TVHelper.isSAFSupported(requireContext())) {
            com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher.pickFolder(requireContext())
        } else {
            com.swordfish.lemuroid.app.tv.folderpicker.TVFolderPickerLauncher.pickFolder(requireContext())
        }
    }
    
    private fun launchSmbConfigActivity() {
        // Launch SMB configuration activity
        val intent = android.content.Intent(requireContext(), com.swordfish.lemuroid.app.tv.settings.TVSmbConfigActivity::class.java)
        startActivity(intent)
    }

    private suspend fun handleResetGamePadBindings() {
        inputDeviceManager.resetAllBindings()
        refreshGamePadBindingsScreen(inputDeviceManager.getGamePadsObservable().first())
    }

    private fun handleResetSettings() {
        settingsInteractor.resetAllSettings()
        activity?.finish()
    }

    private fun confirmResetSettings() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.reset_settings_warning_message_title)
            .setMessage(R.string.reset_settings_warning_message_description)
            .setPositiveButton(R.string.ok) { _, _ -> handleResetSettings() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun refreshCleanupPreferenceSummaries() {
        val context = requireContext()
        val buckets = StorageCleanupManager.getBuckets(context)

        buckets.forEach { bucket ->
            val key = getString(StorageCleanupManager.getBucketPreferenceKeyRes(bucket.type))
            val summary = getString(
                R.string.settings_clear_entry_summary,
                Formatter.formatShortFileSize(context, bucket.sizeBytes),
            )
            findPreference<Preference>(key)?.summary = summary
        }
    }

    private suspend fun refreshCoverStorageSummary() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(requireContext())
        val directoryUri = prefs.getString(SharedPreferencesHelper.KEY_STORAGE_FOLDER_URI, "") ?: ""
        val libraryType = prefs.getString(SharedPreferencesHelper.KEY_LIBRARY_TYPE, "local")
        val summary = buildCoverStorageSummary(directoryUri, libraryType)
        findPreference<Preference>(getString(R.string.pref_key_cover_storage_info))?.summary = summary
    }

    private fun refreshMetadataSummary() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(requireContext())
        val key = getString(R.string.settings_title_thegamesdb_apikey)
        val value = prefs.getString(key, "").orEmpty()
        val summary = if (value.isNotBlank()) {
            getString(R.string.settings_thegamesdb_configured)
        } else {
            getString(R.string.settings_thegamesdb_not_configured)
        }
        findPreference<Preference>(getString(R.string.pref_key_edit_thegamesdb_apikey))?.summary = summary
    }

    private fun showApiKeyDialog() {
        val ctx = requireContext()
        val prefs = SharedPreferencesHelper.getSharedPreferences(ctx)
        val key = getString(R.string.settings_title_thegamesdb_apikey)
        val editText = EditText(ctx).apply {
            setSingleLine(true)
            setText(prefs.getString(key, "").orEmpty())
        }

        android.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.metadata_dialog_apikey_title)
            .setView(editText)
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.edit().putString(key, editText.text?.toString().orEmpty()).apply()
                refreshMetadataSummary()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun buildCoverStorageSummary(
        directoryUri: String,
        libraryType: String?,
    ): String {
        val context = requireContext()
        val uri = runCatching { Uri.parse(directoryUri) }.getOrNull()

        if (libraryType == "smb") {
            val prefs = SharedPreferencesHelper.getSharedPreferences(context)
            val server = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, "")
            val share = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SHARE, "")
            val path = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PATH, "")
            val smbBase = listOfNotNull(server?.takeIf { it.isNotBlank() }, share?.takeIf { it.isNotBlank() }, path?.trim('/')?.takeIf { it.isNotBlank() })
                .joinToString("/")
            val username = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, null)
            val password = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, null).orEmpty()
            val credentials = username?.takeIf { it.isNotBlank() }?.let { SmbCredentials(it, password) }
            val smbWritable = if (!server.isNullOrBlank() && !share.isNullOrBlank()) {
                runCatching { SmbClient().isShareWritable(server, share, credentials) }.getOrDefault(false)
            } else {
                null
            }

            val remotePath = if (smbBase.isNotBlank()) "//$smbBase/GameCovers" else "GameCovers"
            return when (smbWritable) {
                true -> "SMB RW: $remotePath"
                false -> "SMB RO: cache local (GameCovers)"
                null -> "SMB: verification en cours..."
            }
        }

        if (uri?.scheme == "file") {
            val romDir = uri.path ?: ""
            return "$romDir/GameCovers (local)"
        }

        if (uri != null && DocumentFile.fromTreeUri(context, uri) != null) {
            return "SAF: GameCovers in selected storage (local)"
        }

        return getString(R.string.none)
    }

    private suspend fun confirmAndCleanBucket(type: StorageCleanupManager.BucketType) {
        val ctx = requireContext()
        val currentBuckets = StorageCleanupManager.getBuckets(ctx)
        val bucket = currentBuckets.firstOrNull { it.type == type } ?: return
        val label = Formatter.formatShortFileSize(ctx, bucket.sizeBytes)
        val bucketName = getString(StorageCleanupManager.getBucketLabelRes(type))

        android.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.settings_clear_confirm_title)
            .setMessage(getString(R.string.settings_clear_confirm_message, bucketName, label))
            .setPositiveButton(R.string.delete_games_confirm) { _, _ ->
                lifecycleScope.launch {
                    val freed = StorageCleanupManager.cleanBucket(ctx, type)
                    val freedLabel = Formatter.formatShortFileSize(ctx, freed)
                    requireActivity().displayToast(
                        getString(R.string.settings_clear_done, bucketName, freedLabel),
                    )
                    refreshCleanupPreferenceSummaries()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun resolveCleanupTypeFromPreferenceKey(key: String): StorageCleanupManager.BucketType? {
        return StorageCleanupManager.BucketType.entries.firstOrNull {
            key == getString(StorageCleanupManager.getBucketPreferenceKeyRes(it))
        }
    }

    @dagger.Module
    class Module
}
