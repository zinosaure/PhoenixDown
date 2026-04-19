package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.common.kotlin.calculateCrc32
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resultado de verificación de espacio de almacenamiento
 */
data class StorageCheckResult(
    val hasEnoughSpace: Boolean,
    val requiredBytes: Long,
    val availableBytes: Long,
    val requiredFormatted: String,
    val availableFormatted: String,
    val shortageBytes: Long = 0,
    val shortageFormatted: String = ""
)

/**
 * RomDownloader - Descarga múltiples ROMs simultáneamente desde Archive.org
 */
class RomDownloader(
    private val context: Context,
    private val gameMetadataProvider: GameMetadataProvider
) {
    // DirectoriesManager para fallback a almacenamiento local
    private val directoriesManager = com.swordfish.lemuroid.lib.storage.DirectoriesManager(context)

    companion object {
        private const val TAG = "Catalog.RomDownloader"
        private const val CHANNEL_ID = "rom_downloads"
        private const val NOTIFICATION_ID = 1001
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Estado de todas las descargas activas
    private val _downloads = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadInfo>> = _downloads

    // V8.3: Internal Scope (Singleton Stability)
    // Uses SupervisorJob so one failure doesn't cancel others.
    // Independent of UI lifecycle.
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Jobs de descargas activas para poder cancelarlas
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    data class DownloadInfo(
        val id: String,
        val fileName: String,
        val gameTitle: String,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val status: DownloadStatus,
        val error: String? = null,
        val filePath: String? = null
    ) {
        val progressText: String get() = when(status) {
            DownloadStatus.PENDING -> "En cola..."
            DownloadStatus.DOWNLOADING -> "$progress%"
            DownloadStatus.PROCESSING -> "Analyzing..."
            DownloadStatus.COMPLETED -> "✓ Completado"
            DownloadStatus.ERROR -> "✗ Error"
            DownloadStatus.CANCELLED -> "Cancelado"
        }
        
        val sizeText: String get() {
            val downloaded = formatBytes(downloadedBytes)
            val total = formatBytes(totalBytes)
            return "$downloaded / $total"
        }
        
        private fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    enum class DownloadStatus {
        PENDING, DOWNLOADING, PROCESSING, COMPLETED, ERROR, CANCELLED
    }

    init {
        createNotificationChannel()
    }

    /**
     * Verifica si el modo SAF está disponible y configurado.
     * Usa TVHelper para detectar soporte y verifica que hay URI configurada.
     */
    fun isSAFMode(): Boolean {
        // Verificar si el dispositivo soporta SAF
        if (!com.swordfish.lemuroid.app.tv.shared.TVHelper.isSAFSupported(context)) {
            return false
        }
        // Verificar si hay URI SAF configurada y válida
        return tryGetRootDocumentFile() != null
    }

    /**
     * Obtiene el directorio ROOT configurado mediante SAF (DocumentFile).
     * Retorna null si SAF no está configurado o no tiene permisos.
     */
    fun tryGetRootDocumentFile(): androidx.documentfile.provider.DocumentFile? {
        val uriString = com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.getSAFUri(context)
            ?: return null

        return try {
            val uri = android.net.Uri.parse(uriString)
            val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            if (docFile != null && docFile.canWrite()) docFile else null
        } catch (e: Exception) {
            Log.w(TAG, "SAF root not accessible: $e")
            null
        }
    }

    /**
     * Obtiene el directorio de ROMs local (fallback cuando SAF no disponible).
     * Usa la carpeta configurada por TVFolderPicker o fallback al directorio interno.
     */
    fun getLocalRomsDirectory(): java.io.File {
        // 0. Check Harmony (Unified) - Priority
        val harmonyPrefs = com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.getSharedPreferences(context)
        val harmonyPath = harmonyPrefs.getString(com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper.KEY_TV_CUSTOM_ROMS_PATH, null)
        if (harmonyPath != null) {
            val harmonyDir = java.io.File(harmonyPath)
            if (harmonyDir.exists() && harmonyDir.canWrite()) {
                 return harmonyDir
            }
        }

        // 1. Legacy Fallback
        val legacyPrefKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)
        val legacyPath = com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
            .getLegacySharedPreferences(context)
            .getString(legacyPrefKey, null)
        
        if (legacyPath != null) {
            val legacyDir = java.io.File(legacyPath)
            if (legacyDir.exists() && legacyDir.canWrite()) {
                return legacyDir
            }
        }
        
        // Fallback al directorio interno
        return directoriesManager.getInternalRomsDirectory()
    }

    // --- Helper methods for SAF navigation ---

    private fun findOrCreateDir(parent: androidx.documentfile.provider.DocumentFile, name: String): androidx.documentfile.provider.DocumentFile? {
        return parent.findFile(name) ?: parent.createDirectory(name)
    }

    private fun findFile(parent: androidx.documentfile.provider.DocumentFile, name: String): androidx.documentfile.provider.DocumentFile? {
        return parent.findFile(name)
    }

    /**
     * Crea un archivo en el directorio de ROMs (SAF o local según disponibilidad).
     * @return Pair<Uri, Boolean> donde Uri es la ubicación final y Boolean indica si es modo SAF
     */
    fun createRomsFile(fileName: String, systemId: String?): Pair<Uri, Boolean> {
        val safRoot = tryGetRootDocumentFile()
        
        return if (safRoot != null && isSAFMode()) {
            // Modo SAF
            val parentDir = if (!systemId.isNullOrBlank()) {
                findOrCreateDir(safRoot, systemId) ?: throw IllegalStateException("Cannot create dir $systemId")
            } else {
                safRoot
            }
            
            // Delete if exists to overwrite
            val existing = parentDir.findFile(fileName)
            if (existing != null && existing.exists()) {
                existing.delete()
            }
            
            val docFile = parentDir.createFile("application/octet-stream", fileName)
                ?: throw IllegalStateException("Cannot create SAF file $fileName")
            Pair(docFile.uri, true)
        } else {
            // Modo Local (fallback)
            val baseDir = getLocalRomsDirectory()
            val parentDir = if (!systemId.isNullOrBlank()) {
                java.io.File(baseDir, systemId).apply { mkdirs() }
            } else {
                baseDir
            }
            val localFile = java.io.File(parentDir, fileName)
            if (localFile.exists()) {
                localFile.delete()
            }
            Pair(Uri.fromFile(localFile), false)
        }
    }

    /**
     * Verifica si un archivo ya está descargado (SAF o local).
     */
    fun isFileDownloaded(systemId: String, fileName: String): Boolean {
        // V8.5 FIX: Check SMB cache instead of returning false
        if (libraryDestination != null) {
            val key = "${systemId}/${fileName}".lowercase()
            val result = synchronized(smbLibraryCache) {
                val found = smbLibraryCache.contains(key)
                Log.d(TAG, "V8.5 isFileDownloaded: key='$key', cacheSize=${smbLibraryCache.size}, cacheLoaded=$smbLibraryCacheLoaded, found=$found")
                found
            }
            return result
        }

        return try {
            val safRoot = tryGetRootDocumentFile()
            if (safRoot != null && isSAFMode()) {
                // Modo SAF (Mobile fallback)
                val systemDir = safRoot.findFile(systemId) ?: return false
                val file = systemDir.findFile(fileName)
                file != null && file.exists() && file.length() > 0
            } else {
                // Modo Local (Legacy fallback)
                val localFile = java.io.File(getLocalRomsDirectory(), "$systemId/$fileName")
                localFile.exists() && localFile.length() > 0
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun isFileInRomsDir(fileName: String): Boolean {
        // V7 FIX (STRUCTURAL): HIERARCHY OF COMMAND
        // If SMB Library is explicitly configured, we obey it and IGNORE local storage.
        if (libraryDestination != null) {
            return false
        }
        return try {
            val safRoot = tryGetRootDocumentFile()
            if (safRoot != null && isSAFMode()) {
                val file = safRoot.findFile(fileName)
                file != null && file.exists() && file.length() > 0
            } else {
                val localFile = java.io.File(getLocalRomsDirectory(), fileName)
                localFile.exists() && localFile.length() > 0
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getDownloadedFilePath(systemId: String, fileName: String): String? {
        return try {
            val safRoot = tryGetRootDocumentFile()
            if (safRoot != null && isSAFMode()) {
                val systemDir = safRoot.findFile(systemId) ?: return null
                val file = systemDir.findFile(fileName)
                if (file != null && file.exists() && file.length() > 0) {
                    file.uri.toString()
                } else null
            } else {
                val localFile = java.io.File(getLocalRomsDirectory(), "$systemId/$fileName")
                if (localFile.exists() && localFile.length() > 0) {
                    Uri.fromFile(localFile).toString()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formatea bytes a string legible (KB, MB, GB)
     */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    /**
     * Obtiene el espacio disponible en el destino configurado (SAF, local o SMB)
     * Para SMB retorna -1 (no verificable directamente)
     */
    fun getAvailableSpace(): Long {
        // SMB no es verificable directamente
        if (libraryDestination != null) {
            return -1L
        }
        
        return try {
            val safRoot = tryGetRootDocumentFile()
            if (safRoot != null && isSAFMode()) {
                // SAF: Obtener espacio a través del URI
                val uri = safRoot.uri
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                fd?.use {
                    val statFs = StatFs(it.fileDescriptor.toString())
                    statFs.availableBytes
                } ?: run {
                    // Fallback: intentar con el path del volumen si está disponible
                    val path = safRoot.uri.path
                    if (path != null) {
                        val statFs = StatFs(path)
                        statFs.availableBytes
                    } else -1L
                }
            } else {
                // Local: usar StatFs directamente
                val romsDir = getLocalRomsDirectory()
                romsDir.mkdirs()
                val statFs = StatFs(romsDir.absolutePath)
                statFs.availableBytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting available space: ${e.message}")
            -1L
        }
    }

    /**
     * Verifica si hay espacio suficiente para descargar los archivos especificados
     * @param files Lista de archivos a descargar con sus tamaños
     * @return StorageCheckResult con el resultado de la verificación
     */
    fun checkStorageForDownload(files: List<ArchiveOrgClient.DownloadableFile>): StorageCheckResult {
        val requiredBytes = files.sumOf { it.size }
        val availableBytes = getAvailableSpace()
        
        // Si no podemos determinar el espacio (SMB o error), permitimos la descarga
        if (availableBytes < 0) {
            return StorageCheckResult(
                hasEnoughSpace = true,
                requiredBytes = requiredBytes,
                availableBytes = -1,
                requiredFormatted = formatBytes(requiredBytes),
                availableFormatted = "Desconocido (SMB)",
                shortageBytes = 0,
                shortageFormatted = ""
            )
        }
        
        val hasEnough = availableBytes >= requiredBytes
        val shortage = if (hasEnough) 0L else requiredBytes - availableBytes
        
        return StorageCheckResult(
            hasEnoughSpace = hasEnough,
            requiredBytes = requiredBytes,
            availableBytes = availableBytes,
            requiredFormatted = formatBytes(requiredBytes),
            availableFormatted = formatBytes(availableBytes),
            shortageBytes = shortage,
            shortageFormatted = if (shortage > 0) formatBytes(shortage) else ""
        )
    }

    /**
     * Inicia la descarga de una ROM (en paralelo con otras descargas)
     */
    fun startDownload(
        pack: ArchiveOrgClient.RomPack,
        file: ArchiveOrgClient.DownloadableFile
    ) {
        val downloadId = "${pack.id}_${file.name}"
        
        // Si ya está descargando, no iniciar otra vez
        if (downloadJobs.containsKey(downloadId)) {
            Log.d(TAG, "Download already in progress: $downloadId")
            return
        }

        // Añadir a la lista con estado PENDING
        updateDownloadState(downloadId, DownloadInfo(
            id = downloadId,
            fileName = file.name,
            gameTitle = pack.name,
            progress = 0,
            downloadedBytes = 0,
            totalBytes = file.size,
            status = DownloadStatus.PENDING
        ))

        // Iniciar descarga en background (Using Internal Scope V8.3)
        val job = coroutineScope.launch {
            performSmartDownload(downloadId, pack, file)
        }
        
        downloadJobs[downloadId] = job
    }

    // SMB Configuration (Library Destination)
    // V8.4: SmbClient is now internal to RomDownloader (not passed from ViewModel)
    // This prevents the client from being destroyed when ViewModel is recreated.
    private val smbClient = SmbClient()
    private var libraryDestination: RomSource? = null
    
    // V8.5: Cache of files in SMB library (set of "systemId/fileName" keys)
    private val smbLibraryCache = mutableSetOf<String>()
    private var smbLibraryCacheLoaded = false
    
    fun setLibraryDestination(destination: RomSource?) {
        this.libraryDestination = destination
        // V8.5: Trigger cache refresh when destination is set
        if (destination != null) {
            coroutineScope.launch {
                refreshSmbLibraryCache()
            }
        }
    }
    
    /**
     * V8.5: Scans SMB library and populates the cache.
     * Called once when setLibraryDestination is set.
     */
    suspend fun refreshSmbLibraryCache() {
        val dest = libraryDestination ?: return
        Log.d(TAG, "V8.5: Refreshing SMB library cache...")
        
        try {
            val smbPath = dest.path.removePrefix("smb://")
            val slashIndex = smbPath.indexOf('/')
            if (slashIndex > 0) {
                val server = smbPath.substring(0, slashIndex)
                val remaining = smbPath.substring(slashIndex)
                val pathParts = remaining.removePrefix("/").split("/", limit = 2)
                val share = pathParts.getOrNull(0) ?: return
                val subPath = if (pathParts.size > 1) pathParts[1] else ""
                
                val result = smbClient.listFiles(
                    server = server,
                    share = share,
                    path = subPath,
                    credentials = dest.credentials
                )
                
                result.onSuccess { files ->
                    synchronized(smbLibraryCache) {
                        smbLibraryCache.clear()
                        files.forEach { file ->
                            // Store as "systemId/fileName" or just "fileName" if no system
                            val key = if (!file.system.isNullOrBlank()) {
                                "${file.system}/${file.name}"
                            } else {
                                file.name
                            }
                            smbLibraryCache.add(key.lowercase())
                        }
                        smbLibraryCacheLoaded = true
                        Log.d(TAG, "V8.5: SMB library cache loaded with ${smbLibraryCache.size} files")
                    }
                }
                result.onFailure { e ->
                    Log.e(TAG, "V8.5: Failed to refresh SMB cache: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "V8.5: Error refreshing SMB cache", e)
        }
    }
    
    /**
     * V8.5: Adds a file to the SMB cache after successful download.
     */
    private fun addToSmbLibraryCache(systemId: String?, fileName: String) {
        val key = if (!systemId.isNullOrBlank()) {
            "${systemId}/${fileName}"
        } else {
            fileName
        }
        synchronized(smbLibraryCache) {
            smbLibraryCache.add(key.lowercase())
        }
        Log.d(TAG, "V8.5: Added to SMB cache: $key")
    }

    private suspend fun performSmartDownload(downloadId: String, pack: ArchiveOrgClient.RomPack, file: ArchiveOrgClient.DownloadableFile) {
        val download = getDownload(downloadId) ?: return
        
        // 1. Download to Temp
        val tempFile = java.io.File(context.cacheDir, "temp_${System.currentTimeMillis()}_${file.name}")
        
        try {
            updateDownloadState(downloadId, download.copy(status = DownloadStatus.DOWNLOADING))
            
            val request = okhttp3.Request.Builder().url(file.url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body ?: throw IOException("Empty body")
            val totalBytes = body.contentLength()
            
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastUpdate = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check cancellation
                        if (!downloadJobs.containsKey(downloadId)) {
                            tempFile.delete()
                            return
                        }
                        
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            val progress = if (totalBytes > 0) (totalRead * 100 / totalBytes).toInt() else 0
                            updateDownloadState(downloadId, getDownload(downloadId)?.copy(progress = progress))
                            lastUpdate = now
                        }
                    }
                }
            }

            // 2. Smart Detection (CRC & Metadata)
            updateDownloadState(downloadId, getDownload(downloadId)?.copy(status = DownloadStatus.PROCESSING))
            Log.d(TAG, "Analyzing file: ${tempFile.name}")

            // Extract CRC from ZIP content if applicable
            val (crcLong, internalName) = getInternalRomInfo(tempFile) ?: Pair(tempFile.inputStream().calculateCrc32(), null)
            val crcHex = "%08X".format(crcLong)
            
            val storageFile = StorageFile(
                name = file.name,
                size = tempFile.length(),
                uri = Uri.fromFile(tempFile),
                path = tempFile.absolutePath,
                crc = crcHex,
                internalFileName = internalName
            )
            
            val metadata = gameMetadataProvider.retrieveMetadata(storageFile)
            val detectedSystemId = metadata?.system ?: pack.systemId
            
            Log.i(TAG, "Smart Detection Result: ${file.name} -> $detectedSystemId (Source: ${if(metadata?.system != null) "Engine" else "Fallback"})")

            // 3. Move to Final Destination
            var destPath = ""
            var modeLabel = "Local"

            // Centralized Logic (V8.1)
            val result = moveTempFileToLibrary(tempFile, file.name, detectedSystemId)
            destPath = result.first
            modeLabel = result.second

            // Cleanup
            tempFile.delete()

            updateDownloadState(downloadId, getDownload(downloadId)?.copy(
                status = DownloadStatus.COMPLETED,
                filePath = destPath,
                progress = 100
            ))
            
            // V8.5: Add to SMB cache so isFileDownloaded returns true immediately
            if (libraryDestination != null) {
                addToSmbLibraryCache(detectedSystemId, file.name)
            }
            
            showNotification("Download Complete", "${file.name} added to $detectedSystemId ($modeLabel)")
            LibraryIndexScheduler.scheduleLibrarySync(context)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            tempFile.delete()
            updateDownloadState(downloadId, getDownload(downloadId)?.copy(
                status = DownloadStatus.ERROR,
                error = e.message
            ))
            showNotification("Download Failed", e.message ?: "Unknown error")
        } finally {
            downloadJobs.remove(downloadId)
        }
    }
    
    /**
     * Extracts CRC and name of the first file inside a ZIP/7Z/RAR
     * Returns Pair(CRC, InternalName) or null if not a supported archive
     */
    private fun getInternalRomInfo(file: java.io.File): Pair<Long, String>? {
        val extension = file.extension.lowercase()
        return try {
            if (extension == "zip") {
                java.util.zip.ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory) {
                            return Pair(entry.crc, entry.name)
                        }
                    }
                }
            }
            // For 7z/RAR we would need extra libraries, fallback to file CRC for now
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect archive: ${e.message}")
            null
        }
    }

    fun cancelDownload(downloadId: String) {
        downloadJobs[downloadId]?.cancel()
        updateDownloadState(downloadId, null) // Remove from list
    }

    fun cancelAllDownloads() {
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        _downloads.value = emptyMap()
    }

    fun clearCompletedDownloads() {
        val current = _downloads.value.toMutableMap()
        val toRemove = current.filter { it.value.status == DownloadStatus.COMPLETED || it.value.status == DownloadStatus.ERROR }
        toRemove.keys.forEach { current.remove(it) }
        _downloads.value = current
    }

    fun getActiveDownloadsCount(): Int {
        return _downloads.value.count { 
            it.value.status == DownloadStatus.DOWNLOADING || it.value.status == DownloadStatus.PROCESSING 
        }
    }

    fun getDownload(id: String): DownloadInfo? = _downloads.value[id]

    private fun updateDownloadState(id: String, info: DownloadInfo?) {
        val current = _downloads.value.toMutableMap()
        if (info == null) {
            current.remove(id)
        } else {
            current[id] = info
        }
        _downloads.value = current
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "ROM Downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, content: String) {
        // Simple notification, can be improved
        /*
        val builder = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
        
        with(androidx.core.app.NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID + System.currentTimeMillis().toInt(), builder.build())
        }
        */
    }
    // --- V8.1: Centralized Architecture (The "Single Key") ---

    /**
     * Downloads a file from an SMB Source and saves it to the configured Library Destination (SMB or Local).
     * Reuses the exact same logic as Cloud downloads for saving.
     */
    suspend fun downloadFromSmbSource(file: com.swordfish.lemuroid.app.mobile.feature.catalog.SmbFile, source: RomSource) {
        val downloadId = file.path // Use path as ID
        
        // Register download state logic here if we want UI feedback
        // For now, we mirror what CatalogScreen was doing but using the centralized saver
        // NOTE: To fully integrate with UI progress, we would need to emit to _downloads flow.
        // For V8.1 scope, we focus on the transport + save logic.
        
        val tempFile = java.io.File(context.cacheDir, "transfer_${System.currentTimeMillis()}_${file.name}")
        val smbClientForSource = SmbClient() // Client for source

        try {
             Log.d("ANTIGRAVITY", "Starting SMB Source Download: ${file.name}")
             
            // 1. Download Source -> Temp
            val smbPath = source.path.removePrefix("smb://")
            val slashIndex = smbPath.indexOf('/')
            if (slashIndex > 0) {
                val server = smbPath.substring(0, slashIndex)
                val remaining = smbPath.substring(slashIndex)
                val pathParts = remaining.removePrefix("/").split("/", limit = 2)
                val share = pathParts.getOrNull(0) ?: throw IOException("Invalid Source Share")
                
                tempFile.outputStream().use { outputStream ->
                    smbClientForSource.downloadFile(
                        server = server,
                        share = share,
                        remotePath = file.path,
                        outputStream = outputStream,
                        credentials = source.credentials
                    ).getOrThrow()
                }
            } else {
                throw IOException("Invalid Source Path")
            }

            // 2. Smart Detection (Optional but recommended - reusing Cloud logic)
            // For now, we trust the folder structure of the SMB source, but we calculate CRC just in case
            // Metadata provider usage could be added here similar to performSmartDownload
            
            // 3. Move to Final Destination (Reuse Centralized Logic)
            val result = moveTempFileToLibrary(tempFile, file.name, file.system)
            val destPath = result.first
            val modeLabel = result.second
            
            Log.d("ANTIGRAVITY", "SMB Source Download Complete -> $destPath ($modeLabel)")
            showNotification("Download Complete", "${file.name} added to ${file.system ?: "Library"} ($modeLabel)")
            LibraryIndexScheduler.scheduleLibrarySync(context)

        } catch (e: Exception) {
            Log.e("ANTIGRAVITY", "SMB Download Failed: ${e.message}", e)
            showNotification("Download Failed", e.message ?: "Unknown error")
            throw e // Rethrow so UI knows
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Centralized logic to move a temp file to the correct library destination.
     * Respects the Hierarchy of Command: SMB (if config) > SAF > Local.
     * Returns Pair(DestinationPath, ModeLabel)
     */
    private suspend fun moveTempFileToLibrary(tempFile: java.io.File, fileName: String, systemId: String?): Pair<String, String> {
        var destPath = ""
        var modeLabel = "Local"

        // V7 FIX (STRUCTURAL): PRIORITIZE SMB
        val currentSmbSource = libraryDestination // Local copy for thread safety check

        if (currentSmbSource != null) {
                // --- SMB BRANCH (HIGHEST PRIORITY) ---
                val currentSmbClient = smbClient // V8.4: Always available (internal)
                Log.e("ANTIGRAVITY", "Mode: SMB (Priority). Dest: ${currentSmbSource.path}. Client Ready: true")

                if (currentSmbClient != null) {
                    modeLabel = "SMB"
                    
                    // Parse SMB path
                    val smbPath = currentSmbSource.path.removePrefix("smb://")
                    val slashIndex = smbPath.indexOf('/')
                    if (slashIndex > 0) {
                        val server = smbPath.substring(0, slashIndex)
                        val remaining = smbPath.substring(slashIndex)
                        val pathParts = remaining.removePrefix("/").split("/", limit = 2)
                        val share = pathParts.getOrNull(0) ?: throw IOException("Invalid SMB share")
                        val subPath = if (pathParts.size > 1) pathParts[1] else ""
                        
                        // Construct remote path including system folder
                        val remoteDir = if (!systemId.isNullOrBlank()) {
                            if (subPath.isNotEmpty()) "$subPath/$systemId" else systemId
                        } else {
                            subPath // Root of share (or subpath)
                        }
                        
                        // Fix for empty remoteDir forcing a leading slash issues
                        val remoteFilePath = if (!remoteDir.isNullOrEmpty()) "$remoteDir/$fileName" else fileName
                        
                        destPath = "smb://$server/$share/$remoteFilePath"
                        Log.d(TAG, "SMB Details -> Server: $server, Share: $share, RemoteFile: $remoteFilePath")
                        
                        // Upload
                        Log.d("ANTIGRAVITY", "Temp file size before upload: ${tempFile.length()} bytes")
                        
                        var uploadResult: Result<Unit>? = null
                        tempFile.inputStream().use { input ->
                            // Capture result
                            uploadResult = currentSmbClient.uploadFile(server, share, remoteFilePath, input, currentSmbSource.credentials)
                        }

                        // STRICT ERROR HANDLING (V6)
                        val finalResult = uploadResult
                        if (finalResult != null && finalResult.isFailure) {
                            val exception = finalResult.exceptionOrNull()
                            Log.e("ANTIGRAVITY", "SMB Upload FAILED: ${exception?.message}", exception)
                            throw IOException("SMB Upload Failed: ${exception?.message}", exception)
                        } else if (finalResult != null && finalResult.isSuccess) {
                            Log.d("ANTIGRAVITY", "SMB Upload SUCCESS")
                        }
                    } else {
                        throw IOException("Invalid SMB path: ${currentSmbSource.path}")
                    }
                } else {
                    // SMB configured but Client null - CRITICAL FAILURE
                    Log.e("ANTIGRAVITY", "CRITICAL: SMB Source set but Client is null. Aborting.")
                    throw IOException("SMB Client failed to initialize.")
                }
                
        } else if (isSAFMode()) {
            // --- SAF BRANCH (MOBILE DEFAULT) ---
            modeLabel = "SAF"
            val (destUri, _) = createRomsFile(fileName, systemId)
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Failed to save final file (SAF)")
            destPath = destUri.toString()
            
        } else {
            // --- LOCAL FALLBACK (LEGACY) ---
            // Only reached if NO SMB and NO SAF.
            Log.w(TAG, "Falling back to LOCAL storage (Legacy).")
            modeLabel = "Local"
            val (destUri, _) = createRomsFile(fileName, systemId)
            val destFile = java.io.File(destUri.path!!)
            tempFile.copyTo(destFile, overwrite = true)
            destPath = destUri.toString()
        }
        
        return Pair(destPath, modeLabel)
    }
}
