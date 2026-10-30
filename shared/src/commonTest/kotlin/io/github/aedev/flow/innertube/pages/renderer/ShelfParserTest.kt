package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.innertube.pages.SearchFixture
import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test

/**
 * Search puts shelves in two containers the channel never produced — `verticalListRenderer`
 * ("Latest from <creator>") and `gridShelfViewModel` (the inline Shorts strips).
 */
class ShelfParserTest {
    private fun feedEntries(name: String): List<JsonObject> =
        SearchFixture(name)["contents"]
            .objectOrNull()
            ?.get("twoColumnSearchResultsRenderer")
            .objectOrNull()
            ?.get("primaryContents")
            .objectOrNull()
            ?.get("sectionListRenderer")
            .objectOrNull()
            ?.get("contents")
            .arrayOrNull()
            .orEmpty()
            .flatMap { entry ->
                entry
                    .objectOrNull()
                    ?.get("itemSectionRenderer")
                    .objectOrNull()
                    ?.get("contents")
                    .arrayOrNull()
                    .orEmpty()
                    .mapNotNull { it.objectOrNull() }
            }

    private fun shelves(name: String): List<FeedShelf> =
        feedEntries(name).mapIndexedNotNull { index, entry -> entry.toFeedShelf(FeedItemOwner(), index) }

    @Test
    fun readsTheLatestFromShelfOutOfAVerticalListRenderer() {
        val shelf = shelves(SearchFixture.ALL_SAM_SULEK).first { it.style == FeedShelfStyle.List }

        assertThat(shelf.title).isEqualTo("Latest from Sam Sulek")
        assertThat(shelf.collapsedItemCount).isEqualTo(2)
        assertThat(shelf.items).isNotEmpty()
        assertThat(shelf.items.all { it is FeedItem.VideoItem }).isTrue()
    }

    @Test
    fun readsTheInlineShortsStripsOutOfAGridShelfViewModel() {
        val grids = shelves(SearchFixture.ALL_SAM_SULEK).filter { it.style == FeedShelfStyle.Grid }

        assertThat(grids).hasSize(2)
        assertThat(grids.map { it.title }.distinct()).containsExactly("Shorts")
        assertThat(grids.flatMap { it.items }.all { it is FeedItem.ShortItem }).isTrue()
    }

    @Test
    fun positionQualifiesTheIdSoSameTitledShelvesNeverCollide() {
        val ids = shelves(SearchFixture.ALL_SAM_SULEK).map { it.id }

        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun readsTheCommunityPostsShelfThroughTheExistingPostParser() {
        val shelf =
            shelves(SearchFixture.ALL_LINUS_TECH_TIPS)
                .first { it.items.any { item -> item is FeedItem.PostItem } }

        assertThat(shelf.title).isEqualTo("Latest posts from Linus Tech Tips")
        assertThat(shelf.style).isEqualTo(FeedShelfStyle.Carousel)
    }

    @Test
    fun aContainerTheRegistryCannotParseYieldsNoShelfRatherThanAnEmptyOne() {
        val shelf = JsonObject(emptyMap()).toFeedShelf(FeedItemOwner(), 0)

        assertThat(shelf).isNull()
    }
}
