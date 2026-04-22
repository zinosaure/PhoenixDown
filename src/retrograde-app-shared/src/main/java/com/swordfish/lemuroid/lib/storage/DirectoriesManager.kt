package com.swordfish.lemuroid.lib.storage

import android.content.Context
import android.os.Environment
import java.io.File

class DirectoriesManager(private val appContext: Context) {
    private fun documentsRoot(): File =
        appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: appContext.getExternalFilesDir(null)
            ?: appContext.filesDir

    @Deprecated("Use the external states directory")
    fun getInternalStatesDirectory(): File =
        File(appContext.filesDir, "states").apply {
            mkdirs()
        }

    fun getCoresDirectory(): File =
        File(appContext.filesDir, "cores").apply {
            mkdirs()
        }

    fun getSystemDirectory(): File =
        File(appContext.filesDir, "system").apply {
            mkdirs()
        }

    fun getStatesDirectory(): File =
        File(documentsRoot(), "states").apply {
            mkdirs()
        }

    fun getStatesPreviewDirectory(): File =
        File(documentsRoot(), "state-previews").apply {
            mkdirs()
        }

    fun getSavesDirectory(): File =
        File(documentsRoot(), "saves").apply {
            mkdirs()
        }

    fun getInternalRomsDirectory(): File =
        File(appContext.getExternalFilesDir(null), "roms").apply {
            mkdirs()
        }
}
