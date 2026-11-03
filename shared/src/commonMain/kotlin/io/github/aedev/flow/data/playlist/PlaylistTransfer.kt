package io.github.aedev.flow.data.playlist

import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.appCacheDir
import io.github.aedev.flow.utils.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.time.Clock

private const val SHARE_DIR = "playlists"

// Far above any real playlist (5,000 videos is about 2.5 MB), low enough that a wrong file can't exhaust memory.
internal const val PLAYLIST_FILE_MAX_BYTES = 16L * 1024 * 1024

/** What an import did. */
sealed interface PlaylistImport {
    data class Imported(
        val playlistId: String,
        val name: String,
        val videoCount: Int,
        val isMusic: Boolean = false,
    ) : PlaylistImport

    data object NotAPlaylist : PlaylistImport

    data object TooNew : PlaylistImport

    data object Empty : PlaylistImport

    data object Unreadable : PlaylistImport
}

/**
 * Writes playlists to files, for saving or sharing, and reads them back as new playlists.
 *
 * Upstream reads and writes `content://` URIs through a ContentResolver and shares through a
 * FileProvider; iOS's document picker and share sheet hand over and take plain files, so every
 * document here is a file path (S06's CustomFontStore does the same).
 */
class PlaylistTransfer
    constructor(
        private val playlists: PlaylistRepository,
    ) {
        /** Saves the playlist into [target], a document the viewer picked; false when it could not be written. */
        suspend fun writeTo(
            target: Path,
            name: String,
            description: String,
            videos: List<Video>,
            isMusic: Boolean = false,
        ): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    val text = PlaylistFileCodec.encode(name, description, videos, Clock.System.now().toEpochMilliseconds(), isMusic)
                    target.writeText(text)
                }.isSuccess
            }

        /** A copy in the cache another app can read, replacing the last one shared; null when it could not be written. */
        suspend fun shareableCopy(
            name: String,
            description: String,
            videos: List<Video>,
            isMusic: Boolean = false,
        ): Path? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = Path(appCacheDir(), SHARE_DIR).also { SystemFileSystem.createDirectories(it) }
                    SystemFileSystem.list(dir).forEach { SystemFileSystem.delete(it, mustExist = false) }
                    val file = Path(dir, PlaylistFileCodec.fileName(name))
                    file.writeText(PlaylistFileCodec.encode(name, description, videos, Clock.System.now().toEpochMilliseconds(), isMusic))
                    file
                }.getOrNull()
            }

        /** Adds the playlist in [source] as a new playlist of the viewer's own. */
        suspend fun import(
            source: Path,
            fallbackName: String,
        ): PlaylistImport =
            withContext(Dispatchers.IO) {
                val text =
                    runCatching { SystemFileSystem.source(source).use { it.readBounded() } }.getOrNull()
                        ?: return@withContext PlaylistImport.Unreadable
                when (val read = PlaylistFileCodec.decode(text)) {
                    PlaylistFileRead.NotAPlaylist -> {
                        PlaylistImport.NotAPlaylist
                    }

                    PlaylistFileRead.TooNew -> {
                        PlaylistImport.TooNew
                    }

                    is PlaylistFileRead.Read -> {
                        val videos = read.file.videos.map(PlaylistFileVideo::toVideo)
                        if (videos.isEmpty()) return@withContext PlaylistImport.Empty
                        val name =
                            read.file.playlist.name
                                .trim()
                                .ifEmpty { fallbackName }
                        val isMusic = read.file.playlist.isMusic
                        val id = playlists.importPlaylist(name, read.file.playlist.description, videos, isMusic)
                        PlaylistImport.Imported(id, name, videos.size, isMusic)
                    }
                }
            }

        private fun RawSource.readBounded(): String? {
            val out = Buffer()
            while (true) {
                val read = readAtMostTo(out, DEFAULT_BUFFER_SIZE.toLong())
                if (read < 0) break
                if (out.size > PLAYLIST_FILE_MAX_BYTES) return null
            }
            return out.readString()
        }

        private companion object {
            const val DEFAULT_BUFFER_SIZE = 8 * 1024
        }
    }
