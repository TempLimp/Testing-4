package io.github.aedev.flow.data.profile

import io.github.aedev.flow.utils.appFilesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.time.Clock

/**
 * The photos profiles use as avatars: one square WebP per profile, named by its id. The registry only records that a
 * profile has one and when it changed, and that version is what tells a cached image apart from a new photo.
 *
 * Upstream decodes the picked image with Coil and encodes it with Android's Bitmap; here Skia does both (the photo
 * picker hands over a file, as the document picker does for fonts).
 */
class ProfileAvatarPhotos {
    fun file(id: String): Path = fileFor(id)

    /** Saves the picked image as [id]'s photo and returns its version, or null when it can't be read. */
    suspend fun save(
        id: String,
        source: Path,
    ): Long? =
        withContext(Dispatchers.IO) {
            val bytes = runCatching { SystemFileSystem.source(source).buffered().use { it.readByteArray() } }.getOrNull() ?: return@withContext null
            val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return@withContext null
            val webp = centreSquare(image, PHOTO_PX).encodeToData(EncodedImageFormat.WEBP, PHOTO_QUALITY)?.bytes ?: return@withContext null
            write(file(id), webp)
            Clock.System.now().toEpochMilliseconds()
        }

    fun delete(id: String): Boolean = runCatching { SystemFileSystem.delete(file(id)) }.isSuccess

    // Android's AtomicFile: a reader sees the old photo or the new one, never half of it.
    private fun write(
        target: Path,
        bytes: ByteArray,
    ) {
        target.parent?.let { SystemFileSystem.createDirectories(it) }
        val partial = Path(target.parent!!, "${target.name}.new")
        try {
            SystemFileSystem.sink(partial).buffered().use { it.write(bytes) }
            SystemFileSystem.atomicMove(partial, target)
        } catch (e: Exception) {
            SystemFileSystem.delete(partial, mustExist = false)
            throw e
        }
    }

    companion object {
        const val DIR = "profile_avatars"
        private const val PHOTO_PX = 256
        private const val PHOTO_QUALITY = 90

        fun fileFor(id: String): Path = fileIn(appFilesDir(), id)

        internal fun fileIn(
            filesDir: Path,
            id: String,
        ): Path {
            require(isSafeProfileId(id)) { "Unsafe profile id" }
            return Path(filesDir, "$DIR/$id.webp")
        }
    }
}

/** The middle square of [source], scaled to [side] pixels. */
internal fun centreSquare(
    source: Image,
    side: Int,
): Image {
    val edge = minOf(source.width, source.height)
    val surface = Surface.makeRasterN32Premul(side, side)
    surface.canvas.drawImageRect(
        source,
        Rect.makeXYWH(((source.width - edge) / 2).toFloat(), ((source.height - edge) / 2).toFloat(), edge.toFloat(), edge.toFloat()),
        Rect.makeWH(side.toFloat(), side.toFloat()),
        SamplingMode.LINEAR,
        null,
        true,
    )
    return surface.makeImageSnapshot()
}
