package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Ignore
import kotlin.test.Test

class ExploreDestinationParserTest {
    private fun page(name: String) = ExploreFixture(name).toSettledExploreDestinationPage()

    private fun shelfTitled(
        name: String,
        title: String,
    ): FeedShelf = page(name).shelves.first { it.title == title }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun readsTheLiveDestinationsShelvesOutOfRichSectionRenderers() {
        val page = page(ExploreFixture.DESTINATION_LIVE)

        assertThat(page.title).isEqualTo("Live")
        assertThat(page.shelves.map { it.title })
            .containsAtLeast("Live Now", "Recent Live Streams", "Upcoming Live Streams")
            .inOrder()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aShelfCarriesTheSeeAllParamsTheResponseShipped() {
        val shelf = shelfTitled(ExploreFixture.DESTINATION_LIVE, "Live Now")

        assertThat(shelf.moreParams).isEqualTo("EgdsaXZldGFikgEDCKEK")
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun liveRowsParseAsVideosWithTheirOwnByline() {
        val items = shelfTitled(ExploreFixture.DESTINATION_LIVE, "Live Now").items

        assertThat(items).isNotEmpty()
        assertThat(items.all { it is FeedItem.VideoItem }).isTrue()
        val video = (items.first() as FeedItem.VideoItem).video
        assertThat(video.channelName).isNotEmpty()
        assertThat(video.channelId).startsWith("UC")
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun theHeroCarouselIsSkippedRatherThanRenderedAsAnEmptyShelf() {
        val page = page(ExploreFixture.DESTINATION_LIVE)

        assertThat(page.shelves.none { it.items.isEmpty() }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun shelfIdsArePositionQualifiedSoSameTitledShelvesNeverCollide() {
        val ids = page(ExploreFixture.DESTINATION_NEWS).shelves.map { it.id }

        assertThat(ids).containsNoDuplicates()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun theNewsDestinationPublishesItsCategoryTabsWithTheirParams() {
        val tabs = page(ExploreFixture.DESTINATION_NEWS).tabs

        assertThat(tabs.map { it.title }).containsAtLeast("Top stories", "Sports", "World")
        assertThat(tabs.first().selected).isTrue()
        assertThat(tabs.first { it.title == "Sports" }.params).isEqualTo("EgZzcG9ydHPSBwIIAg%3D%3D")
        assertThat(tabs.all { it.browseId == "FEnews_destination" }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun theNewsDestinationCarriesAShelfOfCommunityPosts() {
        val posts =
            page(ExploreFixture.DESTINATION_NEWS)
                .shelves
                .flatMap { it.items }
                .filterIsInstance<FeedItem.PostItem>()

        assertThat(posts).isNotEmpty()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun theMusicDestinationsAlbumLockupsParseAsPlaylists() {
        val shelf = shelfTitled(ExploreFixture.DESTINATION_MUSIC, "New & Trending Songs")

        assertThat(shelf.items).isNotEmpty()
        assertThat(shelf.items.all { it is FeedItem.PlaylistItem }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aLearningShelfWhoseSeeAllIsAPlaylistExposesItAsOne() {
        val withPlaylist = page(ExploreFixture.DESTINATION_LEARNING).shelves.firstOrNull { it.morePlaylistId != null }

        assertThat(withPlaylist).isNotNull()
        assertThat(withPlaylist!!.morePlaylistId).startsWith("OL")
    }

    /**
     * Sports heads its page with a `carouselHeaderRenderer` and publishes no page title at all, so
     * the screen labels a tab from its own string rather than from the response.
     */
    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too
    @Test
    fun theSportsDestinationReadsTheSameWayAsLiveWithoutAPageTitle() {
        val page = page(ExploreFixture.DESTINATION_SPORTS)

        assertThat(page.title).isNull()
        assertThat(page.shelves.map { it.title }).contains("Highlights")
        assertThat(page.shelves.all { it.moreParams != null }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun anUnavailableDestinationYieldsNoShelvesInsteadOfThrowing() {
        val page = page(ExploreFixture.DESTINATION_MOVIES_UNAVAILABLE)

        assertThat(page.shelves).isEmpty()
        assertThat(page.title).isEqualTo("Movies")
    }

    /**
     * The shelf id is position-qualified, and the position counts shelves that parsed rather than
     * entries walked — streaming them one at a time has to keep that counter, or two shelves
     * sharing a title would collide and crash the lazy list.
     */
    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too
    @Test
    fun streamedShelvesMatchTheSettledPageInOrderAndWithUniqueIds() {
        val fixture = ExploreFixture(ExploreFixture.DESTINATION_LIVE)
        val streamed = fixture.exploreShelves().toList()
        val settled = fixture.toSettledExploreDestinationPage().shelves

        assertThat(streamed.map { it.id }).isEqualTo(settled.map { it.id })
        assertThat(streamed.map { it.id }.toSet()).hasSize(streamed.size)
    }
}
