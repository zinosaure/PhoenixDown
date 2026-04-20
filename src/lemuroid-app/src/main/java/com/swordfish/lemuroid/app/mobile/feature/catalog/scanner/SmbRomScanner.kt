package com.swordfish.lemuroid.app.mobile.feature.catalog.scanner

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomFile
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomMetadataExtractor
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomSource
import com.swordfish.lemuroid.app.mobile.feature.catalog.SmbCredentials
import com.swordfish.lemuroid.app.mobile.feature.catalog.SmbFile
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumSet

/**
 * Scanner implementation for SMB/NAS shares
 */
class SmbRomScanner : RomSourceScanner {
    
    companion object {
        private const val TAG = "SmbRomScanner"
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
    
    private data class Endpoint(
        val host: String,
        val port: Int,
    )

    private fun parseEndpoint(server: String): Endpoint {
        val value = server.trim()
        val idx = value.lastIndexOf(':')
        if (idx <= 0 || idx == value.lastIndex) {
            return Endpoint(value, 445)
        }

        val host = value.substring(0, idx)
        val port = value.substring(idx + 1).toIntOrNull()
        return if (port != null && port in 1..65535) {
            Endpoint(host, port)
        } else {
            Endpoint(value, 445)
        }
    }
    
    override suspend fun scan(source: RomSource): Result<List<RomFile>> = withContext(Dispatchers.IO) {
        try {
            // Parse SMB URL: smb://server/share/path
            val smbUrl = source.path
            if (!smbUrl.startsWith("smb://")) {
                return@withContext Result.failure(Exception("Invalid SMB URL: $smbUrl"))
            }
            
            val urlWithoutProtocol = smbUrl.removePrefix("smb://")
            val parts = urlWithoutProtocol.split("/", limit = 2)
            val server = parts[0]
            val share = if (parts.size > 1) parts[1].split("/")[0] else ""
            val path = if (parts.size > 1 && parts[1].contains("/")) {
                "/" + parts[1].substringAfter("/").replace("/", "\\")
            } else {
                ""
            }
            
            Log.d(TAG, "Scanning SMB source '${source.name}': $server/$share$path")
            
            val endpoint = parseEndpoint(server)
            val client = SMBClient()
            val connection = client.connect(endpoint.host, endpoint.port)
            
            val authContext = if (source.credentials != null && source.credentials.username.isNotBlank()) {
                AuthenticationContext(source.credentials.username, source.credentials.password.toCharArray(), "")
            } else {
                AuthenticationContext.guest()
            }
            
            val session = connection.authenticate(authContext)
            val diskShare = session.connectShare(share) as DiskShare
            
            val files = mutableListOf<RomFile>()
            
            try {
                // Start scanning from the given path with full recursion
                scanDirectory(diskShare, path, files, "", 0, source)
                Log.d(TAG, "Scanned SMB source '${source.name}': found ${files.size} ROM files")
                Result.success(files)
            } finally {
                diskShare.close()
                session.close()
                connection.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SMB source '${source.name}': ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun testConnection(source: RomSource): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val smbUrl = source.path
            if (!smbUrl.startsWith("smb://")) {
                return@withContext Result.failure(Exception("Invalid SMB URL"))
            }
            
            val urlWithoutProtocol = smbUrl.removePrefix("smb://")
            val parts = urlWithoutProtocol.split("/", limit = 2)
            val server = parts[0]
            val share = if (parts.size > 1) parts[1].split("/")[0] else ""
            
            val endpoint = parseEndpoint(server)
            val client = SMBClient()
            val connection = client.connect(endpoint.host, endpoint.port)
            
            val authContext = if (source.credentials != null && source.credentials.username.isNotBlank()) {
                AuthenticationContext(source.credentials.username, source.credentials.password.toCharArray(), "")
            } else {
                AuthenticationContext.guest()
            }
            
            val session = connection.authenticate(authContext)
            val diskShare = session.connectShare(share) as DiskShare
            
            diskShare.close()
            session.close()
            connection.close()
            
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "SMB connection test failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    private fun scanDirectory(
        diskShare: DiskShare,
        path: String,
        files: MutableList<RomFile>,
        relativePath: String,
        depth: Int,
        source: RomSource
    ) {
        if (depth > MAX_DEPTH) return
        
        try {
            val entries = diskShare.list(path)
            
            entries.forEach { entry ->
                val name = entry.fileName
                if (name == "." || name == "..") return@forEach
                
                val isDirectory = entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                val newPath = if (path.isEmpty()) name else "$path\\$name"
                val newRelativePath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                
                if (isDirectory) {
                    scanDirectory(diskShare, newPath, files, newRelativePath, depth + 1, source)
                } else {
                    val extension = name.substringAfterLast('.', "").lowercase()
                    
                    if (extension in ROM_EXTENSIONS) {
                        val metadata = RomMetadataExtractor.extractMetadata(newRelativePath, name, extension)
                        
                        files.add(SmbFile(
                            name = name,
                            cleanName = metadata.cleanName,
                            path = newPath,
                            relativePath = newRelativePath,
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
            Log.w(TAG, "Error scanning SMB directory '$path': ${e.message}")
        }
    }
}
