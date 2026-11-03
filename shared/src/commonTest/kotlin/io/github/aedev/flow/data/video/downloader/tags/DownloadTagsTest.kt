package io.github.aedev.flow.data.video.downloader.tags

import io.github.aedev.flow.testing.assertThat
import kotlinx.serialization.json.Json
import kotlin.test.Test

// The DownloadTags cases of upstream's DownloadTagsTest (S06 pulled DownloadTags.kt forward). S18 adds the four
// FlowTagFields cases with that file.
class DownloadTagsTest {
    private val full =
        DownloadTags(
            kind = DownloadKind.VIDEO,
            videoId = "abc123DEF45",
            title = "Title 🎵",
            artists = listOf("A, with comma", "B"),
            channelId = "UC123",
            channelName = "Channel",
            album = "Album",
            albumId = "MPREb",
            albumArtist = "A",
            trackNumber = 3,
            trackTotal = 12,
            playlistId = "PL1",
            releaseDate = "2024-02-29",
            description = "Line one\nLine two",
            sourceUrl = "https://www.youtube.com/watch?v=abc123DEF45",
            viewCount = 42L,
            likeCount = 7L,
            thumbnailUrl = "https://i.ytimg.com/vi/abc123DEF45/hqdefault.jpg",
            lyrics = "la la",
        )

    @Test
    fun jsonRoundTripKeepsEveryField() {
        val json = Json.encodeToString(DownloadTags.serializer(), full)
        assertThat(Json.decodeFromString(DownloadTags.serializer(), json)).isEqualTo(full)
    }

    @Test
    fun jsonWithOnlyRequiredFieldsDecodesWithDefaults() {
        val decoded = Json.decodeFromString(DownloadTags.serializer(), """{"kind":"SHORT","videoId":"id","title":"t"}""")
        assertThat(decoded).isEqualTo(DownloadTags(kind = DownloadKind.SHORT, videoId = "id", title = "t"))
        assertThat(decoded.schema).isEqualTo(DownloadTags.SCHEMA_VERSION)
    }

    @Test
    fun displayArtistJoinsDistinctCreditedArtists() {
        val tags = full.copy(artists = listOf(" Alice ", "bob", "Alice", "", "Bob"))
        assertThat(tags.displayArtist()).isEqualTo("Alice, bob")
    }

    @Test
    fun displayArtistFallsBackToTheChannelWithoutTheTopicSuffix() {
        val tags = full.copy(artists = emptyList(), channelName = "Queen - Topic")
        assertThat(tags.displayArtist()).isEqualTo("Queen")
        assertThat(tags.copy(channelName = null).displayArtist()).isNull()
        assertThat(tags.copy(channelName = " - Topic").displayArtist()).isNull()
    }
}
