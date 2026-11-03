package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class SearchHistoryEntriesTest {
    private val filtered = SearchFilter(duration = Duration.OVER_20_MINUTES, uploadDate = UploadDate.TODAY)

    private fun entry(
        query: String,
        filters: SearchFilter? = null,
    ) = SearchHistoryItem(id = query, query = query, timestamp = 1L, filters = filters)

    @Test
    fun searchingTheSameWordsAgainReplacesTheEntryAndMovesItToTheTop() {
        val history = listOf(entry("cats"), entry("World news", SearchFilter.DEFAULT))

        val updated = history.withSearch("world news ", SearchType.TEXT, filtered, maxSize = 50, now = 2L)

        assertThat(updated.map { it.query }).containsExactly("world news ", "cats").inOrder()
        assertThat(updated.first().filters).isEqualTo(filtered)
    }

    @Test
    fun anEntrySavedBeforeFiltersWereKeptStaysAsItWas() {
        val updated = listOf(entry("cats")).withSearch("dogs", SearchType.TEXT, filtered, maxSize = 50, now = 2L)

        assertThat(updated.last()).isEqualTo(entry("cats"))
        assertThat(updated.last().filters).isNull()
    }

    @Test
    fun historyKeepsToItsSize() {
        val history = (1..5).map { entry("q$it") }

        assertThat(history.withSearch("new", SearchType.TEXT, null, maxSize = 3, now = 2L).map { it.query })
            .containsExactly("new", "q1", "q2")
            .inOrder()
    }

    @Test
    fun entriesStoredWithoutFiltersReadBackWithNone() {
        val json = """[{"id":"a","query":"cats","timestamp":1,"type":"TEXT"}]"""

        val read = GsonCompatJson.decodeFromString<List<SearchHistoryItem>>(json).map(SearchHistoryItem::sanitized)

        assertThat(read.single().filters).isNull()
    }

    @Test
    fun filtersSurviveARoundTripThroughTheStoredJson() {
        val withFeatures = filtered.copy(contentType = ContentType.VIDEOS, features = setOf(SearchFeature.FOUR_K))

        val json = GsonCompatJson.encodeToString(listOf(entry("world news", withFeatures)))
        val read = GsonCompatJson.decodeFromString<List<SearchHistoryItem>>(json).map(SearchHistoryItem::sanitized)

        assertThat(read.single().filters).isEqualTo(withFeatures)
    }

    @Test
    fun theWatchFilterSurvivesARoundTripAndOlderEntriesReadItAsAny() {
        val unwatched = filtered.copy(watchState = WatchState.UNWATCHED)

        val json = GsonCompatJson.encodeToString(listOf(entry("world news", unwatched)))
        val read = GsonCompatJson.decodeFromString<List<SearchHistoryItem>>(json).map(SearchHistoryItem::sanitized)
        val old =
            GsonCompatJson
                .decodeFromString<List<SearchHistoryItem>>(
                    """[{"id":"a","query":"cats","timestamp":1,"type":"TEXT","filters":{"uploadDate":"TODAY"}}]""",
                ).map(SearchHistoryItem::sanitized)

        assertThat(read.single().filters).isEqualTo(unwatched)
        assertThat(old.single().filters?.watchState).isEqualTo(WatchState.ANY)
    }

    @Test
    fun aFilterValueFromANewerVersionFallsBackToItsDefault() {
        val json =
            """[{"id":"a","query":"cats","timestamp":1,"type":"TEXT",""" +
                """"filters":{"contentType":"PODCASTS","duration":"OVER_20_MINUTES","uploadDate":"TODAY",""" +
                """"sortType":"RELEVANCE","features":["HD","SPATIAL_AUDIO"]}}]"""

        val read = GsonCompatJson.decodeFromString<List<SearchHistoryItem>>(json).map(SearchHistoryItem::sanitized)

        assertThat(read.single().filters)
            .isEqualTo(SearchFilter(duration = Duration.OVER_20_MINUTES, uploadDate = UploadDate.TODAY, features = setOf(SearchFeature.HD)))
    }

    @Test
    fun aStoredShortsSearchRunsOverEverythingOnceShortsAreHidden() {
        val shorts = SearchFilter(contentType = ContentType.SHORTS, uploadDate = UploadDate.TODAY)

        assertThat(shorts.availableWith(shortsEnabled = false)).isEqualTo(SearchFilter(uploadDate = UploadDate.TODAY))
        assertThat(shorts.availableWith(shortsEnabled = true)).isEqualTo(shorts)
    }

}
