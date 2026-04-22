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

    private val saveFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        SharedPreferencesHelper.getSharedPreferences(requireContext())
            .edit().putString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, uri.toString()).apply()
        findPreference<androidx.preference.Preference>("pref_key_tv_save_location")?.summary =
            uriToReadablePathTv(requireContext(), uri.toString())
    }

    private val downloadFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        SharedPreferencesHelper.getSharedPreferences(requireContext())
            .edit().putString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, uri.toString()).apply()
        findPreference<androidx.preference.Preference>("pref_key_tv_download_location")?.summary =
            uriToReadablePathTv(requireContext(), uri.toString())
    }

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
            refreshMetadataSummary()
        }

        refreshSourcesSection()
        refreshStorageLocationSummaries()
    }

    override fun onResume() {
        super.onResume()
        refreshSaveSyncScreen()
        refreshSourcesSection()
        lifecycleScope.launch {
            refreshCleanupPreferenceSummaries()
            refreshMetadataSummary()
        }
    }

    private fun getGamePadPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_gamepad_settings))
    }

    private fun getRomsCategoryPreference() =
        findPreference<androidx.preference.PreferenceCategory>("pref_category_roms")

    private fun refreshStorageLocationSummaries() {
        val ctx = requireContext()
        val prefs = SharedPreferencesHelper.getSharedPreferences(ctx)

        val saveUri = prefs.getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "") ?: ""
        val saveSummary = if (saveUri.isBlank()) {
            getString(R.string.settings_save_location_default)
        } else {
            uriToReadablePathTv(ctx, saveUri)
        }
        findPreference<androidx.preference.Preference>("pref_key_tv_save_location")?.summary = saveSummary

        val downloadId = prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "") ?: ""
        val downloadSummary = if (downloadId.isBlank()) {
            getString(R.string.settings_download_location_default)
        } else {
            uriToReadablePathTv(ctx, downloadId)
        }
        findPreference<androidx.preference.Preference>("pref_key_tv_download_location")?.summary = downloadSummary
    }

    private fun refreshSourcesSection() {
        val category = getRomsCategoryPreference() ?: return
        val ctx = requireContext()
        val sources = com.swordfish.lemuroid.lib.storage.source.SourceRepository(ctx).getCustomSources()

        // Hide the static "Choose Directory" preference — replaced by the dynamic "Add source" button
        category.findPreference<androidx.preference.Preference>(getString(R.string.pref_key_choose_directory))?.isVisible = false

        // Remove any previously added dynamic source entries (key starts with "dyn_source_")
        val keysToRemove = (0 until category.preferenceCount)
            .mapNotNull { category.getPreference(it).key }
            .filter { it.startsWith("dyn_source_") }
        keysToRemove.forEach { key -> category.findPreference<androidx.preference.Preference>(key)?.let { category.removePreference(it) } }

        // Also remove old "add source" button if present
        category.findPreference<androidx.preference.Preference>("dyn_add_source")?.let { category.removePreference(it) }

        // Add one row per configured source
        sources.forEachIndexed { index, source ->
            val pref = androidx.preference.Preference(ctx).apply {
                key = "dyn_source_$index"
                title = source.name
                summary = getString(R.string.settings_source_tap_to_remove)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    android.app.AlertDialog.Builder(ctx)
                        .setTitle(R.string.settings_source_remove_confirm_title)
                        .setMessage(getString(R.string.settings_source_remove_confirm_message, source.name))
                        .setPositiveButton(R.string.delete_games_confirm) { _, _ ->
                            com.swordfish.lemuroid.lib.storage.source.SourceRepository(ctx).removeSource(source.id)
                            com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler.scheduleLibrarySync(ctx.applicationContext)
                            refreshSourcesSection()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                    true
                }
            }
            category.addPreference(pref)
        }

        // "Add source" button
        val addPref = androidx.preference.Preference(ctx).apply {
            key = "dyn_add_source"
            title = getString(R.string.settings_title_add_source)
            summary = getString(R.string.settings_description_add_source)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                launchFolderPicker()
                true
            }
        }
        category.addPreference(addPref)
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
            "pref_key_tv_save_location" -> showSaveLocationDialog()
            "pref_key_tv_download_location" -> showDownloadLocationDialog()
            getString(R.string.pref_key_export_save_games) ->
                exportSavesLauncher.launch("phoenix-down-savegames-backup.zip")
            getString(R.string.pref_key_import_save_games) ->
                importSavesLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            getString(R.string.pref_key_open_manual) ->
                startActivity(Intent(requireContext(), com.swordfish.lemuroid.app.tv.settings.manual.TVManualActivity::class.java))
            getString(R.string.pref_key_open_about) ->
                startActivity(Intent(requireContext(), com.swordfish.lemuroid.app.tv.settings.about.TVAboutActivity::class.java))
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun showDownloadLocationDialog() {
        val ctx = requireContext()
        android.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.settings_title_download_location)
            .setItems(arrayOf(
                getString(R.string.settings_picker_local_folder),
                getString(R.string.settings_picker_smb_server),
                getString(R.string.settings_picker_reset)
            )) { _, which ->
                when (which) {
                    0 -> {
                        val prefs = SharedPreferencesHelper.getSharedPreferences(ctx)
                        val current = prefs.getString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "") ?: ""
                        val initialUri = if (current.startsWith("content://")) android.net.Uri.parse(current) else null
                        downloadFolderPickerLauncher.launch(initialUri)
                    }
                    1 -> showDownloadSmbInputDialog()
                    2 -> {
                        SharedPreferencesHelper.getSharedPreferences(ctx)
                            .edit()
                            .putString(SharedPreferencesHelper.KEY_DOWNLOAD_SOURCE_ID, "")
                            .putString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_USERNAME, "")
                            .putString(SharedPreferencesHelper.KEY_DOWNLOAD_SMB_PASSWORD, "")
                            .apply()
                        findPreference<androidx.preference.Preference>("pref_key_tv_download_location")?.summary =
                            getString(R.string.settings_download_location_default)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDownloadSmbInputDialog() {
        startActivity(
            Intent(requireContext(), TVSmbConfigActivity::class.java)
                .putExtra(TVSmbConfigActivity.EXTRA_MODE, TVSmbConfigActivity.MODE_DOWNLOAD),
        )
    }

    private fun showSaveLocationDialog() {
        val ctx = requireContext()
        android.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.settings_title_save_location)
            .setItems(arrayOf(
                getString(R.string.settings_picker_local_folder),
                getString(R.string.settings_picker_smb_server),
                getString(R.string.settings_picker_reset)
            )) { _, which ->
                when (which) {
                    0 -> {
                        val prefs = SharedPreferencesHelper.getSharedPreferences(ctx)
                        val current = prefs.getString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "") ?: ""
                        val initialUri = if (current.startsWith("content://")) android.net.Uri.parse(current) else null
                        saveFolderPickerLauncher.launch(initialUri)
                    }
                    1 -> showSaveSmbInputDialog()
                    2 -> {
                        SharedPreferencesHelper.getSharedPreferences(ctx)
                            .edit()
                            .putString(SharedPreferencesHelper.KEY_SAVE_LOCATION_URI, "")
                            .putString(SharedPreferencesHelper.KEY_SAVE_SMB_USERNAME, "")
                            .putString(SharedPreferencesHelper.KEY_SAVE_SMB_PASSWORD, "")
                            .apply()
                        findPreference<androidx.preference.Preference>("pref_key_tv_save_location")?.summary =
                            getString(R.string.settings_save_location_default)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSaveSmbInputDialog() {
        startActivity(
            Intent(requireContext(), TVSmbConfigActivity::class.java)
                .putExtra(TVSmbConfigActivity.EXTRA_MODE, TVSmbConfigActivity.MODE_SAVE),
        )
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
            .putExtra(TVSmbConfigActivity.EXTRA_MODE, TVSmbConfigActivity.MODE_LIBRARY)
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

    companion object {
        fun uriToReadablePathTv(context: Context, uri: String): String {
            if (uri.isBlank()) return ""
            if (uri.startsWith("smb://")) return uri
            if (uri.startsWith("content://")) {
                return runCatching {
                    val docId = android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uri))
                    if (docId.contains(":")) {
                        val (vol, path) = docId.split(":", limit = 2)
                        if (vol == "primary") "/storage/emulated/0/$path" else "/storage/$vol/$path"
                    } else docId
                }.getOrElse { uri }
            }
            return uri
        }
    }

    @dagger.Module
    class Module
}
