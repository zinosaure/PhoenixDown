package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.content.Context
import com.swordfish.lemuroid.lib.storage.source.SourceRepository

/**
 * UI-layer facade over [SourceRepository].
 *
 * Extends SourceRepository so all CRUD operations (getSources, addSource, updateSource,
 * removeSource, upsertByPath) are inherited and work on the same "rom_sources" prefs file
 * that StorageProviders read from.
 *
 * Usage in Composables: `remember { SourceManager(context) }`
 */
class SourceManager(context: Context) : SourceRepository(context)

/**
 * State of a downloadable file in the catalog UI.
 */
enum class DownloadState {
    NOT_DOWNLOADED,
    ALREADY_DOWNLOADED,
    IN_LIBRARY,
}

