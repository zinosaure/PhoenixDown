package com.swordfish.lemuroid.app.mobile.feature.catalog

/**
 * Unified interface for ROM files from any source (Local, SMB, WebDAV, SFTP)
 */
interface RomFile {
    val name: String
    val cleanName: String
    val size: Long
    val extension: String
    val system: String?
    val region: String?
    val flag: String
    
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
