package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicTrack
import kotlin.test.Test

class ScrobbleRulesTest {
    private fun track(
        id: String = "abc",
        artist: String = "Artist - Topic",
        artists: List<MusicArtist> = emptyList(),
        album: String = "Album",
    ) = MusicTrack(videoId = id, title = " Song ", artist = artist, thumbnailUrl = "", duration = 200, album = album, artists = artists)

    @Test
    fun aListenCountsAtHalfTheTrackOrFourMinutes() {
        assertThat(ScrobbleRules.counts(durationMs = 200_000, playedMs = 99_000)).isFalse()
        assertThat(ScrobbleRules.counts(durationMs = 200_000, playedMs = 100_000)).isTrue()
        assertThat(ScrobbleRules.counts(durationMs = 20 * 60_000, playedMs = 4 * 60_000)).isTrue()
        assertThat(ScrobbleRules.counts(durationMs = 30_000, playedMs = 30_000)).isFalse()
    }

    @Test
    fun theEntryTakesTheFirstCreditedArtistAndDropsTheTopicSuffix() {
        assertThat(ScrobbleRules.entryFor(track(), 200_000, 5_000_000)?.artist).isEqualTo("Artist")
        val credited = track(artists = listOf(MusicArtist("Lead"), MusicArtist("Guest")))
        val entry = ScrobbleRules.entryFor(credited, 200_000, 5_000_000)!!
        assertThat(entry.artist).isEqualTo("Lead")
        assertThat(entry.title).isEqualTo("Song")
        assertThat(entry.timestampSec).isEqualTo(5_000)
        assertThat(entry.durationSec).isEqualTo(200)
    }

    @Test
    fun placeholderAlbumsAreLeftOutAndDeviceFilesAreMarked() {
        val entry = ScrobbleRules.entryFor(track(id = "local_9", album = "Unknown Album"), 200_000, 0)!!
        assertThat(entry.album).isEmpty()
        assertThat(entry.fromYouTube).isFalse()
    }

    @Test
    fun nothingIsSentWithoutAnArtist() {
        assertThat(ScrobbleRules.entryFor(track(artist = " "), 200_000, 0)).isNull()
    }

    @Test
    fun aLoveUsesTheSameArtistAsTheScrobble() {
        assertThat(ScrobbleRules.loveFor(track(), loved = true)).isEqualTo(LoveEntry("Artist", "Song", loved = true))
    }

    @Test
    fun theThresholdIsHalfTheTrackCappedAtFourMinutesAndAbsentForShortTracks() {
        assertThat(ScrobbleRules.thresholdMs(200_000)).isEqualTo(100_000)
        assertThat(ScrobbleRules.thresholdMs(20 * 60_000)).isEqualTo(4 * 60_000)
        assertThat(ScrobbleRules.thresholdMs(30_000)).isNull()
    }
}
