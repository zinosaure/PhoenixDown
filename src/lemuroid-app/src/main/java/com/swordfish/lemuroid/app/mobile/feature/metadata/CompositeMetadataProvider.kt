package com.swordfish.lemuroid.app.mobile.feature.metadata

import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import timber.log.Timber

/**
 * Two-phase metadata provider:
 * 1. System detection: Uses primary providers (LibretroDB) to detect system and get basic metadata
 * 2. Enrichment: Uses enrichment providers (TheGamesDB) to fill in missing metadata
 */
class CompositeMetadataProvider(
    private val systemDetectionProviders: List<GameMetadataProvider>,
    private val enrichmentProviders: List<GameMetadataProvider> = emptyList()
) : GameMetadataProvider {

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        // Phase 1: System Detection - Get basic metadata with system identification
        var detectedMetadata: GameMetadata? = null
        
        for (provider in systemDetectionProviders) {
            try {
                val metadata = provider.retrieveMetadata(storageFile)
                if (metadata != null && !metadata.system.isNullOrEmpty()) {
                    Timber.d("System detected by ${provider.javaClass.simpleName} for ${storageFile.name}: ${metadata.system}")
                    detectedMetadata = metadata
                    break
                }
            } catch (e: Exception) {
                Timber.w(e, "Provider ${provider.javaClass.simpleName} failed for ${storageFile.name}")
            }
        }
        
        // Fallback: Try to identify system by unique file extension
        if (detectedMetadata == null) {
            val extension = storageFile.name.substringAfterLast('.', "")
            if (extension.isNotEmpty()) {
                val system = com.swordfish.lemuroid.lib.library.GameSystem.findByUniqueFileExtension(extension)
                if (system != null) {
                    Timber.d("System identified by extension for ${storageFile.name}: ${system.id}")
                    detectedMetadata = GameMetadata(
                        name = storageFile.extensionlessName,
                        description = null,
                        thumbnail = null,
                        system = system.id.dbname,
                        publisher = null,
                        developer = null,
                        genre = null,
                        year = null,
                        romName = storageFile.name
                    )
                }
            }
        }
        
        // Final fallback: Unknown system
        if (detectedMetadata == null) {
            detectedMetadata = GameMetadata(
                name = storageFile.name,
                description = "Unknown System",
                thumbnail = null,
                system = com.swordfish.lemuroid.lib.library.SystemID.UNKNOWN.dbname,
                publisher = null,
                developer = null,
                genre = null,
                year = null,
                romName = storageFile.name
            )
        }
        
        // Phase 2: Enrichment - Fill in missing metadata from scrapers
        val currentMetadata = detectedMetadata
        if (currentMetadata != null && needsEnrichment(currentMetadata)) {
            for (provider in enrichmentProviders) {
                try {
                    val enrichedData = provider.retrieveMetadata(storageFile)
                    if (enrichedData != null) {
                        Timber.d("Enriching metadata for ${storageFile.name} from ${provider.javaClass.simpleName}")
                        detectedMetadata = mergeMetadata(currentMetadata, enrichedData)
                        break // Use first successful enrichment
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Enrichment provider ${provider.javaClass.simpleName} failed for ${storageFile.name}")
                }
            }
        }
        
        return detectedMetadata
    }
    
    /**
     * Check if metadata needs enrichment (missing important fields)
     */
    private fun needsEnrichment(metadata: GameMetadata): Boolean {
        return metadata.thumbnail == null || 
               metadata.description == null || 
               metadata.developer == null
    }
    
    /**
     * Merge enriched data into detected metadata, keeping system and romName from detection
     */
    private fun mergeMetadata(detected: GameMetadata, enriched: GameMetadata): GameMetadata {
        return detected.copy(
            name = enriched.name ?: detected.name,
            description = enriched.description ?: detected.description,
            thumbnail = enriched.thumbnail ?: detected.thumbnail,
            developer = enriched.developer ?: detected.developer,
            publisher = enriched.publisher ?: detected.publisher,
            year = enriched.year ?: detected.year,
            genre = enriched.genre ?: detected.genre
            // Keep detected.system and detected.romName - don't override!
        )
    }
}
