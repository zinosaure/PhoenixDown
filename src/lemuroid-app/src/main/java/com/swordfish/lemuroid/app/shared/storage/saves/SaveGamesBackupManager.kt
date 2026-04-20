package com.swordfish.lemuroid.app.shared.storage.saves

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SaveGamesBackupManager {
    data class ExportResult(
        val filesCount: Int,
        val totalBytes: Long,
    )

    data class ImportResult(
        val filesCount: Int,
        val totalBytes: Long,
    )

    suspend fun exportToZip(
        appContext: Context,
        destinationUri: Uri,
    ): ExportResult = withContext(Dispatchers.IO) {
        val directoriesManager = DirectoriesManager(appContext)
        val payload = listOf(
            "saves" to directoriesManager.getSavesDirectory(),
            "states" to directoriesManager.getStatesDirectory(),
            "state-previews" to directoriesManager.getStatesPreviewDirectory(),
        )

        var filesCount = 0
        var totalBytes = 0L

        appContext.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
            ZipOutputStream(output).use { zipOutput ->
                payload.forEach { (entryRoot, directory) ->
                    if (!directory.exists()) return@forEach

                    directory.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val relative = file.relativeTo(directory).invariantSeparatorsPath
                            val entryName = "$entryRoot/$relative"
                            ZipEntry(entryName).also { zipOutput.putNextEntry(it) }
                            file.inputStream().use { input ->
                                totalBytes += input.copyTo(zipOutput)
                            }
                            zipOutput.closeEntry()
                            filesCount += 1
                        }
                }
            }
        } ?: throw IllegalStateException("Cannot open output stream for backup export")

        ExportResult(filesCount, totalBytes)
    }

    suspend fun importFromZip(
        appContext: Context,
        sourceUri: Uri,
    ): ImportResult = withContext(Dispatchers.IO) {
        val directoriesManager = DirectoriesManager(appContext)
        val targets = mapOf(
            "saves" to directoriesManager.getSavesDirectory(),
            "states" to directoriesManager.getStatesDirectory(),
            "state-previews" to directoriesManager.getStatesPreviewDirectory(),
        )

        var filesCount = 0
        var totalBytes = 0L

        appContext.contentResolver.openInputStream(sourceUri)?.use { stream ->
            ZipInputStream(stream).use { zipInput ->
                while (true) {
                    val entry = zipInput.nextEntry ?: break
                    if (entry.isDirectory) {
                        zipInput.closeEntry()
                        continue
                    }

                    val normalized = entry.name.replace('\\', '/')
                    val root = normalized.substringBefore('/', "")
                    val destinationRoot = targets[root]

                    if (destinationRoot == null) {
                        zipInput.closeEntry()
                        continue
                    }

                    val relativePath = normalized.removePrefix("$root/")
                    if (relativePath.isBlank()) {
                        zipInput.closeEntry()
                        continue
                    }

                    val outFile = File(destinationRoot, relativePath)
                    val safe = isChildOf(outFile, destinationRoot)
                    if (!safe) {
                        zipInput.closeEntry()
                        continue
                    }

                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output: OutputStream ->
                        totalBytes += zipInput.copyTo(output)
                    }
                    filesCount += 1
                    zipInput.closeEntry()
                }
            }
        } ?: throw IllegalStateException("Cannot open input stream for backup import")

        ImportResult(filesCount, totalBytes)
    }

    private fun isChildOf(
        child: File,
        parent: File,
    ): Boolean {
        val parentPath = parent.canonicalFile.toPath()
        val childPath = child.canonicalFile.toPath()
        return childPath.startsWith(parentPath)
    }
}
