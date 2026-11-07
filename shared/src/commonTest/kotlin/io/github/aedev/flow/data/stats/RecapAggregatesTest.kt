package io.github.aedev.flow.data.stats

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class RecapAggregatesTest {
    private val september = YearMonth(2026, 9)
    private val august = YearMonth(2026, 8)

    private fun videoMonth(
        views: Int = 0,
        watchedMs: Long = 0L,
        channelViews: Map<String, Int> = emptyMap(),
        topicViews: Map<String, Int> = emptyMap(),
        dayMs: Map<Int, Long> = emptyMap(),
        dayViews: Map<Int, Int> = emptyMap(),
        hourViews: Map<Int, Int> = emptyMap(),
        queries: Map<String, Int> = emptyMap(),
        discovered: List<String> = emptyList(),
        sponsor: Map<String, Long> = emptyMap(),
    ) = VideoMonthRecord(
        views = views,
        watchedMs = watchedMs,
        channelViews = channelViews,
        channelNames = channelViews.keys.associateWith { "Name $it" },
        topicViews = topicViews,
        dayMs = dayMs,
        dayViews = dayViews,
        hourViews = hourViews,
        queries = queries,
        discoveredChannels = discovered,
        sponsorSkippedMs = sponsor,
    )

    private fun video(vararg months: Pair<YearMonth, VideoMonthRecord>) =
        VideoStatsSnapshot(months = months.associate { LedgerTime.monthKey(it.first) to it.second })

    private fun music(vararg months: Pair<YearMonth, MusicStatsStorage.SerializableMonth>) =
        MusicStatsStorage.SerializableStats(months = months.associate { LedgerTime.monthKey(it.first) to it.second })

    @Test
    fun aMonthShowsOnlyItsOwnNumbersAndComparesWithTheMonthBefore() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(september to videoMonth(views = 5, watchedMs = 3_000_000L), august to videoMonth(views = 2, watchedMs = 1_000_000L)),
                music(september to MusicStatsStorage.SerializableMonth(plays = 4, listenedMs = 600_000L)),
            )

        assertThat(summary.video.views).isEqualTo(5)
        assertThat(summary.music.plays).isEqualTo(4)
        assertThat(summary.combined.totalMs).isEqualTo(3_600_000L)
        assertThat(summary.previousTotalMs).isEqualTo(1_000_000L)
    }

    @Test
    fun aFirstMonthHasNothingToCompareWith() {
        val summary = RecapAggregates.summarize(RecapPeriod.Month(september), video(september to videoMonth(views = 1)), music())
        assertThat(summary.previousTotalMs).isNull()
    }

    @Test
    fun aYearFoldsItsMonthsAndRanksAcrossThem() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Year(2026),
                video(
                    august to videoMonth(views = 3, channelViews = mapOf("UCa" to 3)),
                    september to videoMonth(views = 4, channelViews = mapOf("UCa" to 1, "UCb" to 3)),
                    YearMonth(2025, 12) to videoMonth(views = 9, channelViews = mapOf("UCz" to 9)),
                ),
                music(),
            )

        assertThat(summary.video.views).isEqualTo(7)
        assertThat(summary.video.topChannels.map { it.id to it.count }).containsExactly("UCa" to 4, "UCb" to 3).inOrder()
        assertThat(
            summary.video.topChannels
                .first()
                .name,
        ).isEqualTo("Name UCa")
    }

    @Test
    fun aStreakRunsAcrossTheEndOfAMonth() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Year(2026),
                video(
                    august to videoMonth(dayMs = mapOf(30 to 60_000L, 31 to 60_000L)),
                    september to videoMonth(dayMs = mapOf(1 to 60_000L, 2 to 60_000L, 5 to 60_000L)),
                ),
                music(),
            )

        assertThat(summary.combined.longestStreak).isEqualTo(4)
        assertThat(summary.combined.activeDays).isEqualTo(5)
    }

    @Test
    fun videoAndMusicDaysAddUpInTheCombinedCalendar() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(september to videoMonth(dayMs = mapOf(3 to 60_000L))),
                music(september to MusicStatsStorage.SerializableMonth(dayMs = mapOf(3 to 30_000L, 4 to 10_000L))),
            )

        assertThat(summary.combined.dayMs[LocalDate(2026, 9, 3)]).isEqualTo(90_000L)
        assertThat(summary.combined.busiestDay?.first).isEqualTo(LocalDate(2026, 9, 3))
    }

    @Test
    fun olderMusicMonthsSpreadTheirTimeOverTheDaysTheyHavePlaysOn() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(),
                music(september to MusicStatsStorage.SerializableMonth(plays = 4, listenedMs = 400_000L, dayPlays = mapOf(1 to 3, 2 to 1))),
            )

        assertThat(summary.music.activity.dayMs[LocalDate(2026, 9, 1)]).isEqualTo(300_000L)
        assertThat(summary.music.activity.dayMs[LocalDate(2026, 9, 2)]).isEqualTo(100_000L)
    }

    @Test
    fun topicsNewThisMonthAreTheTopOnesLastMonthNeverHad() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(
                    august to videoMonth(topicViews = mapOf("space" to 5)),
                    september to videoMonth(topicViews = mapOf("space" to 4, "cooking" to 2)),
                ),
                music(),
            )

        assertThat(summary.video.newTopics).containsExactly("cooking")
    }

    @Test
    fun searchTextsOlderThanTheHistorySOwnLimitAreLeftOut() {
        val snapshot =
            video(
                august to videoMonth(queries = mapOf("old" to 1)),
                september to videoMonth(queries = mapOf("new" to 1)),
            )

        val kept = RecapAggregates.summarize(RecapPeriod.Year(2026), snapshot, music(), queriesSince = september)
        assertThat(kept.video.topQueries.map { it.id }).containsExactly("new")
    }

    @Test
    fun traitsNeedEnoughActivityToBeClaimed() {
        val late = mapOf(23 to 15, 0 to 10)
        val quiet =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(september to videoMonth(hourViews = mapOf(23 to 2))),
                music(),
            )
        val owl = RecapAggregates.summarize(RecapPeriod.Month(september), video(september to videoMonth(hourViews = late)), music())

        assertThat(quiet.insights).doesNotContain(RecapInsight.NIGHT_OWL)
        assertThat(owl.insights).contains(RecapInsight.NIGHT_OWL)
    }

    @Test
    fun sponsorSkipsOfTenMinutesOrMoreEarnTheTimeSaverTrait() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(september to videoMonth(sponsor = mapOf("sponsor" to 400_000L, "intro" to 200_000L))),
                music(),
            )

        assertThat(summary.video.sponsorSavedMs).isEqualTo(600_000L)
        assertThat(summary.insights).contains(RecapInsight.TIME_SAVER)
    }

    @Test
    fun availableMonthsComeFromBothLedgersNewestFirst() {
        val months =
            RecapAggregates.availableMonths(
                video(august to videoMonth(views = 1)),
                music(
                    september to MusicStatsStorage.SerializableMonth(plays = 1),
                    august to MusicStatsStorage.SerializableMonth(plays = 1),
                ),
            )

        assertThat(months).containsExactly(september, august).inOrder()
    }

    @Test
    fun nothingRecordedIsAnEmptyRecap() {
        assertThat(RecapAggregates.summarize(RecapPeriod.AllTime, video(), music()).isEmpty).isTrue()
    }

    @Test
    fun rankedItemsCarryTheirPortraits() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(
                    september to
                        VideoMonthRecord(
                            views = 2,
                            channelViews = mapOf("UCa" to 2),
                            channelAvatars = mapOf("UCa" to "https://yt3/avatar"),
                            videoViews = mapOf("vid1" to 2),
                        ),
                ),
                music(
                    september to
                        MusicStatsStorage.SerializableMonth(
                            plays = 1,
                            artistPlays = mapOf("a1" to 1),
                            artistArt = mapOf("a1" to "https://art/a1"),
                            trackPlays = mapOf("t1" to 1),
                            trackArt = mapOf("t1" to "https://art/t1"),
                        ),
                ),
            )

        assertThat(
            summary.video.topChannels
                .single()
                .imageUrl,
        ).isEqualTo("https://yt3/avatar")
        assertThat(
            summary.video.topVideos
                .single()
                .imageUrl,
        ).contains("vid1")
        assertThat(
            summary.music.topArtists
                .single()
                .imageUrl,
        ).isEqualTo("https://art/a1")
        assertThat(
            summary.music.topTracks
                .single()
                .imageUrl,
        ).isEqualTo("https://art/t1")
    }

    @Test
    fun aMonthThatStraddlesPerDayMinutesKeepsItsOlderDays() {
        val month =
            MusicStatsStorage.SerializableMonth(
                plays = 6,
                listenedMs = 460_000L,
                dayPlays = mapOf(1 to 3, 2 to 1, 23 to 2),
                dayMs =
                    mapOf(23 to 60_000L),
            )
        val summary = RecapAggregates.summarize(RecapPeriod.Month(september), video(), music(september to month))

        assertThat(summary.music.activity.activeDays).isEqualTo(3)
        assertThat(summary.music.activity.dayMs[LocalDate(2026, 9, 1)]).isEqualTo(300_000L)
        assertThat(summary.music.activity.dayMs[LocalDate(2026, 9, 2)]).isEqualTo(100_000L)
        assertThat(summary.music.activity.dayMs[LocalDate(2026, 9, 23)]).isEqualTo(60_000L)
    }

    @Test
    fun aViewCountIsNeverListedAsAnArtist() {
        val month =
            MusicStatsStorage.SerializableMonth(
                plays = 3,
                artistPlays = mapOf("34m views" to 2, "a1" to 1),
                artistNames = mapOf("34m views" to "34M views", "a1" to "Real Artist"),
                discoveredArtists = listOf("34m views", "a1"),
            )
        val summary = RecapAggregates.summarize(RecapPeriod.Month(september), video(), music(september to month))

        assertThat(summary.music.topArtists.map { it.name }).containsExactly("Real Artist")
        assertThat(summary.music.discoveredArtists.map { it.name }).containsExactly("Real Artist")
    }

    @Test
    fun topAlbumsSumThePlaysOfEveryTrackPlacedOnAnAlbum() {
        val month =
            MusicStatsStorage.SerializableMonth(
                plays = 9,
                trackPlays = mapOf("a1" to 3, "a2" to 2, "b1" to 4, "loose" to 7),
            )
        val albums =
            mapOf(
                "a1" to TrackAlbum("MPREb_a", "Album A", "Artist", "art_a"),
                "a2" to TrackAlbum("MPREb_a", "Album A", "Artist", "art_a"),
                "b1" to TrackAlbum("MPREb_b", "Album B", "Other", "art_b"),
            )

        val recap =
            RecapAggregates
                .summarize(RecapPeriod.Month(september), VideoStatsSnapshot(), music(september to month), trackAlbums = albums)
                .music

        assertThat(recap.topAlbums.map { it.id to it.count }).containsExactly("MPREb_a" to 5, "MPREb_b" to 4).inOrder()
        assertThat(recap.topAlbums.first().kind).isEqualTo(RankedKind.ALBUM)
        assertThat(recap.topTracks.first().kind).isEqualTo(RankedKind.TRACK)
    }
}
