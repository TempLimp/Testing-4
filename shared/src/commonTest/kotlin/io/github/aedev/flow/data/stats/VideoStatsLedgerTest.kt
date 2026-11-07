package io.github.aedev.flow.data.stats

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class VideoStatsLedgerTest {
    private val zone: TimeZone = TimeZone.UTC
    private val now = at(2026, 9, 14, 21)

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
    ): Long = LocalDateTime(year, month, day, hour, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

    private fun view(
        videoId: String = "v1",
        channelId: String = "UCa",
        format: ViewFormat = ViewFormat.LONG,
        watchedMs: Long = 120_000L,
        counted: Boolean = true,
        skipped: Boolean = false,
    ) = ViewEvent(videoId, "Title $videoId", channelId, "Channel $channelId", format, watchedMs, counted, skipped)

    private fun VideoStatsLedger.record(
        event: ViewEvent,
        time: Long = now,
        topics: List<String> = listOf("space", "physics", "science", "extra"),
    ) = VideoStatsLedgerOps.recordView(this, time, event, topics, zone)

    private fun VideoStatsLedger.month(time: Long = now) = months.getValue(LedgerTime.at(time, zone).monthKey)

    @Test
    fun aCountedViewLandsInEveryAggregateOfItsMonth() {
        val ledger = VideoStatsLedger()
        ledger.record(view())

        val month = ledger.month()
        assertThat(month.views).isEqualTo(1)
        assertThat(month.sessions).isEqualTo(1)
        assertThat(month.watchedMs).isEqualTo(120_000L)
        assertThat(month.formatViews[ViewFormat.LONG]).isEqualTo(1)
        assertThat(month.channelViews["UCa"]).isEqualTo(1)
        assertThat(month.channelMs["UCa"]).isEqualTo(120_000L)
        assertThat(month.channelNames["UCa"]).isEqualTo("Channel UCa")
        assertThat(month.videoViews["v1"]).isEqualTo(1)
        assertThat(month.videoTitles["v1"]).isEqualTo("Title v1")
        assertThat(month.topicViews.keys).containsExactly("space", "physics", "science")
        assertThat(month.dayViews).containsExactly(14, 1)
        assertThat(month.dayMs).containsExactly(14, 120_000L)
        assertThat(month.hourViews).containsExactly(21, 1)
    }

    @Test
    fun aRewatchCountsAsASecondViewOfTheSameVideo() {
        val ledger = VideoStatsLedger()
        ledger.record(view())
        ledger.record(view())

        assertThat(ledger.month().videoViews["v1"]).isEqualTo(2)
        assertThat(ledger.month().channelViews["UCa"]).isEqualTo(2)
    }

    @Test
    fun aSkipAddsTimeAndNamesWhatWasPassedOverWithoutCountingAView() {
        val ledger = VideoStatsLedger()
        ledger.record(view(watchedMs = 15_000L, counted = false, skipped = true))

        val month = ledger.month()
        assertThat(month.views).isEqualTo(0)
        assertThat(month.watchedMs).isEqualTo(15_000L)
        assertThat(month.videoSkips["v1"]).isEqualTo(1)
        assertThat(month.channelSkips["UCa"]).isEqualTo(1)
        assertThat(month.videoTitles["v1"]).isEqualTo("Title v1")
        assertThat(month.topicViews).isEmpty()
    }

    @Test
    fun aSessionWithNoTimeAndNoViewLeavesNothing() {
        val ledger = VideoStatsLedger()
        ledger.record(view(watchedMs = 0L, counted = false))
        assertThat(ledger.months).isEmpty()
    }

    @Test
    fun formatsAreCountedApart() {
        val ledger = VideoStatsLedger()
        ledger.record(view(videoId = "s1", format = ViewFormat.SHORT, watchedMs = 20_000L))
        ledger.record(view(videoId = "l1", format = ViewFormat.LIVE, watchedMs = 600_000L))

        assertThat(ledger.month().formatViews).containsExactly(ViewFormat.SHORT, 1, ViewFormat.LIVE, 1)
        assertThat(ledger.month().formatMs).containsExactly(ViewFormat.SHORT, 20_000L, ViewFormat.LIVE, 600_000L)
    }

    @Test
    fun onlyChannelsNeverSeenBeforeAreDiscoveries() {
        val ledger = VideoStatsLedger()
        ledger.seedChannels.add("UCold")
        ledger.rebuildKnownChannels()

        ledger.record(view(channelId = "UCold"))
        ledger.record(view(videoId = "v2", channelId = "UCnew"))
        ledger.record(view(videoId = "v3", channelId = "UCnew"), time = at(2026, 10, 2, 9))

        assertThat(ledger.month().discoveredChannels).containsExactly("UCnew")
        assertThat(ledger.month(at(2026, 10, 2, 9)).discoveredChannels).isEmpty()
    }

    @Test
    fun knownChannelsSurviveARoundTripWithoutBeingStoredBeyondTheSeed() {
        val ledger = VideoStatsLedger()
        ledger.seedChannels.add("UCold")
        ledger.record(view(channelId = "UCnew"))

        val snapshot = ledger.toSnapshot()
        assertThat(snapshot.seedChannels).containsExactly("UCold")

        val restored = snapshot.toLedger()
        assertThat(restored.knownChannels).containsExactly("UCold", "UCnew")
        restored.record(view(videoId = "v9", channelId = "UCnew"))
        assertThat(restored.month().discoveredChannels).containsExactly("UCnew")
    }

    @Test
    fun viewsSplitAcrossCalendarMonths() {
        val ledger = VideoStatsLedger()
        ledger.record(view(), time = at(2026, 8, 31, 23))
        ledger.record(view(), time = at(2026, 9, 1, 0))

        assertThat(ledger.months.keys).containsExactly("2026-08", "2026-09")
    }

    @Test
    fun aDislikeIsKeptOnceWithItsDetailsAndCanBeTakenBack() {
        val ledger = VideoStatsLedger()
        val disliked = DislikedVideo("v1", "Bad take", "UCa", now)
        VideoStatsLedgerOps.recordDislike(ledger, now, disliked, zone)
        VideoStatsLedgerOps.recordDislike(ledger, now, disliked.copy(at = now + 1), zone)
        assertThat(ledger.month().dislikes).hasSize(1)

        VideoStatsLedgerOps.clearDislike(ledger, now, "v1", zone)
        assertThat(ledger.month().dislikes).isEmpty()
    }

    @Test
    fun searchesAlwaysCountButKeepTheirTextOnlyWhenGiven() {
        val ledger = VideoStatsLedger()
        VideoStatsLedgerOps.recordSearch(ledger, now, "  Black Holes ", zone)
        VideoStatsLedgerOps.recordSearch(ledger, now, null, zone)

        assertThat(ledger.month().actions[LedgerAction.SEARCH]).isEqualTo(2)
        assertThat(ledger.month().queries).containsExactly("black holes", 1)

        VideoStatsLedgerOps.clearQueries(ledger)
        assertThat(ledger.month().queries).isEmpty()
        assertThat(ledger.month().actions[LedgerAction.SEARCH]).isEqualTo(2)
    }

    @Test
    fun sponsorSkipsAddUpPerCategory() {
        val ledger = VideoStatsLedger()
        VideoStatsLedgerOps.recordSponsorSkip(ledger, now, "sponsor", 30_000L, zone)
        VideoStatsLedgerOps.recordSponsorSkip(ledger, now, "sponsor", 12_000L, zone)
        VideoStatsLedgerOps.recordSponsorSkip(ledger, now, "intro", 0L, zone)

        assertThat(ledger.month().sponsorSkippedMs).containsExactly("sponsor", 42_000L)
    }

    @Test
    fun perMonthCapsDropTheWeakestChannelsAndTheirNames() {
        val ledger = VideoStatsLedger()
        repeat(3) { ledger.record(view(videoId = "fav$it", channelId = "UCfav")) }
        (0 until VideoStatsParams.CHANNELS_PER_MONTH + 20).forEach { i ->
            ledger.record(view(videoId = "v$i", channelId = "UC$i"))
        }

        val month = ledger.month()
        assertThat(month.channelViews.size).isAtMost(VideoStatsParams.CHANNELS_PER_MONTH)
        assertThat(month.channelViews).containsKey("UCfav")
        assertThat(month.channelNames.keys).containsAtLeastElementsIn(month.channelViews.keys)
    }

    @Test
    fun onlyTheNewestMonthsAreKept() {
        val ledger = VideoStatsLedger()
        (0 until VideoStatsParams.MONTHS_MAX + 3).forEach { offset ->
            val time = at(2023 + offset / 12, offset % 12 + 1, 10, 12)
            ledger.record(view(videoId = "v$offset"), time = time)
        }
        assertThat(ledger.months.size).isEqualTo(VideoStatsParams.MONTHS_MAX)
        assertThat(ledger.months.keys.minOrNull()).isEqualTo("2023-04")
    }

    @Test
    fun theSnapshotRoundTripsEveryField() {
        val ledger = VideoStatsLedger()
        ledger.record(view())
        ledger.record(view(videoId = "s1", format = ViewFormat.SHORT, counted = false, skipped = true, watchedMs = 3_000L))
        VideoStatsLedgerOps.recordDislike(ledger, now, DislikedVideo("v2", "Nope", "UCb", now), zone)
        VideoStatsLedgerOps.recordAction(ledger, now, LedgerAction.SAVE, zone)
        VideoStatsLedgerOps.recordSponsorSkip(ledger, now, "sponsor", 5_000L, zone)

        val json = LedgerJson.encodeToString(VideoStatsSnapshot.serializer(), ledger.toSnapshot())
        val restored = LedgerJson.decodeFromString(VideoStatsSnapshot.serializer(), json)
        assertThat(restored).isEqualTo(ledger.toSnapshot())
    }
}
