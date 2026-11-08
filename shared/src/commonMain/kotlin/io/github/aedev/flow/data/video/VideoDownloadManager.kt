package io.github.aedev.flow.data.video

import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Clock

/**
 * Legacy compat — wraps DownloadWithItems for backward compatibility with existing UI.
 */
data class DownloadedVideo(
    val video: Video,
    val filePath: String,
    val downloadedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val fileSize: Long = 0,
    val downloadId: Long = -1,
    val quality: String = "Unknown",
    val isAudioOnly: Boolean = false,
)

/**
 * Manages all video/audio download persistence and file operations.
 * Backed by Room database via DownloadDao.
 *
 * Pulled forward by S10, the read half the player needs (PlaybackLoadResolver plays a finished download instead of
 * streaming it); the rest of upstream's class, which writes and moves downloads, is S18's.
 */
class VideoDownloadManager(
    private val downloadDao: DownloadDao,
) {
    suspend fun getSponsorBlockData(videoId: String): String? = downloadDao.getSponsorBlockData(videoId)

    /** Get download with items */
    suspend fun getDownloadWithItems(videoId: String): DownloadWithItems? = downloadDao.getDownloadWithItems(videoId)

    /** The finished video download of [videoId] when its file is still on disk. */
    suspend fun findLocalCopy(videoId: String): DownloadedVideo? =
        withContext(Dispatchers.IO) {
            downloadDao
                .getDownloadWithItems(videoId)
                ?.takeIf { it.overallStatus == DownloadItemStatus.COMPLETED && !it.isAudioOnly }
                ?.takeIf { download -> download.primaryFilePath?.let(::downloadFileExists) == true }
                ?.let(::toDownloadedVideo)
        }

    /** Path of the finished video download of [videoId] when its file is still on disk. */
    suspend fun localCopyPath(videoId: String): String? = findLocalCopy(videoId)?.filePath

    fun toDownloadedVideo(dwi: DownloadWithItems): DownloadedVideo =
        DownloadedVideo(
            video =
                Video(
                    id = dwi.download.videoId,
                    title = dwi.download.title,
                    channelName = dwi.download.uploader,
                    channelId = dwi.download.channelId,
                    thumbnailUrl = dwi.download.thumbnailPath?.let { "file://$it" } ?: dwi.download.thumbnailUrl,
                    duration = dwi.download.duration.toInt(),
                    viewCount = dwi.download.viewCount,
                    uploadDate = "",
                    description = dwi.download.description,
                    likeCount = dwi.download.likeCount,
                ),
            filePath = dwi.primaryFilePath ?: "",
            downloadedAt = dwi.download.createdAt,
            fileSize = dwi.totalSize,
            downloadId = dwi.download.createdAt,
            quality = dwi.items.firstOrNull()?.quality ?: "Unknown",
            isAudioOnly = dwi.isAudioOnly,
        )
}

/**
 * DownloadFiles.exists for a download's path. Upstream also asks Android's content resolver about a document in a
 * folder the user picked; where an iOS download lives is S18's to decide.
 */
// shortcut: plain file paths only; S18 adds downloads kept in a folder picked through Files.
internal fun downloadFileExists(path: String): Boolean = runCatching { SystemFileSystem.exists(Path(path)) }.getOrDefault(false)
