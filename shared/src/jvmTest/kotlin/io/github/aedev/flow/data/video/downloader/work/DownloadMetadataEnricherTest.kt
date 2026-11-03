package io.github.aedev.flow.data.video.downloader.work

import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind
import io.github.aedev.flow.data.video.downloader.tags.DownloadTags
import io.github.aedev.flow.testing.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test

class DownloadMetadataEnricherTest {
    private val repository: YouTubeRepository = mockk()
    private val dao: DownloadDao = mockk(relaxed = true)
    private val enricher = DownloadMetadataEnricher(repository, dao)

    private fun request(kind: DownloadKind = DownloadKind.VIDEO) =
        DownloadRequest(DownloadTags(kind = kind, videoId = "v", title = "Title"))

    @Test
    fun theWatchPageFillsDateDescriptionCountsAndChannelAndIsStored() =
        runTest {
            coEvery { repository.enrichFromWatchMetadata(any()) } answers {
                firstArg<Video>().copy(
                    channelId = "UC",
                    description = "About",
                    likeCount = 5,
                    viewCount = 50,
                    timestamp = 1_700_000_000_000,
                )
            }

            val enriched = enricher.enrich(request())

            assertThat(enriched.tags.channelId).isEqualTo("UC")
            assertThat(enriched.tags.description).isEqualTo("About")
            assertThat(enriched.tags.likeCount).isEqualTo(5)
            assertThat(enriched.tags.releaseDate).isNotNull()
            coVerify(exactly = 1) { dao.updateMetadata("v", "Title", any(), "UC", "About", any(), 50, 5, any()) }
        }

    @Test
    fun songsNeverAskTheWatchPage() =
        runTest {
            val song = request(DownloadKind.MUSIC)

            assertThat(enricher.enrich(song)).isSameInstanceAs(song)
            coVerify(exactly = 0) { repository.enrichFromWatchMetadata(any()) }
        }

    @Test
    fun aFailedWatchPageLeavesTheRequestAsItWas() =
        runTest {
            coEvery { repository.enrichFromWatchMetadata(any()) } throws IllegalStateException("offline")
            val original = request()

            assertThat(enricher.enrich(original)).isSameInstanceAs(original)
        }

    @Test
    fun releaseDatesAreIsoCalendarDates() {
        assertThat(DownloadMetadataEnricher.isoDate(1_700_000_000_000, TimeZone.UTC)).isEqualTo("2023-11-14")
        assertThat(DownloadMetadataEnricher.isoDate(0)).isNull()
    }
}
