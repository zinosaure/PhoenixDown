package com.swordfish.lemuroid.app.mobile.feature.catalog.scanner

import android.util.Log
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomFile
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scanner implementation for SFTP shares (SSH File Transfer Protocol)
 * 
 * Dependency: com.jcraft.jsch or com.sshtools.sshj (respectively lightweight vs full-featured)
 * Library selection: will evaluate jsch (pure Java, established) vs sshj (more active maintenance)
 */
class SftpRomScanner : RomSourceScanner {
    
    companion object {
        private const val TAG = "SftpRomScanner"
        private const val MAX_DEPTH = 10
        private const val DEFAULT_SFTP_PORT = 22
        
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
            Log.w(TAG, "SFTP scanning for '${source.name}' - NOT YET IMPLEMENTED")
            Log.i(TAG, "SFTP support is planned for Phoenix Down 2.0")
            
            // Placeholder for SFTP implementation
            // This will be implemented using jsch or sshj library
            Result.failure(Exception("SFTP scanner not yet implemented"))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SFTP source '${source.name}': ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun testConnection(source: RomSource): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.w(TAG, "SFTP connection test for '${source.name}' - NOT YET IMPLEMENTED")
        Result.failure(Exception("SFTP scanner not yet implemented"))
    }
}

/**
 * Status tracking for SFTP scanner implementation
 * 
 * TODO:
 * 1. Evaluate and add SFTP library dependency (jsch recommended for portability)
 * 2. Implement SSH session management (connect, auth, disconnect)
 * 3. Support password-based authentication
 * 4. Support key-based authentication (private key + passphrase)
 * 5. Implement recursive folder scanning with SFTP protocol
 * 6. Add timeout handling for slow/unreliable connections
 * 7. Handle SSH key exchange and algorithm negotiation
 * 8. Implement connection reuse and pooling
 * 9. Test on various SSH implementations (OpenSSH, PuTTY servers, etc.)
 * 10. Add host key verification options (accept-unknown, known-hosts, etc.)
 */
