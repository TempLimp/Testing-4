package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

class ShortsQueueHandoffTest {
    private val handoff = ShortsQueueHandoff()

    private fun video(
        id: String,
        isShort: Boolean = true,
    ) = Video(
        id = id,
        title = "t-$id",
        channelName = "c",
        channelId = "ch",
        thumbnailUrl = "",
        duration = 30,
        viewCount = 0,
        uploadDate = "",
        isShort = isShort,
    )

    @Test
    fun parkedListIsReadableByToken() {
        val token = handoff.offer(listOf(video("a")).map { it.toShortVideo() })

        assertEquals(listOf("a"), handoff.peek(token)?.map { it.id })
    }

    @Test
    fun peekDoesNotConsumeSoARecreatedScreenCanReadItAgain() {
        val token = handoff.offer(listOf(video("a")).map { it.toShortVideo() })

        handoff.peek(token)

        assertEquals(1, handoff.peek(token)?.size)
    }

    @Test
    fun unknownTokenReadsAsAbsent() {
        assertNull(handoff.peek("nope"))
    }

    @Test
    fun oldestEntriesAreEvictedSoTheRegistryCannotGrow() {
        val tokens = (1..8).map { handoff.offer(listOf(video("v$it").toShortVideo())) }

        assertNull("the first token should have been evicted", handoff.peek(tokens.first()))
        assertEquals(listOf("v8"), handoff.peek(tokens.last())?.map { it.id })
    }

    // ── sourceForShelf ──

    @Test
    fun shelfTapBecomesASnapshotAnchoredOnTheTappedShort() {
        val shelf = listOf(video("a"), video("b"), video("c"))

        val source = handoff.sourceForShelf(shelf, shelf[1])

        assertTrue(source is ShortsQueueSource.Snapshot)
        assertEquals("b", source.openAtVideoId)
        assertEquals(listOf("a", "b", "c"), handoff.peek((source as ShortsQueueSource.Snapshot).token)?.map { it.id })
    }

    @Test
    fun nonReelsAreDroppedFromTheQueuedShelf() {
        // A mixed shelf must not put a regular video into the vertical player.
        val shelf = listOf(video("a"), video("music", isShort = false), video("c"))

        val source = handoff.sourceForShelf(shelf, shelf[0]) as ShortsQueueSource.Snapshot

        assertEquals(listOf("a", "c"), handoff.peek(source.token)?.map { it.id })
    }

    @Test
    fun tappingANonReelFallsBackToSeedingTheFeed() {
        val shelf = listOf(video("a"), video("music", isShort = false))

        val source = handoff.sourceForShelf(shelf, shelf[1])

        assertEquals(ShortsQueueSource.SeededFeed("music"), source)
    }

    @Test
    fun clearEmptiesTheRegistry() {
        val token = handoff.offer(listOf(video("a").toShortVideo()))

        handoff.clear()

        assertNull(handoff.peek(token))
    }
}
