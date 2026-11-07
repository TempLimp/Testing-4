package io.github.aedev.flow.data.music

import io.github.aedev.flow.data.local.GsonCompatJson
import io.github.aedev.flow.testing.assertThat
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test

/** The port's own: upstream keeps the music playlists as Gson JSON in the `playlists` store, which the port reads and writes. */
class PlaylistJsonTest {
    private val playlists = ListSerializer(Playlist.serializer())

    // As Gson writes a Playlist: every field but a null one, and no computed trackCount or duration.
    private val gsonWritten =
        """[{"id":"p1","name":"Road","description":"","tracks":[{"videoId":"v1","title":"Song","artist":"Band",""" +
            """"thumbnailUrl":"https://i.ytimg.com/vi/v1/hq.jpg","duration":200}],"createdAt":1700000000000,""" +
            """"thumbnailUrl":"https://i.ytimg.com/vi/v1/hq.jpg"}]"""

    @Test
    fun aListGsonWroteReadsBack() {
        val playlist = GsonCompatJson.decodeFromString(playlists, gsonWritten).single()

        assertThat(playlist.name).isEqualTo("Road")
        assertThat(playlist.tracks.single().videoId).isEqualTo("v1")
        assertThat(playlist.customTrackCount).isNull()
        assertThat(playlist.trackCount).isEqualTo(1)
        assertThat(playlist.duration).isEqualTo(200)
    }

    @Test
    fun whatThePortWritesReadsBackTheSameAndCarriesNoComputedFields() {
        val playlist = GsonCompatJson.decodeFromString(playlists, gsonWritten)
        val written = GsonCompatJson.encodeToString(playlists, playlist)

        assertThat(GsonCompatJson.decodeFromString(playlists, written)).isEqualTo(playlist)
        assertThat(written).doesNotContain("trackCount")
    }
}
