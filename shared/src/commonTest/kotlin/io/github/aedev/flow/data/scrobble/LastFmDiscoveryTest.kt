package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class LastFmDiscoveryTest {
    @Test
    fun picksAlternateBetweenSeedsAndSkipTheSeedsThemselvesAndHiddenArtists() {
        val picks =
            discoveryPicks(
                similar =
                    listOf(
                        listOf("A" to "a1", "Seeded" to "Song", "A" to "a2"),
                        listOf("Hidden" to "h1", "B" to "b1", "a" to "A1"),
                    ),
                seeds = listOf("Seeded" to "Song"),
                hiddenArtists = setOf("hidden"),
                limit = 10,
            )

        assertThat(picks).containsExactly("A" to "a1", "B" to "b1", "A" to "a2").inOrder()
    }

    @Test
    fun theShelfStopsAtItsLimit() {
        val many = (1..30).map { "Artist $it" to "Song $it" }

        assertThat(discoveryPicks(listOf(many), emptyList(), emptySet(), limit = 12)).hasSize(12)
    }
}
