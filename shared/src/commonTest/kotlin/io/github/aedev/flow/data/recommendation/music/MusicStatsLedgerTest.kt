/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class MusicStatsLedgerTest {
    private val now = 1_700_000_000_000L

    private fun record(
        ledger: MusicStatsLedger,
        at: Long = now,
        artistKey: String = "UCa",
        artistName: String = "Artist A",
        trackId: String = "t1",
        trackTitle: String = "Song One",
        genre: String? = null,
        listenedMs: Long = 60_000L,
        counted: Boolean = true,
        newArtist: Boolean = false,
        skipped: Boolean = false,
    ) = MusicStatsLedgerOps.record(
        ledger,
        at,
        artistKey,
        artistName,
        trackId,
        trackTitle,
        genre,
        listenedMs,
        counted,
        newArtist,
        skipped,
    )

    @Test
    fun countedPlayLandsInEveryAggregateOfItsMonth() {
        val ledger = MusicStatsLedger()
        record(ledger, genre = "pop", newArtist = true)

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.plays).isEqualTo(1)
        assertThat(month.sessions).isEqualTo(1)
        assertThat(month.listenedMs).isEqualTo(60_000L)
        assertThat(month.artistPlays["UCa"]).isEqualTo(1)
        assertThat(month.artistNames["UCa"]).isEqualTo("Artist A")
        assertThat(month.trackPlays["t1"]).isEqualTo(1)
        assertThat(month.trackTitles["t1"]).isEqualTo("Song One")
        assertThat(month.genrePlays["pop"]).isEqualTo(1)
        assertThat(month.discoveredArtists).containsExactly("UCa")
        assertThat(month.dayPlays.values.sum()).isEqualTo(1)
        assertThat(month.hourPlays.values.sum()).isEqualTo(1)
    }

    @Test
    fun partialSessionAddsMinutesAndASessionButNoPlay() {
        val ledger = MusicStatsLedger()
        record(ledger, listenedMs = 30_000L, counted = false)

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.plays).isEqualTo(0)
        assertThat(month.sessions).isEqualTo(1)
        assertThat(month.listenedMs).isEqualTo(30_000L)
        assertThat(month.artistPlays).isEmpty()
        assertThat(month.discoveredArtists).isEmpty()
    }

    @Test
    fun playsSplitAcrossCalendarMonths() {
        val ledger = MusicStatsLedger()
        val sixtyDays = 60L * 24 * 60 * 60 * 1000
        record(ledger, at = now)
        record(ledger, at = now - sixtyDays, trackId = "t2", trackTitle = "Song Two")

        assertThat(ledger.months).hasSize(2)
        assertThat(MusicStatsLedgerOps.monthKey(now)).isNotEqualTo(MusicStatsLedgerOps.monthKey(now - sixtyDays))
    }

    @Test
    fun perMonthArtistCapDropsTheWeakestAndTheirNames() {
        val ledger = MusicStatsLedger()
        record(ledger, artistKey = "UCstrong", artistName = "Strong", trackId = "s")
        record(ledger, artistKey = "UCstrong", artistName = "Strong", trackId = "s")
        (0 until MusicStatsParams.ARTISTS_PER_MONTH).forEach { i ->
            record(ledger, artistKey = "UCa$i", artistName = "A$i", trackId = "t$i")
        }

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.artistPlays.size).isAtMost(MusicStatsParams.ARTISTS_PER_MONTH)
        assertThat(month.artistPlays).containsKey("UCstrong")
        assertThat(month.artistNames.keys).isEqualTo(month.artistPlays.keys)
    }

    @Test
    fun ledgerSerializationRoundTrips() {
        val ledger = MusicStatsLedger()
        record(ledger, genre = "pop", newArtist = true)
        record(ledger, listenedMs = 10_000L, counted = false)

        val restored = ledger.toSerializable().toLedger()
        val key = MusicStatsLedgerOps.monthKey(now)
        assertThat(restored.months.getValue(key).plays).isEqualTo(1)
        assertThat(restored.months.getValue(key).sessions).isEqualTo(2)
        assertThat(restored.months.getValue(key).listenedMs).isEqualTo(70_000L)
        assertThat(restored.months.getValue(key).discoveredArtists).containsExactly("UCa")
        assertThat(restored.schemaVersion).isEqualTo(MusicStatsParams.SCHEMA_VERSION)
    }

    @Test
    fun aDeliberateEarlyExitIsANamedSkip() {
        val ledger = MusicStatsLedger()
        record(ledger, listenedMs = 12_000L, counted = false, skipped = true)

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.plays).isEqualTo(0)
        assertThat(month.trackSkips["t1"]).isEqualTo(1)
        assertThat(month.artistSkips["UCa"]).isEqualTo(1)
        assertThat(month.trackTitles["t1"]).isEqualTo("Song One")
    }

    @Test
    fun aMisTapUnderFiveSecondsIsNotASkip() {
        val ledger = MusicStatsLedger()
        record(ledger, listenedMs = 3_000L, counted = false, skipped = true)

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.trackSkips).isEmpty()
        assertThat(month.listenedMs).isEqualTo(3_000L)
    }

    @Test
    fun listeningTimeIsKeptPerDayPartialSessionsIncluded() {
        val ledger = MusicStatsLedger()
        record(ledger, listenedMs = 60_000L)
        record(ledger, listenedMs = 20_000L, counted = false)

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.dayMs.values.sum()).isEqualTo(80_000L)
    }

    @Test
    fun sayingNoToAnArtistIsRecordedWithANameAndADate() {
        val ledger = MusicStatsLedger()
        MusicStatsLedgerOps.recordSaidNo(ledger, now, "UCz", "Artist Z", blocked = false)
        MusicStatsLedgerOps.recordSaidNo(ledger, now, "UCy", "Artist Y", blocked = true)

        val month = ledger.months.getValue(MusicStatsLedgerOps.monthKey(now))
        assertThat(month.dislikedArtists).containsExactly("UCz", now)
        assertThat(month.blockedArtists).containsExactly("UCy", now)
        assertThat(month.artistNames).containsEntry("UCz", "Artist Z")
    }
}
