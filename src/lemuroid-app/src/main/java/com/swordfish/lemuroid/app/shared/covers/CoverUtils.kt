package com.swordfish.lemuroid.app.shared.covers

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.SuccessResult
import com.swordfish.lemuroid.common.drawable.TextDrawable
import com.swordfish.lemuroid.common.graphics.ColorUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.smb.SmbClient
import com.swordfish.lemuroid.lib.storage.smb.SmbCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

object CoverUtils {
    const val IMAGE_CACHE_SUBFOLDER = "image_cache"
    private const val GAME_COVERS_SUBFOLDER = "GameCovers"
    private const val COVERS_CACHE_SUBFOLDER = "gamecovers"
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private sealed interface CoverLocation {
        data class LocalFile(val file: File) : CoverLocation

        data class SafUri(val uri: Uri) : CoverLocation

        data class SmbRemote(
            val server: String,
            val share: String,
            val remotePath: String,
            val credentials: SmbCredentials?,
        ) : CoverLocation
    }

    fun loadCover(
        game: Game,
        imageView: ImageView?,
    ) {
        if (imageView == null) return

        imageView.load(getCoverModel(imageView.context, game), imageView.context.imageLoader) {
            val fallbackDrawable = getFallbackDrawable(game)
            fallback(fallbackDrawable)
            error(fallbackDrawable)
            listener(
                onSuccess = { _, result ->
                    persistCoverAsync(imageView.context.applicationContext, game, result)
                },
            )
        }
    }

    fun getCoverModel(
        appContext: Context,
        game: Game,
    ): Any? {
        val localUri = getExistingStoredCoverUri(appContext, game)
        return localUri ?: game.coverFrontUrl
    }

    fun persistCoverAsync(
        appContext: Context,
        game: Game,
        result: SuccessResult,
    ) {
        ioScope.launch {
            persistCoverInternal(appContext, game, result.drawable)
        }
    }

    fun buildImageLoader(applicationContext: Context): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .diskCache(
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve(IMAGE_CACHE_SUBFOLDER))
                    .maxSizePercent(0.20)
                    .build(),
            )
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addNetworkInterceptor(ThrottleFailedThumbnailsInterceptor)
                    .build()
            }
            .crossfade(true)
            .interceptorDispatcher(Dispatchers.IO)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
    }

    fun getFallbackDrawable(game: Game) = TextDrawable(computeTitle(game), computeColor(game))

    fun getFallbackRemoteUrl(game: Game): String {
        val color = Integer.toHexString(computeColor(game)).substring(2)
        val title = computeTitle(game)
        return "https://fakeimg.pl/512x512/$color/fff/?font=bebas&text=$title"
    }

    private fun computeTitle(game: Game): String {
        val sanitizedName =
            game.title
                .replace(Regex("\\(.*\\)"), "")

        return sanitizedName.asSequence()
            .filter { it.isDigit() or it.isUpperCase() or (it == '&') }
            .take(3)
            .joinToString("")
            .ifBlank { game.title.first().toString() }
            .capitalize()
    }

    private fun computeColor(game: Game): Int {
        return ColorUtils.randomColor(game.title)
    }

    private suspend fun persistCoverInternal(
        appContext: Context,
        game: Game,
        drawable: Drawable,
    ) {
        val location = resolvePreferredCoverLocation(appContext, game)

        if (locationExists(location, appContext.contentResolver)) {
            return
        }

        val bitmap = drawableToBitmap(drawable)

        when (location) {
            is CoverLocation.LocalFile -> {
                runCatching {
                    location.file.parentFile?.mkdirs()
                    location.file.outputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    }
                }
            }

            is CoverLocation.SafUri -> {
                runCatching {
                    appContext.contentResolver.openOutputStream(location.uri, "w")?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    }
                }
            }

            is CoverLocation.SmbRemote -> {
                val uploaded = runCatching {
                    val smbClient = SmbClient()
                    val coverBytes = bitmapToJpegBytes(bitmap)
                    ByteArrayInputStream(coverBytes).use { stream ->
                        smbClient.uploadFile(
                            server = location.server,
                            share = location.share,
                            remotePath = location.remotePath,
                            inputStream = stream,
                            credentials = location.credentials,
                        )
                    }
                }.getOrNull()?.isSuccess == true

                // If SMB write is unavailable (RO share, ACLs, etc.), keep a local mirror cache.
                if (!uploaded) {
                    val localMirror = File(File(appContext.cacheDir, COVERS_CACHE_SUBFOLDER), "smb_${game.id}.jpg")
                    runCatching {
                        localMirror.parentFile?.mkdirs()
                        localMirror.outputStream().use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        }
                    }
                }
            }
        }
    }

    private fun getExistingStoredCoverUri(
        appContext: Context,
        game: Game,
    ): Uri? {
        val coverName = "${game.id}.jpg"
        val uri = Uri.parse(game.fileUri)

        // Never do SMB I/O here: this path is hit from UI rendering.
        // Only use local cached mirrors for SMB and let normal metadata/cover fetch proceed asynchronously.
        if (uri.scheme == "smb") {
            val localMirror = File(File(appContext.cacheDir, COVERS_CACHE_SUBFOLDER), "smb_$coverName")
            return if (localMirror.exists() && localMirror.length() > 0) {
                Uri.fromFile(localMirror)
            } else {
                null
            }
        }

        val location = resolvePreferredCoverLocation(appContext, game)
        return when (location) {
            is CoverLocation.LocalFile -> {
                if (location.file.exists()) {
                    Uri.fromFile(location.file)
                } else {
                    null
                }
            }
            is CoverLocation.SafUri -> {
                if (locationExists(location, appContext.contentResolver)) {
                    location.uri
                } else {
                    null
                }
            }
            is CoverLocation.SmbRemote -> null
        }
    }

    private fun resolvePreferredCoverLocation(
        appContext: Context,
        game: Game,
    ): CoverLocation {
        val coverName = "${game.id}.jpg"
        val uri = Uri.parse(game.fileUri)

        if (uri.scheme == "smb") {
            val smb = resolveSmbCoverLocation(appContext, uri, coverName)
            if (smb != null) {
                return smb
            }
        }

        if (uri.scheme == "file") {
            val romParent = File(uri.path ?: "").parentFile
            if (romParent != null && romParent.exists() && romParent.canWrite()) {
                return CoverLocation.LocalFile(File(File(romParent, GAME_COVERS_SUBFOLDER), coverName))
            }
        }

        val safRoot = resolveSafRootWritable(appContext)
        if (safRoot != null) {
            val coversDir = safRoot.findFile(GAME_COVERS_SUBFOLDER) ?: safRoot.createDirectory(GAME_COVERS_SUBFOLDER)
            if (coversDir != null && coversDir.canWrite()) {
                val existing = coversDir.findFile(coverName)
                val file = existing ?: coversDir.createFile("image/jpeg", coverName)
                if (file != null) {
                    return CoverLocation.SafUri(file.uri)
                }
            }
        }

        val fallback = File(File(appContext.cacheDir, COVERS_CACHE_SUBFOLDER), coverName)
        return CoverLocation.LocalFile(fallback)
    }

    private fun resolveSafRootWritable(appContext: Context): DocumentFile? {
        val uriString = SharedPreferencesHelper.getSAFUri(appContext) ?: return null
        val treeUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return null
        return if (root.canWrite()) root else null
    }

    private fun locationExists(
        location: CoverLocation,
        contentResolver: ContentResolver,
    ): Boolean {
        return when (location) {
            is CoverLocation.LocalFile -> location.file.exists() && location.file.length() > 0
            is CoverLocation.SafUri -> {
                runCatching {
                    contentResolver.openInputStream(location.uri)?.use { stream ->
                        stream.read() != -1
                    } ?: false
                }.getOrDefault(false)
            }
            is CoverLocation.SmbRemote -> false
        }
    }

    private fun resolveSmbCoverLocation(
        appContext: Context,
        uri: Uri,
        coverName: String,
    ): CoverLocation.SmbRemote? {
        val fullPath = uri.path?.removePrefix("/")?.replace("\\", "/") ?: return null
        val shareFromUri = fullPath.substringBefore("/", "")
        val share = if (shareFromUri.isNotBlank()) shareFromUri else return null

        val romPathInShare = fullPath.substringAfter("/", "")
        val romDir = romPathInShare.substringBeforeLast("/", "")
        val coversDir = if (romDir.isBlank()) GAME_COVERS_SUBFOLDER else "$romDir/$GAME_COVERS_SUBFOLDER"
        val remotePath = "$coversDir/$coverName"

        val prefs = SharedPreferencesHelper.getSharedPreferences(appContext)
        val server = uri.authority?.takeIf { it.isNotBlank() }
            ?: prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_SERVER, null)
            ?: return null

        val username = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_USERNAME, null)
        val password = prefs.getString(SharedPreferencesHelper.KEY_SMB_LIBRARY_PASSWORD, null).orEmpty()
        val credentials = username?.takeIf { it.isNotBlank() }?.let { SmbCredentials(it, password) }

        return CoverLocation.SmbRemote(
            server = server,
            share = share,
            remotePath = remotePath,
            credentials = credentials,
        )
    }

    /**
     * Downloads a cover from SMB and saves it to local cache, returning a file:// URI for Coil.
     */
    private fun resolveSmbCoverAsLocalCache(
        appContext: Context,
        smb: CoverLocation.SmbRemote,
        coverName: String,
    ): Uri? {
        val cacheDir = File(appContext.cacheDir, COVERS_CACHE_SUBFOLDER)
        cacheDir.mkdirs()
        val localFile = File(cacheDir, "smb_$coverName")
        if (localFile.exists() && localFile.length() > 0) return Uri.fromFile(localFile)
        return runCatching {
            kotlinx.coroutines.runBlocking {
                localFile.outputStream().use { out ->
                    SmbClient().downloadFile(
                        server = smb.server,
                        share = smb.share,
                        remotePath = smb.remotePath,
                        outputStream = out,
                        credentials = smb.credentials,
                    )
                }
            }
            Uri.fromFile(localFile)
        }.getOrNull()
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        return output.toByteArray()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
