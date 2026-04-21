package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomSource
import com.swordfish.lemuroid.app.mobile.feature.catalog.SourceManager
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.library.RomMigrationHelper
import com.swordfish.lemuroid.app.utils.android.displayErrorDialog
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StorageFrameworkPickerLauncher : RetrogradeActivity() {
    @Inject
    lateinit var directoriesManager: DirectoriesManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    private var pendingNewUri: Uri? = null
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            showInitialSetupNotice()
        }
    }

    private fun showInitialSetupNotice() {
        AlertDialog.Builder(this)
            .setTitle(R.string.initial_setup_local_only_title)
            .setMessage(R.string.initial_setup_local_only_message)
            .setPositiveButton(R.string.initial_setup_local_only_continue) { _, _ ->
                launchFolderPicker()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun launchFolderPicker() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                this.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                this.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                this.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                this.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            }
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)
        } catch (e: Exception) {
            showStorageAccessFrameworkNotSupportedDialog()
        }
    }

    private fun showStorageAccessFrameworkNotSupportedDialog() {
        val message = getString(R.string.dialog_saf_not_found, directoriesManager.getInternalRomsDirectory())
        val actionLabel = getString(R.string.ok)
        displayErrorDialog(message, actionLabel) { finish() }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            val sharedPreferences = SharedPreferencesHelper.getSharedPreferences(this)
            val preferenceKey = SharedPreferencesHelper.KEY_STORAGE_FOLDER_URI

            val currentValue: String? = sharedPreferences.getString(preferenceKey, null)
            val newValue = resultData?.data

            if (newValue != null && newValue.toString() != currentValue) {
                pendingNewUri = newValue
                checkAndMigrateRoms(newValue)
            } else {
                startLibraryIndexWork()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun checkAndMigrateRoms(newUri: Uri) {
        val migrationHelper = RomMigrationHelper(this, retrogradeDb)
        
        lifecycleScope.launch {
            // Check if current library is local and has ROMs
            if (!migrationHelper.isLocalLibrary()) {
                // SMB library - show warning and proceed without migration
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@StorageFrameworkPickerLauncher,
                        R.string.rom_migration_smb_not_supported,
                        Toast.LENGTH_LONG
                    ).show()
                    proceedWithFolderChange(newUri)
                }
                return@launch
            }

            val romCount = migrationHelper.countExistingRoms()
            
            withContext(Dispatchers.Main) {
                if (romCount > 0) {
                    showMigrationDialog(romCount, newUri, migrationHelper)
                } else {
                    // No ROMs to migrate, proceed directly
                    proceedWithFolderChange(newUri)
                }
            }
        }
    }

    private fun showMigrationDialog(romCount: Int, newUri: Uri, migrationHelper: RomMigrationHelper) {
        val destFolderName = try {
            DocumentFile.fromTreeUri(this, newUri)?.name ?: newUri.lastPathSegment ?: "?"
        } catch (e: Exception) {
            newUri.lastPathSegment ?: "?"
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.rom_migration_title)
            .setMessage(getString(R.string.rom_migration_message, romCount, destFolderName))
            .setPositiveButton(R.string.rom_migration_confirm) { _, _ ->
                performMigration(newUri, migrationHelper)
            }
            .setNegativeButton(R.string.rom_migration_cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun performMigration(newUri: Uri, migrationHelper: RomMigrationHelper) {
        // Show progress dialog
        progressDialog = ProgressDialog(this).apply {
            setTitle(R.string.rom_migration_progress_title)
            setMessage(getString(R.string.rom_migration_progress, "", 0, 0))
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = 100
            show()
        }

        lifecycleScope.launch {
            val result = migrationHelper.migrateRomsSAF(newUri) { current, total, fileName, phase ->
                lifecycleScope.launch(Dispatchers.Main) {
                    progressDialog?.apply {
                        progress = (current * 100) / total
                        setMessage(getString(R.string.rom_migration_progress, fileName, current, total))
                    }
                }
            }

            withContext(Dispatchers.Main) {
                progressDialog?.dismiss()
                progressDialog = null

                when {
                    result.success -> {
                        Toast.makeText(
                            this@StorageFrameworkPickerLauncher,
                            getString(R.string.rom_migration_success, result.movedCount),
                            Toast.LENGTH_LONG
                        ).show()
                        proceedWithFolderChange(newUri)
                    }
                    result.movedCount > 0 -> {
                        // Partial success
                        Toast.makeText(
                            this@StorageFrameworkPickerLauncher,
                            getString(R.string.rom_migration_partial, result.movedCount, result.movedCount + result.failedCount),
                            Toast.LENGTH_LONG
                        ).show()
                        proceedWithFolderChange(newUri)
                    }
                    else -> {
                        // Complete failure
                        Toast.makeText(
                            this@StorageFrameworkPickerLauncher,
                            getString(R.string.rom_migration_failed, result.errors.firstOrNull() ?: "Unknown error"),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun proceedWithFolderChange(newUri: Uri) {
        updatePersistableUris(newUri)
        val folderName = runCatching {
            DocumentFile.fromTreeUri(this, newUri)?.name
        }.getOrNull() ?: (newUri.lastPathSegment ?: "Local Folder")
        com.swordfish.lemuroid.lib.storage.source.SourceRepository(this)
            .upsertByPath(RomSource.local(folderName, newUri.toString()))
        startLibraryIndexWork()
        finish()
    }

    private fun updatePersistableUris(uri: Uri) {
        // Take persistable permission for the new URI
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        // Do NOT revoke other URIs — SourceRepository may reference them as additional sources
    }

    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, StorageFrameworkPickerLauncher::class.java))
        }
    }
}
