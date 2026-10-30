package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * The destination live rows carry no `badges` array at all, so the thumbnail overlay style is the
 * only signal that survives a locale where "watching" is not the word. Their counts are concurrent
 * viewers, which a card would otherwise print as views.
 */
class ExploreLiveSignalsTest {
    private fun videos(title: String): List<Video> =
        ExploreFixture(ExploreFixture.DESTINATION_LIVE)
            .toSettledExploreDestinationPage()
            .shelves
            .first { it.title == title }
            .items
            .filterIsInstance<FeedItem.VideoItem>()
            .map { it.video }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aLiveRowIsMarkedLiveFromItsOverlayWithoutABadge() {
        assertThat(videos("Live Now").all { it.isLive }).isTrue()
    }

    /** A live card has no date, so its concurrent-viewer count is the only number it can show. */
    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too
    @Test
    fun aLiveRowKeepsItsConcurrentViewerCount() {
        assertThat(videos("Live Now").any { it.viewCount > 0L }).isTrue()
    }

    /** YouTube mixes already-started streams into its own "Upcoming" shelf, so the row decides, not the shelf. */
    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too
    @Test
    fun anUpcomingRowIsMarkedUpcomingAndCarriesItsScheduledStart() {
        val upcoming = videos("Upcoming Live Streams").filter { it.isUpcoming }

        assertThat(upcoming).isNotEmpty()
        assertThat(upcoming.all { it.timestamp > 0L }).isTrue()
        assertThat(upcoming.all { it.uploadDate.isNotEmpty() }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun anUpcomingRowsWaitingCountIsNeverReportedAsAViewCount() {
        assertThat(videos("Upcoming Live Streams").filter { it.isUpcoming }.all { it.viewCount == 0L }).isTrue()
    }

    /**
     * The destinations serve streams, so an upcoming row counts down like the player's rather than
     * reading as a premiere. Search and the channel tabs keep the wording they already had.
     */
    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too
    @Test
    fun anUpcomingRowOnADestinationIsMarkedAScheduledStream() {
        val upcoming = videos("Upcoming Live Streams").filter { it.isUpcoming }

        assertThat(upcoming).isNotEmpty()
        assertThat(upcoming.all { it.isScheduledLive }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun anOverlayAloneDoesNotMarkARowUpcoming() {
        val live = videos("Live Now")

        assertThat(live.none { it.isUpcoming }).isTrue()
        assertThat(live.none { it.isScheduledLive }).isTrue()
    }

    @Ignore // explore/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun aFinishedStreamKeepsItsDurationAndItsRealViewCount() {
        val recent = videos("Recent Live Streams")

        assertThat(recent).isNotEmpty()
        assertThat(recent.none { it.isLive || it.isUpcoming }).isTrue()
        assertThat(recent.any { it.duration > 0 }).isTrue()
        assertThat(recent.any { it.viewCount > 0L }).isTrue()
    }
}
