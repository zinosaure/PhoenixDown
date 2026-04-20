package com.swordfish.lemuroid.app.mobile.feature.catalog.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.app.mobile.feature.catalog.LocalFile
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomFile
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomMetadataExtractor
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scanner implementation for local folders (SAF - Storage Access Framework)
 */
class LocalFolderRomScanner(private val context: Context) : RomSourceScanner {
    
    companion object {
        private const val TAG = "LocalFolderRomScanner"
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
    
    override suspend fun scan(source: RomSource): Result<List<RomFile>> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(source.path)
            val documentFile = DocumentFile.fromTreeUri(context, uri)
                ?: return@withContext Result.failure(Exception("Cannot access folder: ${source.path}"))
            
            val files = mutableListOf<RomFile>()
            scanDirectory(documentFile, files, "", 0)
            
            Log.d(TAG, "Scanned source '${source.name}': found ${files.size} ROM files")
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning local folder '${source.name}': ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun testConnection(source: RomSource): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(source.path)
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            Result.success(documentFile != null && documentFile.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun scanDirectory(
        directory: DocumentFile,
        files: MutableList<RomFile>,
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
}
