package com.swordfish.lemuroid.lib.library.metadata

data class GameMetadata(
    val name: String?,
    val system: String?,
    val romName: String?,
    val developer: String?,
    val thumbnail: String?,
    // Extended metadata
    val year: Int? = null,
    val genre: String? = null,
    val description: String? = null,
    val publisher: String? = null,
)
