package io.github.aedev.flow.data.local

import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.testing.assertThat
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Guards the album/playlist order reported in issue #676.
 *
 * Upstream answers the three DAO calls from mockk, which is JVM-only; here they run against the real tables in an
 * in-memory database, and the rows inserted are read back instead of captured.
 */
class PlaylistOrderingTest {
    private val database = inMemoryAppDatabase()
    private val playlistDao = database.playlistDao()
    private val repository = PlaylistRepository(playlistDao, database.videoDao())

    @BeforeTest
    fun createAlbum() =
        runBlocking {
            playlistDao.insertPlaylistRow(
                PlaylistEntity(id = "album", name = "album", description = "", thumbnailUrl = "", isPrivate = false, createdAt = 0L),
            )
        }

    @AfterTest
    fun teardown() = database.close()

    private suspend fun inserted() = playlistDao.getAllPlaylistVideoCrossRefs()

    private fun video(id: String) =
        Video(
            id = id,
            title = id,
            channelName = "artist",
            channelId = "channel",
            thumbnailUrl = "",
            duration = 0,
            viewCount = 0L,
            uploadDate = "",
        )

    @Test
    fun bulkAddKeepsTheGivenTrackOrder() =
        runBlocking {
            val tracks = listOf("t1", "t2", "t3", "t4")

            repository.addVideosToPlaylist("album", tracks.map(::video))

            assertThat(inserted().sortedBy { it.position }.map { it.videoId }).isEqualTo(tracks)
        }

    @Test
    fun singleAddAppendsAfterTheExistingTail() =
        runBlocking {
            repository.addVideosToPlaylist("album", listOf(video("t1"), video("t2")))

            repository.addVideoToPlaylist("album", video("t3"))

            assertThat(inserted().sortedBy { it.position }.map { it.videoId })
                .isEqualTo(listOf("t1", "t2", "t3"))
        }

    @Test
    fun reAddingATrackDoesNotMoveIt() =
        runBlocking {
            repository.addVideosToPlaylist("album", listOf(video("t1"), video("t2")))
            val before = inserted().associate { it.videoId to it.position }

            repository.addVideoToPlaylist("album", video("t1"))

            assertThat(inserted().associate { it.videoId to it.position }).isEqualTo(before)
            assertThat(inserted().sortedBy { it.position }.map { it.videoId }).isEqualTo(listOf("t1", "t2"))
        }
}
