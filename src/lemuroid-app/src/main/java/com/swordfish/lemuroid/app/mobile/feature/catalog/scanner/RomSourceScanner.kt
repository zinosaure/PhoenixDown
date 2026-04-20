package com.swordfish.lemuroid.app.mobile.feature.catalog.scanner

import android.util.Log
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomFile
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Abstract interface for scanning different ROM source types.
 * Implementations: Local folders, SMB, WebDAV, SFTP
 */
interface RomSourceScanner {
    /**
     * Scan a source and return discovered ROM files
     */
    suspend fun scan(source: RomSource): Result<List<RomFile>>
    
    /**
     * Test connection to a source (useful for network sources)
     */
    suspend fun testConnection(source: RomSource): Result<Boolean>
}

/**
 * Base result wrapper for scan operations
 */
data class ScanResult(
    val sourceId: String,
    val sourceName: String,
    val filesFound: List<RomFile>,
    val totalScans: Int,
    val isSuccess: Boolean,
    val error: Exception? = null
)

/**
 * Orchestrate parallel scanning of multiple ROM sources
 */
class MultiSourceRomScanner(
    private val localScanner: RomSourceScanner,
    private val smbScanner: RomSourceScanner,
    private val webdavScanner: RomSourceScanner? = null,
    private val sftpScanner: RomSourceScanner? = null
) {
    
    companion object {
        private const val TAG = "MultiSourceRomScanner"
    }
    
    /**
     * Scan all sources in parallel and deduplicate results
     */
    suspend fun scanAllSources(sources: List<RomSource>): Result<List<RomFile>> =
        scanAllSourcesWithOrigin(sources).map { files -> files.map { it.second } }

    /**
     * Scan all sources in parallel, keep source origin, and deduplicate results.
     */
    suspend fun scanAllSourcesWithOrigin(sources: List<RomSource>): Result<List<Pair<RomSource, RomFile>>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting parallel scan of ${sources.size} sources")

                val results = scanSourcesInParallel(sources)
                
                // Log per-source results
                results.forEach { result ->
                    if (result.isSuccess) {
                        Log.d(TAG, "Source '${result.sourceName}': ${result.filesFound.size} ROMs found")
                    } else {
                        Log.e(TAG, "Source '${result.sourceName}': ${result.error?.message}")
                    }
                }

                val sourceById = sources.associateBy { it.id }
                val allFilesWithOrigin = results
                    .filter { it.isSuccess }
                    .flatMap { result ->
                        val source = sourceById[result.sourceId] ?: return@flatMap emptyList()
                        result.filesFound.map { file -> source to file }
                    }

                val deduplicatedFiles = deduplicateRomsWithOrigin(allFilesWithOrigin)

                Log.d(TAG, "Scan complete: ${deduplicatedFiles.size} unique ROMs after deduplication")
                Result.success(deduplicatedFiles)

            } catch (e: Exception) {
                Log.e(TAG, "Error during parallel scan: ${e.message}", e)
                Result.failure(e)
            }
        }

    private suspend fun scanSourcesInParallel(sources: List<RomSource>): List<ScanResult> = coroutineScope {
        val jobs = sources.map { source ->
            async {
                scanSourceSafe(source)
            }
        }
        jobs.awaitAll()
    }
    
    /**
     * Scan a single source safely (with error handling)
     */
    private suspend fun scanSourceSafe(source: RomSource): ScanResult {
        val scanner = selectScanner(source)
        
        return try {
            val result = scanner.scan(source)
            
            if (result.isSuccess) {
                ScanResult(
                    sourceId = source.id,
                    sourceName = source.name,
                    filesFound = result.getOrElse { emptyList() },
                    totalScans = 1,
                    isSuccess = true
                )
            } else {
                ScanResult(
                    sourceId = source.id,
                    sourceName = source.name,
                    filesFound = emptyList(),
                    totalScans = 1,
                    isSuccess = false,
                    error = (result.exceptionOrNull() as? Exception) ?: Exception("Unknown error")
                )
            }
        } catch (e: Exception) {
            ScanResult(
                sourceId = source.id,
                sourceName = source.name,
                filesFound = emptyList(),
                totalScans = 1,
                isSuccess = false,
                error = e
            )
        }
    }
    
    /**
     * Select the appropriate scanner based on source type
     */
    private fun selectScanner(source: RomSource): RomSourceScanner {
        return when (source.type) {
            com.swordfish.lemuroid.app.mobile.feature.catalog.SourceType.LOCAL -> localScanner
            com.swordfish.lemuroid.app.mobile.feature.catalog.SourceType.SMB -> smbScanner
            com.swordfish.lemuroid.app.mobile.feature.catalog.SourceType.WEBDAV -> 
                webdavScanner ?: throw IllegalArgumentException("WebDAV scanner not available")
            com.swordfish.lemuroid.app.mobile.feature.catalog.SourceType.SFTP -> 
                sftpScanner ?: throw IllegalArgumentException("SFTP scanner not available")
            com.swordfish.lemuroid.app.mobile.feature.catalog.SourceType.ARCHIVE_ORG -> 
                localScanner // Placeholder
        }
    }
    
    /**
     * Deduplicate ROMs by clean name and system.
     * Keep the first occurrence and mark alternatives (useful for display).
     */
    private fun deduplicateRomsWithOrigin(files: List<Pair<RomSource, RomFile>>): List<Pair<RomSource, RomFile>> {
        val seen = mutableSetOf<String>()
        val deduplicated = mutableListOf<Pair<RomSource, RomFile>>()
        
        files.forEach { sourceAndFile ->
            val file = sourceAndFile.second
            val key = "${file.system}::${file.cleanName}".lowercase()
            if (key !in seen) {
                seen.add(key)
                deduplicated.add(sourceAndFile)
            }
        }
        
        Log.d(TAG, "Deduplication: ${files.size} files -> ${deduplicated.size} unique")
        return deduplicated
    }
}
