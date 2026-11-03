package io.github.aedev.flow.data.local

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import io.github.aedev.flow.utils.appFilesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface

/** Why a picked file was not taken as the app font. */
enum class FontImportError { NOT_A_FONT_FILE, TOO_LARGE, UNREADABLE }

sealed interface FontImportResult {
    data class Imported(
        val displayName: String,
    ) : FontImportResult

    data class Rejected(
        val error: FontImportError,
    ) : FontImportResult
}

/**
 * The one custom font file, kept in app storage because a picked document does not stay readable. Upstream reads
 * a picked document's URI through the content resolver; iOS's document picker hands over a file, whose own name
 * and size answer what the resolver did.
 */
class CustomFontStore {
    private val dir = Path(appFilesDir(), DIR)

    fun file(): Path? =
        if (!SystemFileSystem.exists(dir)) {
            null
        } else {
            SystemFileSystem.list(dir).firstOrNull { SystemFileSystem.metadataOrNull(it)?.isRegularFile == true && it.name.startsWith(BASENAME) }
        }

    suspend fun import(source: Path): FontImportResult =
        withContext(Dispatchers.IO) {
            val name = source.name
            val size = SystemFileSystem.metadataOrNull(source)?.size?.takeIf { it >= 0 }
            val extension = fontExtension(name) ?: return@withContext FontImportResult.Rejected(FontImportError.NOT_A_FONT_FILE)
            if (size != null && !isWithinSizeLimit(size)) return@withContext FontImportResult.Rejected(FontImportError.TOO_LARGE)
            SystemFileSystem.createDirectories(dir)
            val incoming = Path(dir, INCOMING)
            try {
                val copied = copyCapped(source, incoming)
                if (!isWithinSizeLimit(copied)) {
                    SystemFileSystem.delete(incoming, mustExist = false)
                    return@withContext FontImportResult.Rejected(FontImportError.TOO_LARGE)
                }
                if (loadTypeface(incoming) == null) {
                    SystemFileSystem.delete(incoming, mustExist = false)
                    return@withContext FontImportResult.Rejected(FontImportError.UNREADABLE)
                }
                file()?.let { SystemFileSystem.delete(it) }
                // atomicMove throws where Android's renameTo returned false; the catch below rejects it the same way.
                SystemFileSystem.atomicMove(incoming, Path(dir, "$BASENAME.$extension"))
                FontImportResult.Imported(name)
            } catch (e: IOException) {
                SystemFileSystem.delete(incoming, mustExist = false)
                FontImportResult.Rejected(FontImportError.UNREADABLE)
            }
        }

    fun delete() {
        file()?.let { SystemFileSystem.delete(it) }
    }

    /** The custom font as a family, or null when the file is gone or no longer reads as a font. */
    fun loadFamily(file: Path): FontFamily? {
        val bytes = readFont(file) ?: return null
        if (typefaceOf(bytes) == null) return null
        // The identity carries the content, so a newly imported font under the same file name is not served from cache.
        return FontFamily(Font(identity = "$file#${bytes.contentHashCode()}", data = bytes))
    }

    /** Copies at most one byte past the limit, so an unknown-size stream can't fill the disk. */
    private fun copyCapped(
        source: Path,
        target: Path,
    ): Long {
        var total = 0L
        SystemFileSystem.source(source).buffered().use { input ->
            SystemFileSystem.sink(target).buffered().use { sink ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.readAtMostTo(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    total += read
                    if (!isWithinSizeLimit(total)) break
                }
            }
        }
        return total
    }

    private fun loadTypeface(file: Path): Typeface? = readFont(file)?.let(::typefaceOf)

    private fun readFont(file: Path): ByteArray? = runCatching { SystemFileSystem.source(file).buffered().use { it.readByteArray() } }.getOrNull()

    /** Like upstream's `Typeface.Builder`, Skia answers null for a file that is not a font rather than a fallback face. */
    private fun typefaceOf(bytes: ByteArray): Typeface? = runCatching { FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) }.getOrNull()

    companion object {
        const val MAX_SIZE_BYTES = 10L * 1024 * 1024
        private const val DIR = "fonts"
        private const val BASENAME = "custom"
        private const val INCOMING = "incoming.tmp"
        private const val BUFFER_SIZE = 64 * 1024
        private val Extensions = setOf("ttf", "otf")

        /** The lowercase extension when [name] is a TrueType or OpenType file name, else null. */
        fun fontExtension(name: String?): String? =
            name
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.takeIf { it in Extensions }

        fun isWithinSizeLimit(bytes: Long): Boolean = bytes in 1..MAX_SIZE_BYTES
    }
}
