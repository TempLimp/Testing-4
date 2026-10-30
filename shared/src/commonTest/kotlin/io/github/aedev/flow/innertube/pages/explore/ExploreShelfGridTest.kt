package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.channel.toChannelTabContent
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * A shelf's "see all" and the Gaming trending tab are flat grids of renderers the channel tabs
 * already parse, so they ride that path rather than a second one. These pin that.
 */
class ExploreShelfGridTest {
    private fun tab(name: String) = ExploreFixture(name).toChannelTabContent(ChannelTabKind.Videos, FeedItemOwner())

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aSeeAllGridParsesAsAFlatPageOfVideos() {
        val page = tab(ExploreFixture.SHELF_SEE_ALL)

        assertThat(page.items).isNotEmpty()
        assertThat(page.items.all { it is FeedItem.VideoItem }).isTrue()
        assertThat(page.sections).isEmpty()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aSeeAllGridHandsBackItsContinuationToken() {
        assertThat(tab(ExploreFixture.SHELF_SEE_ALL).continuation).isNotEmpty()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun theContinuationResponseAppendsMoreOfTheSameItems() {
        val page = tab(ExploreFixture.SHELF_SEE_ALL_CONTINUATION)

        assertThat(page.items).isNotEmpty()
        assertThat(page.items.all { it is FeedItem.VideoItem }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun theGamingTrendingTabParsesItsGridVideoRenderers() {
        val page = tab(ExploreFixture.GAMING_TRENDING)

        assertThat(page.items).isNotEmpty()
        assertThat(page.items.all { it is FeedItem.VideoItem }).isTrue()
        assertThat(page.continuation).isNull()
    }
}
