package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.channel.toChannelTabContent
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * An explore grid has no owner to inherit a byline from, and a `gridVideoRenderer` carries neither
 * `ownerText` nor the avatar field a watch-page row uses, so both have to be read from the fields it
 * does ship.
 */
class ExploreBylineTest {
    private fun gamingVideos(): List<Video> =
        ExploreFixture(ExploreFixture.GAMING_TRENDING)
            .toChannelTabContent(ChannelTabKind.Videos, FeedItemOwner())
            .items
            .filterIsInstance<FeedItem.VideoItem>()
            .map { it.video }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aGridRowNamesItsCreatorFromTheShortByline() {
        val videos = gamingVideos()

        assertThat(videos).isNotEmpty()
        assertThat(videos.all { it.channelName.isNotBlank() }).isTrue()
        assertThat(videos.all { it.channelId.startsWith("UC") }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aGridRowCarriesTheCreatorAvatarItShipped() {
        assertThat(gamingVideos().all { it.channelThumbnailUrl.isNotBlank() }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aGridRowKeepsItsViewCountAndDate() {
        val videos = gamingVideos()

        assertThat(videos.all { it.viewCount > 0L }).isTrue()
        assertThat(videos.all { it.uploadDate.isNotBlank() }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun destinationRowsNameTheirCreatorToo() {
        val videos =
            ExploreFixture(ExploreFixture.DESTINATION_LIVE)
                .toSettledExploreDestinationPage()
                .shelves
                .flatMap { it.items }
                .filterIsInstance<FeedItem.VideoItem>()
                .map { it.video }

        assertThat(videos).isNotEmpty()
        assertThat(videos.all { it.channelName.isNotBlank() }).isTrue()
        assertThat(videos.all { it.channelThumbnailUrl.isNotBlank() }).isTrue()
    }
}
