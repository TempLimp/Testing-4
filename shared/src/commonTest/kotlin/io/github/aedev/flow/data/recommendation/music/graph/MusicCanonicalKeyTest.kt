package io.github.aedev.flow.data.recommendation.music.graph

import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.testing.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Test

class MusicCanonicalKeyTest {
    @Test
    fun remasterLiveAndParentheticalVariantsCollapseOntoTheStudioKey() {
        val studio = canonicalTrackKey("UCqueen", "Bohemian Rhapsody", 355)
        assertEquals(studio, canonicalTrackKey("UCqueen", "Bohemian Rhapsody (Remastered 2011)", 357))
        assertEquals(studio, canonicalTrackKey("UCqueen", "Bohemian Rhapsody [Live]", 351))
        assertEquals(studio, canonicalTrackKey("UCqueen", "Bohemian Rhapsody - 2011 Remaster", 354))
    }

    @Test
    fun differentArtistsOrClearlyDifferentLengthsKeepSeparateKeys() {
        val studio = canonicalTrackKey("UCqueen", "Bohemian Rhapsody", 355)
        assertNotEquals(studio, canonicalTrackKey("UCmuppets", "Bohemian Rhapsody", 355))
        assertNotEquals(studio, canonicalTrackKey("UCqueen", "Bohemian Rhapsody", 420))
    }

    @Test
    fun trackKeyPrefersTheArtistBrowseIdAndFallsBackToTheLowercasedName() {
        val withId =
            MusicTrack(
                videoId = "a",
                title = "Song",
                artist = "Queen",
                thumbnailUrl = "",
                duration = 200,
                artists = listOf(MusicArtist("Queen", "UCqueen")),
            )
        val withoutId = withId.copy(artists = emptyList(), channelId = "")
        assertEquals("ucqueen|song|20", withId.canonicalKey())
        assertEquals("queen|song|20", withoutId.canonicalKey())
    }
}
