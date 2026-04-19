package com.swordfish.lemuroid.lib.storage.smb

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
import java.io.OutputStream
import java.util.EnumSet

/**
 * Client for connecting to SMB/NAS shares and listing/downloading ROMs.
 * Shared between library scanning and catalog downloads.
 */
class SmbClient {
    
    companion object {
        private const val TAG = "SmbClient"
        private const val MAX_DEPTH = 10 // Recursive search up to 10 levels deep
        
        // ROM file extensions to look for
        val ROM_EXTENSIONS = setOf(
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
     * List all files in an SMB share recursively (raw listing, no metadata extraction)
     */
    suspend fun listFilesRaw(
        server: String,
        share: String,
        path: String = "",
        credentials: SmbCredentials? = null
    ): Result<List<SmbFileInfo>> = withContext(Dispatchers.IO) {
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
            val files = mutableListOf<SmbFileInfo>()
            
            // Start scanning from the given path with full recursion
            scanDirectoryRaw(diskShare, smbPath, files, "", 0)
            
            diskShare.close()
            session.close()
            connection.close()
            client.close()
            
            Log.d(TAG, "Found ${files.size} files")
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing SMB files: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun scanDirectoryRaw(
        diskShare: DiskShare,
        path: String,
        files: MutableList<SmbFileInfo>,
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
                    scanDirectoryRaw(diskShare, fullSmbPath, files, currentRelativePath, depth + 1)
                } else {
                    // Check if it's a ROM file
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension in ROM_EXTENSIONS) {
                        files.add(SmbFileInfo(
                            name = name,
                            path = fullSmbPath,
                            relativePath = currentRelativePath,
                            size = entry.endOfFile,
                            extension = extension
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning directory '$path': ${e.message}")
        }
    }
    
    /**
     * Download a file from SMB to an output stream
     */
    suspend fun downloadFile(
        server: String,
        share: String,
        remotePath: String,
        outputStream: OutputStream,
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
     * Upload a file from an input stream to SMB
     */
    suspend fun uploadFile(
        server: String,
        share: String,
        remotePath: String,
        inputStream: java.io.InputStream,
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
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.noneOf(SMB2CreateOptions::class.java)
            )
            
            smbFile.outputStream.use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesWritten = 0L
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead
                    onProgress(totalBytesWritten, -1) // Total unknown from input stream
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
    
    /**
     * Get input stream for a file on SMB (caller must close connection after use)
     */
    suspend fun getInputStream(
        server: String,
        share: String,
        remotePath: String,
        credentials: SmbCredentials? = null
    ): Result<SmbInputStream> = withContext(Dispatchers.IO) {
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
            
            Result.success(SmbInputStream(
                inputStream = smbFile.inputStream,
                smbFile = smbFile,
                diskShare = diskShare,
                session = session,
                connection = connection,
                client = client
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SMB input stream: ${e.message}", e)
            Result.failure(e)
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
    
    /**
     * Move/rename a file on SMB share
     */
    suspend fun moveFile(
        server: String,
        share: String,
        sourcePath: String,
        destPath: String,
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
            
            val smbSourcePath = sourcePath.replace("/", "\\")
            val smbDestPath = destPath.replace("/", "\\")
            
            // Create destination directory if it doesn't exist
            val destDir = smbDestPath.substringBeforeLast("\\", "")
            if (destDir.isNotEmpty() && !diskShare.folderExists(destDir)) {
                createDirectoryRecursive(diskShare, destDir)
            }
            
            // Open source file with DELETE access for rename
            val smbFile = diskShare.openFile(
                smbSourcePath,
                EnumSet.of(AccessMask.GENERIC_READ, AccessMask.DELETE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_DELETE),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.noneOf(SMB2CreateOptions::class.java)
            )
            
            // Rename to destination
            smbFile.rename(smbDestPath, true)
            
            smbFile.close()
            diskShare.close()
            session.close()
            connection.close()
            client.close()
            
            Log.d(TAG, "Moved file from $smbSourcePath to $smbDestPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error moving SMB file: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a file from SMB share
     */
    suspend fun deleteFile(
        server: String,
        share: String,
        remotePath: String,
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
            
            Log.i(TAG, "Deleting SMB file: $smbPath")
            
            // Check if file exists first
            if (diskShare.fileExists(smbPath)) {
                diskShare.rm(smbPath)
                Log.d(TAG, "File deleted successfully")
            } else {
                Log.w(TAG, "File not found for deletion: $smbPath")
            }
            
            diskShare.close()
            session.close()
            connection.close()
            client.close()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting SMB file: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create a directory on SMB share (recursive)
     */
    private fun createDirectoryRecursive(diskShare: DiskShare, path: String) {
        val parts = path.split("\\")
        var currentPath = ""
        
        for (part in parts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath\\$part"
            try {
                if (!diskShare.folderExists(currentPath)) {
                    diskShare.mkdir(currentPath)
                    Log.d(TAG, "Created directory: $currentPath")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error creating directory '$currentPath': ${e.message}")
            }
        }
    }
}

/**
 * Basic file info from SMB without metadata extraction
 */
data class SmbFileInfo(
    val name: String,
    val path: String,
    val relativePath: String,
    val size: Long,
    val extension: String
) {
    val sizeFormatted: String
        get() = when {
            size >= 1_000_000_000 -> String.format("%.2f GB", size / 1_000_000_000.0)
            size >= 1_000_000 -> String.format("%.2f MB", size / 1_000_000.0)
            size >= 1_000 -> String.format("%.2f KB", size / 1_000.0)
            else -> "$size B"
        }
}

/**
 * SMB Credentials
 */
data class SmbCredentials(
    val username: String,
    val password: String
)

/**
 * Wrapper for SMB input stream that manages connection lifecycle
 */
class SmbInputStream(
    val inputStream: java.io.InputStream,
    private val smbFile: com.hierynomus.smbj.share.File,
    private val diskShare: DiskShare,
    private val session: com.hierynomus.smbj.session.Session,
    private val connection: com.hierynomus.smbj.connection.Connection,
    private val client: SMBClient
) : java.io.Closeable {
    override fun close() {
        try {
            inputStream.close()
            smbFile.close()
            diskShare.close()
            session.close()
            connection.close()
            client.close()
        } catch (e: Exception) {
            Log.w("SmbInputStream", "Error closing SMB connections: ${e.message}")
        }
    }
}
