package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.EnumSet

/**
 * Client for connecting to SMB/NAS shares and listing/downloading ROMs
 */
class SmbClient {
    
    companion object {
        private const val TAG = "SmbClient"
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
     * List ROM files in an SMB share recursively
     */
    suspend fun listFiles(
        server: String,
        share: String,
        path: String = "",
        credentials: SmbCredentials? = null
    ): Result<List<SmbFile>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to SMB: server=$server, share=$share, path=$path")
            
            val client = SMBClient()
            val connection = client.connect(server)
            
            val authContext = if (credentials != null && credentials.username.isNotBlank()) {
                AuthenticationContext(credentials.username, credentials.password.toCharArray(), "")
            } else {
                AuthenticationContext.guest()
            }
            
            val session = connection.authenticate(authContext)
            val diskShare = session.connectShare(share) as DiskShare
            
            val smbPath = path.removePrefix("/").replace("/", "\\")
            val files = mutableListOf<SmbFile>()
            
            // Start scanning from the given path with full recursion
            scanDirectory(diskShare, smbPath, files, "", 0)
            
            diskShare.close()
            session.close()
            connection.close()
            client.close()
            
            Log.d(TAG, "Found ${files.size} ROM files")
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing SMB files: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun scanDirectory(
        diskShare: DiskShare,
        path: String,
        files: MutableList<SmbFile>,
        relativePath: String,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return
        
        try {
            val directoryPath = if (path.isEmpty()) "" else path
            Log.d(TAG, "Scanning directory: '$directoryPath' (depth $depth)")
            
            val entries = diskShare.list(directoryPath)
            
            for (entry in entries) {
                val name = entry.fileName
                if (name == "." || name == "..") continue
                
                val fullSmbPath = if (directoryPath.isEmpty()) name else "$directoryPath\\$name"
                val currentRelativePath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                val isDirectory = entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                
                if (isDirectory) {
                    // Recurse into subdirectory
                    scanDirectory(diskShare, fullSmbPath, files, currentRelativePath, depth + 1)
                } else {
                    // Check if it's a ROM file
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension in ROM_EXTENSIONS) {
                        // Extract metadata from path and filename
                        val metadata = RomMetadataExtractor.extractMetadata(currentRelativePath, name, extension)
                        
                        files.add(SmbFile(
                            name = name,
                            cleanName = metadata.cleanName,
                            path = fullSmbPath,
                            relativePath = currentRelativePath,
                            size = entry.endOfFile,
                            extension = extension,
                            system = metadata.system,
                            region = metadata.region,
                            flag = metadata.flag
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning directory '$path': ${e.message}")
        }
    }
    
    /**
     * Download a file from SMB to local storage
     */
    /**
     * Download a file from SMB to an output stream
     */
    suspend fun downloadFile(
        server: String,
        share: String,
        remotePath: String,
        outputStream: java.io.OutputStream,
        credentials: SmbCredentials? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = SMBClient()
            val connection = client.connect(server)
            
            val authContext = if (credentials != null && credentials.username.isNotBlank()) {
                AuthenticationContext(credentials.username, credentials.password.toCharArray(), "")
            } else {
                AuthenticationContext.guest()
            }
            
            val session = connection.authenticate(authContext)
            val diskShare = session.connectShare(share) as DiskShare
            
            val smbPath = remotePath.replace("/", "\\")
            
            val smbFile = diskShare.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.noneOf(SMB2CreateOptions::class.java)
            )
            
            val fileInfo = smbFile.fileInformation
            val totalSize = fileInfo.standardInformation.endOfFile
            
            smbFile.inputStream.use { input ->
                // Use the provided output stream (caller closes it if needed, or we just write to it)
                // Actually, usually caller using 'use' handles close. We just write.
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    onProgress(totalBytesRead, totalSize)
                }
            }
            
            smbFile.close()
            diskShare.close()
            session.close()
            connection.close()
            client.close()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading SMB file: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a file to SMB share
     */
    suspend fun uploadFile(
        server: String,
        share: String,
        remotePath: String,
        inputStream: java.io.InputStream,
        credentials: SmbCredentials? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = SMBClient()
            val connection = client.connect(server)
            
            val authContext = if (credentials != null && credentials.username.isNotBlank()) {
                AuthenticationContext(credentials.username, credentials.password.toCharArray(), "")
            } else {
                AuthenticationContext.guest()
            }
            
            val session = connection.authenticate(authContext)
            val diskShare = session.connectShare(share) as DiskShare
            
            val smbPath = remotePath.replace("/", "\\")
            
            // Ensure parent directory exists
            val parentPath = smbPath.substringBeforeLast("\\", "")
            if (parentPath.isNotEmpty()) {
                mkdirs(diskShare, parentPath)
            }
            
            val smbFile = diskShare.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                EnumSet.noneOf(SMB2ShareAccess::class.java),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.noneOf(SMB2CreateOptions::class.java)
            )
            
            smbFile.outputStream.use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
            
            smbFile.close()
            diskShare.close()
            session.close()
            connection.close()
            client.close()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading SMB file: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun mkdirs(diskShare: DiskShare, path: String) {
        if (path.isEmpty()) return
        if (diskShare.folderExists(path)) return
        
        val parent = path.substringBeforeLast("\\", "")
        if (parent.isNotEmpty() && parent != path) {
            mkdirs(diskShare, parent)
        }
        
        try {
            if (!diskShare.folderExists(path)) {
                diskShare.mkdir(path)
            }
        } catch (e: Exception) {
            // Ignore if already exists race condition
        }
    }

    /**
     * Test connection to an SMB share
     */
    suspend fun testConnection(
        server: String,
        share: String,
        credentials: SmbCredentials? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val client = SMBClient()
            val connection = client.connect(server)
            
            val authContext = if (credentials != null && credentials.username.isNotBlank()) {
                AuthenticationContext(credentials.username, credentials.password.toCharArray(), "")
            } else {
                AuthenticationContext.guest()
            }
            
            val session = connection.authenticate(authContext)
            val diskShare = session.connectShare(share) as DiskShare
            
            // Try to list root to verify access
            diskShare.list("")
            
            diskShare.close()
            session.close()
            connection.close()
            client.close()
            
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "SMB connection test failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * Represents a file on an SMB share with extracted metadata
 */
data class SmbFile(
    val name: String,
    val cleanName: String,
    val path: String,
    val relativePath: String,
    val size: Long,
    val extension: String,
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
