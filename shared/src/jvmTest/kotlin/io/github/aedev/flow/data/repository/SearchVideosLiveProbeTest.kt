package io.github.aedev.flow.data.repository

import io.github.aedev.flow.testing.assertThat
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test

/**
 * Runs the Home discovery search against the live API. Skipped unless `FLOW_LIVE_PROBE=1` is set,
 * so CI and ordinary test runs never touch the network.
 */
class SearchVideosLiveProbeTest {
    @Test
    fun discoveryQueriesReturnDatedVideosWithChannelIds() {
        assumeTrue("set FLOW_LIVE_PROBE=1 to run", System.getenv("FLOW_LIVE_PROBE") == "1")
        val repository = YouTubeRepository(mockk(relaxed = true))

        val results =
            runBlocking {
                listOf("lofi hip hop", "space documentary", "football highlights", "cooking pasta").associateWith { query ->
                    repository.searchVideos(query).first
                }
            }

        results.forEach { (query, videos) ->
            println(
                "'$query': total=${videos.size} videos=${videos.count { !it.isShort && !it.isLive }} " +
                    "shorts=${videos.count { it.isShort }} live=${videos.count { it.isLive }} " +
                    "upcoming=${videos.count { it.isUpcoming }} timestamp=${videos.count { it.timestamp > 0L }} " +
                    "ucChannelId=${videos.count { it.channelId.startsWith("UC") }} " +
                    "avatar=${videos.count { it.channelThumbnailUrl.isNotBlank() }} " +
                    "collabs=${videos.count { it.collaborators.size > 1 }} " +
                    "shortsDated=${videos.count { it.isShort && it.timestamp > 0L }} " +
                    "shortsTimed=${videos.count { it.isShort && it.duration > 0 }} " +
                    "shortsWithChannel=${videos.count { it.isShort && it.channelId.startsWith("UC") }}",
            )
        }
        assertThat(results.values.flatten()).isNotEmpty()
    }
}
