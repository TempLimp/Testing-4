package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

class ShortsQueueSourceTest {
    private fun roundTrip(source: ShortsQueueSource) = ShortsQueueSource.decode(source.encode())

    @Test
    fun feedRoundTrips() {
        assertEquals(ShortsQueueSource.Feed, roundTrip(ShortsQueueSource.Feed))
    }

    @Test
    fun savedRoundTrips() {
        assertEquals(ShortsQueueSource.Saved(), roundTrip(ShortsQueueSource.Saved()))
    }

    @Test
    fun savedAnchoredOnAShortRoundTrips() {
        // Tapping a specific saved Short must open on it, not at the top of the collection.
        val source = ShortsQueueSource.Saved("abcdefghijk")
        assertEquals(source, roundTrip(source))
        assertEquals("abcdefghijk", source.openAtVideoId)
    }

    @Test
    fun subscriptionsRoundTrips() {
        val source = ShortsQueueSource.Subscriptions("abcdefghijk")
        assertEquals(source, roundTrip(source))
        assertEquals("abcdefghijk", source.openAtVideoId)
    }

    @Test
    fun subscriptionsWithoutAnAnchorRoundTrips() {
        assertEquals(ShortsQueueSource.Subscriptions(), roundTrip(ShortsQueueSource.Subscriptions()))
        assertEquals(null, ShortsQueueSource.Subscriptions().openAtVideoId)
    }

    @Test
    fun seededFeedRoundTrips() {
        val source = ShortsQueueSource.SeededFeed("abcdefghijk")
        assertEquals(source, roundTrip(source))
    }

    @Test
    fun snapshotRoundTrips() {
        val source = ShortsQueueSource.Snapshot(token = "tok123", startVideoId = "abcdefghijk")
        assertEquals(source, roundTrip(source))
    }

    @Test
    fun channelRoundTripsDespiteColonsInTheUrl() {
        // The channel URL carries its own scheme colon, so decoding must only consume the separators
        // it owns.
        val source =
            ShortsQueueSource.Channel(
                channelUrl = "https://www.youtube.com/channel/UCabcdefg",
                startVideoId = "abcdefghijk",
            )

        assertEquals(source, roundTrip(source))
    }

    // ── Degradation: a bad descriptor must never fail navigation ──

    @Test
    fun nullAndBlankDecodeToTheFeed() {
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode(null))
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode(""))
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode("   "))
    }

    @Test
    fun unknownKindDecodesToTheFeed() {
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode("wat:xyz"))
    }

    @Test
    fun truncatedSnapshotDecodesToTheFeed() {
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode("snap:"))
    }

    @Test
    fun channelWithoutAUrlDecodesToTheFeed() {
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode("channel:abcdefghijk"))
    }

    // ── Behavioural properties the controller depends on ──

    @Test
    fun onlyAnchoredSourcesReportAStartShort() {
        assertEquals("abcdefghijk", ShortsQueueSource.SeededFeed("abcdefghijk").openAtVideoId)
        assertEquals("abcdefghijk", ShortsQueueSource.Snapshot("t", "abcdefghijk").openAtVideoId)
        assertEquals("abcdefghijk", ShortsQueueSource.Channel("u", "abcdefghijk").openAtVideoId)
        assertEquals(null, ShortsQueueSource.Feed.openAtVideoId)
        assertEquals(null, ShortsQueueSource.Saved().openAtVideoId)
    }

    @Test
    fun blankStartShortReadsAsAbsent() {
        assertEquals(null, ShortsQueueSource.Snapshot("t", "").openAtVideoId)
    }

    @Test
    fun shelvesAndChannelTabsContinueIntoTheFeedSavedDoesNot() {
        assertTrue(ShortsQueueSource.Snapshot("t", "v").continuesIntoFeed)
        assertTrue(
            "the channel hands over only once it is exhausted, so a swipe never dead-ends",
            ShortsQueueSource.Channel("u", "v").continuesIntoFeed,
        )
        assertFalse("saved Shorts is a deliberate collection", ShortsQueueSource.Saved().continuesIntoFeed)
        assertFalse("the feed is already the feed", ShortsQueueSource.Feed.continuesIntoFeed)
    }

    // #823 is about the subscription list being long enough to swipe through, not about walling the
    // queue off: once it really is exhausted, handing over beats dead-ending.
    @Test
    fun subscriptionsContinuesIntoTheFeedOnlyOnceItIsExhausted() {
        assertTrue(ShortsQueueSource.Subscriptions("abcdefghijk").continuesIntoFeed)
        assertFalse(
            "its own items are the user's subscriptions, so discovery must not be interleaved",
            ShortsQueueSource.Subscriptions("abcdefghijk").isAlgorithmicFeed,
        )
    }

    // ── Channel sort ──

    @Test
    fun channelCarriesTheChosenSortThroughTheRoute() {
        val source =
            ShortsQueueSource.Channel(
                channelUrl = "https://www.youtube.com/channel/UCabcdefg",
                startVideoId = "abcdefghijk",
                sortIndex = 2,
            )

        assertEquals(source, roundTrip(source))
    }

    @Test
    fun channelDefaultsToTheFirstSort() {
        assertEquals(0, ShortsQueueSource.Channel("https://youtube.com/channel/UCx", "vid").sortIndex)
    }

    // A handle URL has no scheme colon to confuse the split, but it also has no sort segment if an
    // older descriptor somehow survives; falling back to 0 beats failing the navigation.
    @Test
    fun aChannelDescriptorWithAnUnparseableSortOpensAtTheDefaultOrder() {
        val decoded = ShortsQueueSource.decode("channel:vid:notanumber:https://www.youtube.com/@handle")

        assertEquals(
            ShortsQueueSource.Channel("https://www.youtube.com/@handle", "vid", 0),
            decoded,
        )
    }
}
