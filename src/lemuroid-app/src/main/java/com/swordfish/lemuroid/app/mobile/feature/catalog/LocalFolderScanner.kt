package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scanner for local folders (using SAF - Storage Access Framework)
 */
class LocalFolderScanner(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalFolderScanner"
        private const val MAX_DEPTH = 10 // Recursive search up to 10 levels deep
        
        // ROM file extensions to look for
        private val ROM_EXTENSIONS = setOf(
            "zip", "7z", "rar",
            "nes", "fds", "unf",
            "sfc", "smc", "fig", "swc",
            "gb", "gbc", "gba",
            "md", "bin", "gen", "smd",
            "n64", "z64", "v64",
            "iso", "cue", "chd", "pbp",
            "nds", "dsi",
            "pce", "sgx",
            "gg", "sms", "sg",
            "ws", "wsc",
            "ngp", "ngc",
            "a26", "a78",
            "lnx",
            "vec",
            "col",
            "int"
        )
    }
    
    /**
     * Scan a local folder for ROM files recursively
     */
    suspend fun scanFolder(uri: Uri): Result<List<LocalFile>> = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
                ?: return@withContext Result.failure(Exception("Cannot access folder"))
            
            val files = mutableListOf<LocalFile>()
            scanDirectory(documentFile, files, "", 0)
            
            Log.d(TAG, "Found ${files.size} ROM files in ${documentFile.name}")
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning local folder: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun scanDirectory(
        directory: DocumentFile,
        files: MutableList<LocalFile>,
        parentPath: String,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return
        
        val currentPath = if (parentPath.isEmpty()) {
            directory.name ?: ""
        } else {
            "$parentPath/${directory.name ?: ""}"
        }
        
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDirectory(file, files, currentPath, depth + 1)
            } else {
                val name = file.name ?: return@forEach
                val extension = name.substringAfterLast('.', "").lowercase()
                
                if (extension in ROM_EXTENSIONS) {
                    val fullPath = "$currentPath/$name"
                    
                    // Extract metadata from path and filename
                    val metadata = RomMetadataExtractor.extractMetadata(fullPath, name, extension)
                    
                    files.add(LocalFile(
                        name = name,
                        cleanName = metadata.cleanName,
                        uri = file.uri,
                        size = file.length(),
                        extension = extension,
                        fullPath = fullPath,
                        system = metadata.system,
                        region = metadata.region,
                        flag = metadata.flag
                    ))
                }
            }
        }
    }
    
    /**
     * Copy a local file to the ROMs directory
     */
    suspend fun copyToRomsDir(
        sourceUri: Uri,
        romsDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromSingleUri(context, sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot access file"))
            
            val fileName = documentFile.name ?: "unknown"
            val destFile = File(romsDir, fileName)
            
            romsDir.mkdirs()
            
            val totalSize = documentFile.length()
            
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        onProgress(totalBytesRead, totalSize)
                    }
                }
            }
            
            Result.success(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * Represents a file in a local folder with extracted metadata
 */
data class LocalFile(
    val name: String,
    val cleanName: String,
    val uri: Uri,
    val size: Long,
    val extension: String,
    val fullPath: String,
    val system: String?,
    val region: String?,
    val flag: String
) {
    val sizeFormatted: String
        get() = when {
            size >= 1_000_000_000 -> String.format("%.2f GB", size / 1_000_000_000.0)
            size >= 1_000_000 -> String.format("%.2f MB", size / 1_000_000.0)
            size >= 1_000 -> String.format("%.2f KB", size / 1_000.0)
            else -> "$size B"
        }
    
    val systemDisplay: String
        get() = RomMetadataExtractor.getSystemDisplayName(system)
}
