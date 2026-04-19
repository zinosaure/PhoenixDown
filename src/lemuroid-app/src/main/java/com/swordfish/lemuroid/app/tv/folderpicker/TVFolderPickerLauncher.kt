package com.swordfish.lemuroid.app.tv.folderpicker

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.library.RomMigrationHelper
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class TVFolderPickerLauncher : ImmersiveActivity() {
    
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    private var pendingNewPath: String? = null
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            startActivityForResult(Intent(this, TVFolderPickerActivity::class.java), REQUEST_CODE_PICK_FOLDER)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
            val preferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)

            val currentValue: String? = sharedPreferences.getString(preferenceKey, null)
            val newValue = resultData?.extras?.getString(TVFolderPickerActivity.RESULT_DIRECTORY_PATH)

            if (newValue != null && newValue != currentValue) {
                pendingNewPath = newValue
                checkAndMigrateRoms(newValue)
            } else {
                startLibraryIndexWork()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun checkAndMigrateRoms(newPath: String) {
        val migrationHelper = RomMigrationHelper(this, retrogradeDb)
        
        lifecycleScope.launch {
            // Check if current library is local and has ROMs
            if (!migrationHelper.isLocalLibrary()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TVFolderPickerLauncher,
                        R.string.rom_migration_smb_not_supported,
                        Toast.LENGTH_LONG
                    ).show()
                    proceedWithFolderChange(newPath)
                }
                return@launch
            }

            val romCount = migrationHelper.countExistingRoms()
            
            withContext(Dispatchers.Main) {
                if (romCount > 0) {
                    showMigrationDialog(romCount, newPath, migrationHelper)
                } else {
                    proceedWithFolderChange(newPath)
                }
            }
        }
    }

    private fun showMigrationDialog(romCount: Int, newPath: String, migrationHelper: RomMigrationHelper) {
        val destFolderName = File(newPath).name

        AlertDialog.Builder(this)
            .setTitle(R.string.rom_migration_title)
            .setMessage(getString(R.string.rom_migration_message, romCount, destFolderName))
            .setPositiveButton(R.string.rom_migration_confirm) { _, _ ->
                performMigration(newPath, migrationHelper)
            }
            .setNegativeButton(R.string.rom_migration_cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun performMigration(newPath: String, migrationHelper: RomMigrationHelper) {
        progressDialog = ProgressDialog(this).apply {
            setTitle(R.string.rom_migration_progress_title)
            setMessage(getString(R.string.rom_migration_progress, "", 0, 0))
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = 100
            show()
        }

        lifecycleScope.launch {
            val result = migrationHelper.migrateRomsLegacy(newPath) { current, total, fileName, phase ->
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
                            this@TVFolderPickerLauncher,
                            getString(R.string.rom_migration_success, result.movedCount),
                            Toast.LENGTH_LONG
                        ).show()
                        proceedWithFolderChange(newPath)
                    }
                    result.movedCount > 0 -> {
                        Toast.makeText(
                            this@TVFolderPickerLauncher,
                            getString(R.string.rom_migration_partial, result.movedCount, result.movedCount + result.failedCount),
                            Toast.LENGTH_LONG
                        ).show()
                        proceedWithFolderChange(newPath)
                    }
                    else -> {
                        Toast.makeText(
                            this@TVFolderPickerLauncher,
                            getString(R.string.rom_migration_failed, result.errors.firstOrNull() ?: "Unknown error"),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun proceedWithFolderChange(newPath: String) {
        val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
        val preferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)

        sharedPreferences.edit().apply {
            this.putString(preferenceKey, newPath)
            this.commit()
        }

        startLibraryIndexWork()
        finish()
    }

    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, TVFolderPickerLauncher::class.java))
        }
    }
}
