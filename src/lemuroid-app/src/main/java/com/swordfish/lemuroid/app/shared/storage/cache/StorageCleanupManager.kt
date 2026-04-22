package com.swordfish.lemuroid.app.shared.storage.cache

import android.content.Context
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.lib.storage.local.LocalStorageProvider
import com.swordfish.lemuroid.lib.storage.local.StorageAccessFrameworkProvider
import com.swordfish.lemuroid.lib.storage.smb.SmbStorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageCleanupManager {
    enum class BucketType {
        ROM_CACHE_LOCAL,
        ROM_CACHE_SAF,
        ROM_CACHE_SMB,
        COVER_IMAGES,
    }

    data class Bucket(
        val type: BucketType,
        val sizeBytes: Long,
    )

    fun getBucketLabelRes(type: BucketType): Int =
        when (type) {
            BucketType.ROM_CACHE_LOCAL -> R.string.settings_cleanup_rom_cache_local
            BucketType.ROM_CACHE_SAF -> R.string.settings_cleanup_rom_cache_saf
            BucketType.ROM_CACHE_SMB -> R.string.settings_cleanup_rom_cache_smb
            BucketType.COVER_IMAGES -> R.string.settings_cleanup_cover_images
        }

    fun getBucketPreferenceKeyRes(type: BucketType): Int =
        when (type) {
            BucketType.ROM_CACHE_LOCAL -> R.string.pref_key_clear_rom_cache_local
            BucketType.ROM_CACHE_SAF -> R.string.pref_key_clear_rom_cache_saf
            BucketType.ROM_CACHE_SMB -> R.string.pref_key_clear_rom_cache_smb
            BucketType.COVER_IMAGES -> R.string.pref_key_clear_cover_images_cache
        }

    suspend fun getBuckets(appContext: Context): List<Bucket> =
        withContext(Dispatchers.IO) {
            BucketType.entries.map { Bucket(it, getDirectoryForBucket(appContext, it).computeFolderSize()) }
        }

    suspend fun cleanBucket(
        appContext: Context,
        type: BucketType,
    ): Long = withContext(Dispatchers.IO) {
        val target = getDirectoryForBucket(appContext, type)
        val size = target.computeFolderSize()

        if (target.exists()) {
            target.deleteRecursively()
        }

        // Keep expected folder structure available for next writes.
        target.mkdirs()
        size
    }

    private fun getDirectoryForBucket(
        appContext: Context,
        type: BucketType,
    ): File {
        return when (type) {
            BucketType.ROM_CACHE_LOCAL -> File(appContext.cacheDir, LocalStorageProvider.LOCAL_STORAGE_CACHE_SUBFOLDER)
            BucketType.ROM_CACHE_SAF -> File(appContext.cacheDir, StorageAccessFrameworkProvider.SAF_CACHE_SUBFOLDER)
            BucketType.ROM_CACHE_SMB -> File(appContext.cacheDir, SmbStorageProvider.SMB_CACHE_SUBFOLDER)
            BucketType.COVER_IMAGES -> File(appContext.cacheDir, CoverUtils.IMAGE_CACHE_SUBFOLDER)
        }
    }

    private fun File.computeFolderSize(): Long {
        if (!exists()) return 0L
        return walkBottomUp()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
