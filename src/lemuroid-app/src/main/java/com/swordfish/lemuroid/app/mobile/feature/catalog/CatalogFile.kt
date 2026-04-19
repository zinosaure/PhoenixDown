package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.net.Uri

/**
 * Unified file representation for displaying files from any source in the catalog
 */
data class CatalogFile(
    val name: String,
    val source: SourceType,
    val sourceName: String,
    val size: Long,
    val extension: String,
    val downloadState: DownloadState,
    
    // Source-specific data
    val archiveOrgPack: ArchiveOrgClient.RomPack? = null,
    val archiveOrgFile: ArchiveOrgClient.DownloadableFile? = null,
    val localUri: Uri? = null,
    val smbPath: String? = null,
    val smbSource: RomSource? = null
) {
    val sizeFormatted: String
        get() = when {
            size >= 1_000_000_000 -> String.format("%.2f GB", size / 1_000_000_000.0)
            size >= 1_000_000 -> String.format("%.2f MB", size / 1_000_000.0)
            size >= 1_000 -> String.format("%.2f KB", size / 1_000.0)
            else -> "$size B"
        }
    
    companion object {
        fun fromArchiveOrg(
            pack: ArchiveOrgClient.RomPack,
            file: ArchiveOrgClient.DownloadableFile,
            downloadState: DownloadState
        ) = CatalogFile(
            name = file.name,
            source = SourceType.ARCHIVE_ORG,
            sourceName = "Archive.org",
            size = file.size,
            extension = file.name.substringAfterLast('.', "").lowercase(),
            downloadState = downloadState,
            archiveOrgPack = pack,
            archiveOrgFile = file
        )
        
        fun fromLocal(
            file: LocalFile,
            sourceName: String,
            downloadState: DownloadState
        ) = CatalogFile(
            name = file.name,
            source = SourceType.LOCAL,
            sourceName = sourceName,
            size = file.size,
            extension = file.extension,
            downloadState = downloadState,
            localUri = file.uri
        )
        
        fun fromSmb(
            file: SmbFile,
            source: RomSource,
            downloadState: DownloadState
        ) = CatalogFile(
            name = file.name,
            source = SourceType.SMB,
            sourceName = source.name,
            size = file.size,
            extension = file.extension,
            downloadState = downloadState,
            smbPath = file.path,
            smbSource = source
        )
    }
}
