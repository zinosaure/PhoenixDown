package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper class to migrate ROMs when the library folder is changed.
 * Only supports local-to-local migration (not SMB).
 * 
 * Uses a safe "copy-first-delete-after" strategy:
 * 1. Copy ALL files to destination first (originals remain intact)
 * 2. Update DB with new URIs
 * 3. Only if everything succeeded, delete originals
 * 
 * This ensures that if the app is killed mid-migration, originals are preserved.
 */
class RomMigrationHelper(
    private val context: Context,
    private val retrogradeDb: RetrogradeDatabase
) {
    companion object {
        private const val TAG = "RomMigrationHelper"
    }

    data class MigrationResult(
        val success: Boolean,
        val movedCount: Int,
        val failedCount: Int,
        val errors: List<String>
    ) {
        companion object {
            fun success(movedCount: Int) = MigrationResult(true, movedCount, 0, emptyList())
            fun partial(movedCount: Int, failedCount: Int, errors: List<String>) = 
                MigrationResult(false, movedCount, failedCount, errors)
            fun failure(errors: List<String>) = MigrationResult(false, 0, 0, errors)
        }
    }

    /**
     * Counts the total number of ROMs in the database.
     */
    suspend fun countExistingRoms(): Int = withContext(Dispatchers.IO) {
        retrogradeDb.gameDao().selectTotalCount()
    }

    /**
     * Checks if the current library is local (not SMB).
     */
    fun isLocalLibrary(): Boolean {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val libraryType = prefs.getString(SharedPreferencesHelper.KEY_LIBRARY_TYPE, "local")
        return libraryType == "local"
    }

    /**
     * Migrates ROMs from current library location to a new SAF destination.
     * Uses safe copy-first-delete-after strategy.
     * 
     * Phase 1: Copy all files to destination (originals preserved)
     * Phase 2: Update DB with new URIs
     * Phase 3: Delete originals (only if Phase 1 & 2 succeeded completely)
     */
    suspend fun migrateRomsSAF(
        destinationUri: Uri,
        onProgress: (current: Int, total: Int, fileName: String, phase: String) -> Unit
    ): MigrationResult = withContext(Dispatchers.IO) {
        if (!isLocalLibrary()) {
            return@withContext MigrationResult.failure(listOf("SMB migration not supported"))
        }

        val games = retrogradeDb.gameDao().selectAllGames()
        if (games.isEmpty()) {
            return@withContext MigrationResult.success(0)
        }

        val destinationDir = DocumentFile.fromTreeUri(context, destinationUri)
        if (destinationDir == null || !destinationDir.canWrite()) {
            return@withContext MigrationResult.failure(listOf("Cannot write to destination folder"))
        }

        val errors = mutableListOf<String>()
        
        // Data structure to track copied files
        data class CopiedFile(
            val game: Game,
            val sourceUri: Uri,
            val destFile: DocumentFile,
            val destUri: Uri
        )
        val copiedFiles = mutableListOf<CopiedFile>()

        // ========== PHASE 1: Copy all files (originals preserved) ==========
        Log.d(TAG, "Phase 1: Copying ${games.size} files to destination")
        
        games.forEachIndexed { index, game ->
            try {
                val sourceUri = Uri.parse(game.fileUri)
                val sourceFile = DocumentFile.fromSingleUri(context, sourceUri)
                
                if (sourceFile == null || !sourceFile.exists()) {
                    Log.w(TAG, "Source file not found: ${game.fileName}")
                    errors.add("File not found: ${game.fileName}")
                    return@forEachIndexed
                }

                onProgress(index + 1, games.size, game.fileName, "Copying")

                // Check if file already exists in destination
                val existingFile = destinationDir.findFile(game.fileName)
                if (existingFile != null) {
                    // File already exists - could be from interrupted previous migration
                    Log.d(TAG, "File already exists in destination: ${game.fileName}")
                    copiedFiles.add(CopiedFile(game, sourceUri, existingFile, existingFile.uri))
                    return@forEachIndexed
                }

                // Create destination file
                val mimeType = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"
                val destFile = destinationDir.createFile(mimeType, game.fileName)
                
                if (destFile == null) {
                    Log.e(TAG, "Could not create destination file: ${game.fileName}")
                    errors.add("Could not create: ${game.fileName}")
                    return@forEachIndexed
                }

                // Copy content
                val copied = copyFile(sourceUri, destFile.uri)
                if (!copied) {
                    destFile.delete()
                    errors.add("Copy failed: ${game.fileName}")
                    return@forEachIndexed
                }

                copiedFiles.add(CopiedFile(game, sourceUri, destFile, destFile.uri))

            } catch (e: Exception) {
                Log.e(TAG, "Error copying ${game.fileName}", e)
                errors.add("Error: ${game.fileName} - ${e.message}")
            }
        }

        // If no files were copied successfully, abort
        if (copiedFiles.isEmpty()) {
            return@withContext MigrationResult.failure(errors)
        }

        // ========== PHASE 2: Update database with new URIs ==========
        Log.d(TAG, "Phase 2: Updating DB for ${copiedFiles.size} games")
        
        try {
            val updatedGames = copiedFiles.map { copied ->
                copied.game.copy(fileUri = copied.destUri.toString())
            }
            retrogradeDb.gameDao().update(updatedGames)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update database", e)
            // Critical error - but files are safely copied, user can rescan
            return@withContext MigrationResult.partial(
                copiedFiles.size, 
                games.size - copiedFiles.size,
                errors + "Database update failed: ${e.message}"
            )
        }

        // ========== PHASE 3: Delete originals (only if all copied successfully) ==========
        val allCopied = copiedFiles.size == games.size && errors.isEmpty()
        
        if (allCopied) {
            Log.d(TAG, "Phase 3: Deleting ${copiedFiles.size} original files")
            
            copiedFiles.forEachIndexed { index, copied ->
                try {
                    onProgress(index + 1, copiedFiles.size, copied.game.fileName, "Cleaning")
                    
                    val sourceFile = DocumentFile.fromSingleUri(context, copied.sourceUri)
                    if (sourceFile?.exists() == true) {
                        val deleted = sourceFile.delete()
                        if (!deleted) {
                            Log.w(TAG, "Could not delete original: ${copied.game.fileName}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting original ${copied.game.fileName}", e)
                    // Non-critical - file is already copied and DB updated
                }
            }
            
            return@withContext MigrationResult.success(copiedFiles.size)
        } else {
            // Partial migration - don't delete originals
            Log.d(TAG, "Partial migration: ${copiedFiles.size} of ${games.size} copied. Not deleting originals.")
            return@withContext MigrationResult.partial(
                copiedFiles.size, 
                games.size - copiedFiles.size, 
                errors
            )
        }
    }

    /**
     * Migrates ROMs using legacy file paths (for TV without SAF).
     * Uses safe copy-first-delete-after strategy.
     */
    suspend fun migrateRomsLegacy(
        destinationPath: String,
        onProgress: (current: Int, total: Int, fileName: String, phase: String) -> Unit
    ): MigrationResult = withContext(Dispatchers.IO) {
        if (!isLocalLibrary()) {
            return@withContext MigrationResult.failure(listOf("SMB migration not supported"))
        }

        val games = retrogradeDb.gameDao().selectAllGames()
        if (games.isEmpty()) {
            return@withContext MigrationResult.success(0)
        }

        val destinationDir = File(destinationPath)
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        if (!destinationDir.canWrite()) {
            return@withContext MigrationResult.failure(listOf("Cannot write to destination folder"))
        }

        val errors = mutableListOf<String>()
        
        data class CopiedFile(
            val game: Game,
            val sourceFile: File?,
            val destFile: File,
            val newUri: String
        )
        val copiedFiles = mutableListOf<CopiedFile>()

        // ========== PHASE 1: Copy all files ==========
        Log.d(TAG, "Phase 1 (Legacy): Copying ${games.size} files to destination")
        
        games.forEachIndexed { index, game ->
            try {
                val sourceUri = Uri.parse(game.fileUri)
                
                val sourceFile = if (sourceUri.scheme == "file") {
                    File(sourceUri.path!!)
                } else {
                    null
                }

                if (sourceFile == null || !sourceFile.exists()) {
                    Log.w(TAG, "Source file not found or not a file URI: ${game.fileName}")
                    errors.add("File not accessible: ${game.fileName}")
                    return@forEachIndexed
                }

                onProgress(index + 1, games.size, game.fileName, "Copying")

                val destFile = File(destinationDir, game.fileName)
                
                // Check if already exists
                if (destFile.exists()) {
                    Log.d(TAG, "File already exists in destination: ${game.fileName}")
                    val newUri = Uri.fromFile(destFile).toString()
                    copiedFiles.add(CopiedFile(game, sourceFile, destFile, newUri))
                    return@forEachIndexed
                }
                
                // Copy file
                sourceFile.copyTo(destFile, overwrite = false)
                
                val newUri = Uri.fromFile(destFile).toString()
                copiedFiles.add(CopiedFile(game, sourceFile, destFile, newUri))

            } catch (e: Exception) {
                Log.e(TAG, "Error copying ${game.fileName}", e)
                errors.add("Error: ${game.fileName} - ${e.message}")
            }
        }

        if (copiedFiles.isEmpty()) {
            return@withContext MigrationResult.failure(errors)
        }

        // ========== PHASE 2: Update database ==========
        Log.d(TAG, "Phase 2 (Legacy): Updating DB for ${copiedFiles.size} games")
        
        try {
            val updatedGames = copiedFiles.map { copied ->
                copied.game.copy(fileUri = copied.newUri)
            }
            retrogradeDb.gameDao().update(updatedGames)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update database", e)
            return@withContext MigrationResult.partial(
                copiedFiles.size, 
                games.size - copiedFiles.size,
                errors + "Database update failed: ${e.message}"
            )
        }

        // ========== PHASE 3: Delete originals ==========
        val allCopied = copiedFiles.size == games.size && errors.isEmpty()
        
        if (allCopied) {
            Log.d(TAG, "Phase 3 (Legacy): Deleting ${copiedFiles.size} original files")
            
            copiedFiles.forEachIndexed { index, copied ->
                try {
                    onProgress(index + 1, copiedFiles.size, copied.game.fileName, "Cleaning")
                    copied.sourceFile?.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting original ${copied.game.fileName}", e)
                }
            }
            
            return@withContext MigrationResult.success(copiedFiles.size)
        } else {
            return@withContext MigrationResult.partial(
                copiedFiles.size, 
                games.size - copiedFiles.size, 
                errors
            )
        }
    }

    private fun copyFile(sourceUri: Uri, destUri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                    true
                } ?: false
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file", e)
            false
        }
    }
}
