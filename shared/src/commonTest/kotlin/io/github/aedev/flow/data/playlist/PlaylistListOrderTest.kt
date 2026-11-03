package io.github.aedev.flow.data.playlist

import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class PlaylistListOrderTest {
    private fun playlist(
        id: String,
        name: String = id,
        createdAt: Long = 0,
        position: Int = 0,
    ) = PlaylistInfo(
        id = id,
        name = name,
        description = "",
        videoCount = 0,
        thumbnailUrl = "",
        isPrivate = true,
        createdAt = createdAt,
        position = position,
    )

    private val playlists =
        listOf(
            playlist("a", name = "rock", createdAt = 1, position = 2),
            playlist("b", name = "Ambient", createdAt = 3, position = 1),
            playlist("c", name = "jazz", createdAt = 2, position = 3),
        )

    @Test
    fun newestAndOldestFollowWhenEachPlaylistWasMade() {
        assertThat(playlists.sortedFor(PlaylistListOrder.NEWEST).map { it.id }).containsExactly("b", "c", "a").inOrder()
        assertThat(playlists.sortedFor(PlaylistListOrder.OLDEST).map { it.id }).containsExactly("a", "c", "b").inOrder()
    }

    @Test
    fun nameOrderIgnoresLetterCase() {
        assertThat(playlists.sortedFor(PlaylistListOrder.NAME).map { it.name }).containsExactly("Ambient", "jazz", "rock").inOrder()
    }

    @Test
    fun customOrderFollowsPositionsAndPutsUnplacedPlaylistsFirstNewestLeading() {
        val withNew = playlists + playlist("new", createdAt = 10) + playlist("older", createdAt = 5)
        assertThat(withNew.sortedFor(PlaylistListOrder.CUSTOM).map { it.id })
            .containsExactly("new", "older", "b", "a", "c")
            .inOrder()
    }

    @Test
    fun anUnknownStoredValueFallsBackToNewest() {
        assertThat(PlaylistListOrder.fromStorageValue("bogus")).isEqualTo(PlaylistListOrder.NEWEST)
        assertThat(PlaylistListOrder.fromStorageValue("custom")).isEqualTo(PlaylistListOrder.CUSTOM)
    }
}
