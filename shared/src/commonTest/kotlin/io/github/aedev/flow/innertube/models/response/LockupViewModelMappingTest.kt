package io.github.aedev.flow.innertube.models.response

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.requireFixture
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Exercised against a captured watch-next payload rather than a hand-built fixture: the fields this
 * mapper reads moved when YouTube replaced `compactVideoRenderer` with `lockupViewModel`, and a
 * fixture written from the model would have kept passing while the real shape drifted.
 */
class LockupViewModelMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun response(): WatchMetadataResponse = json.decodeFromString(requireFixture("watch_next_related_lockups.json"))

    @Test
    fun relatedLockupsDeserializeIntoCompactVideos() {
        val videos = response().relatedVideos()

        assertThat(videos.map { it.videoId }).containsExactly("DdNbdhWmX04", "i_nj_vkD03g").inOrder()
    }

    @Test
    fun channelIdComesFromTheAvatarBrowseEndpoint() {
        val videos = response().relatedVideos()

        assertThat(videos.map { it.channelId() })
            .containsExactly("UCOMwpjGPUrGGUS2Diq6UmVw", "UCXuqSBlHAE6Xw-yeJA0Tunw")
            .inOrder()
    }

    @Test
    fun channelHandleAndAvatarAreCarriedThrough() {
        val first = response().relatedVideos().first()

        assertThat(first.channelHandle).isEqualTo("/@ElHusamoX")
        assertThat(first.channelAvatarUrl).contains("yt3.ggpht.com")
    }

    @Test
    fun viewCountIsTakenFromThePartWithThePlayArrowIcon() {
        val videos = response().relatedVideos()

        assertThat(videos.map { it.viewCountText?.text() }).containsExactly("342K", "1.6M").inOrder()
    }

    @Test
    fun bylineIsTheChannelNameAndNeverTheViewCount() {
        val videos = response().relatedVideos()

        assertThat(videos.map { it.longBylineText?.text() })
            .containsExactly("El Husamo X", "Linus Tech Tips")
            .inOrder()
    }

    @Test
    fun publishedTextAndDurationSurviveTheMapping() {
        val first = response().relatedVideos().first()

        assertThat(first.publishedTimeText?.text()).isEqualTo("18h ago")
        assertThat(first.lengthText?.text()).isEqualTo("21:51")
    }

    @Test
    fun aCompactVideoRendererStillResolvesItsChannelIdFromTheByline() {
        val legacy =
            WatchMetadataResponse.CompactVideo(
                videoId = "legacy",
                longBylineText =
                    WatchMetadataResponse.Runs(
                        runs =
                            listOf(
                                WatchMetadataResponse.Runs.Run(
                                    text = "Some Channel",
                                    navigationEndpoint =
                                        WatchMetadataResponse.NavEndpoint(
                                            browseEndpoint =
                                                WatchMetadataResponse.NavEndpoint.BrowseEndpoint(
                                                    browseId = "UCLegacyChannelId",
                                                ),
                                        ),
                                ),
                            ),
                    ),
            )

        assertThat(legacy.channelId()).isEqualTo("UCLegacyChannelId")
    }
}
