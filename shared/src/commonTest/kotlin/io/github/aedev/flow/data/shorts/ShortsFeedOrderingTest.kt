package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class ShortsFeedOrderingTest {
    @Test
    fun aReelThatResolvesIntoAChannelRunSwapsWithTheNextReelOfAnotherChannel() {
        // Channels: a a ? b, where "?" has just resolved to "a" and sits after the current reel.
        val queue = listOf("a1" to "a", "a2" to "a", "x" to "a", "b1" to "b", "c1" to "c")

        val result = deferChannelRuns(queue, changedIds = setOf("x"), currentIndex = 1, id = { it.first }, channelId = { it.second })

        assertThat(result.map { it.first }).containsExactly("a1", "a2", "b1", "x", "c1").inOrder()
    }

    @Test
    fun reelsAlreadyShownAndReelsWithoutAChannelNeverMove() {
        val queue = listOf("a1" to "a", "a2" to "a", "u" to "", "a3" to "a")

        val result = deferChannelRuns(queue, changedIds = setOf("a2", "u"), currentIndex = 2, id = { it.first }, channelId = { it.second })

        assertThat(result).isEqualTo(queue)
    }

    @Test
    fun discoveryMergeNeverMovesThePlayingItemOrConsumedPrefix() {
        val current = listOf("old1", "playing", "old2", "old3")

        val result = mergeDiscoveryCandidates(current, listOf("new1", "new2"), 1) { it }

        assertThat(result)
            .containsExactly(
                "old1",
                "playing",
                "new1",
                "old2",
                "new2",
                "old3",
            ).inOrder()
    }

    @Test
    fun aChannelSRunOfReelsIsSpreadAcrossRoundsWithoutLosingAny() {
        val feed = listOf("a1", "a2", "a3", "a4", "b1", "a5", "c1", "b2", "b3")

        val result = spreadChannels(feed, channelId = { it.take(1) })

        assertThat(result).containsExactly("a1", "a2", "b1", "c1", "b2", "a3", "a4", "b3", "a5").inOrder()
    }

    @Test
    fun aSliceWrapsRoundTheEndOfAShortList() {
        val reels = listOf("a", "b", "c", "d", "e")

        assertThat(reels.sliceFrom(offset = 0, count = 3)).containsExactly("a", "b", "c").inOrder()
        assertThat(reels.sliceFrom(offset = 3, count = 3)).containsExactly("d", "e", "a").inOrder()
        assertThat(listOf("a").sliceFrom(offset = 5, count = 3)).containsExactly("a")
        assertThat(emptyList<String>().sliceFrom(offset = 0, count = 3)).isEmpty()
    }

    @Test
    fun reelsWithoutAChannelAreNeverMoved() {
        val feed = listOf("a1", "?1", "a2", "a3", "?2")

        val result = spreadChannels(feed, channelId = { if (it.startsWith("?")) "" else it.take(1) })

        assertThat(result).containsExactly("a1", "?1", "a2", "?2", "a3").inOrder()
    }

    /** #931: the reel endpoint answers with what follows the tapped Short, never the Short itself. */
    @Test
    fun aSequenceWithoutItsSeedStillOpensOnTheSeed() {
        val result = openingOnSeed(listOf("next1", "next2"), seed = "tapped") { it }

        assertThat(result).containsExactly("tapped", "next1", "next2").inOrder()
    }

    /** The sequence's own copy carries real metadata; the seed handed in may be a bare placeholder. */
    @Test
    fun aSequenceCarryingItsSeedPrefersThatCopyAndDoesNotDuplicateIt() {
        val items = listOf("next1" to "Next", "tapped" to "Real title")

        val result = openingOnSeed(items, seed = "tapped" to "Short") { it.first }

        assertThat(result).containsExactly("tapped" to "Real title", "next1" to "Next").inOrder()
    }
}
