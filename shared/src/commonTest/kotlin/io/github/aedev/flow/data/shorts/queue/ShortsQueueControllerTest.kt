package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Clock

class ShortsQueueControllerTest {
    // Pinned: ShortVideo.timestamp defaults to Clock.System.now().toEpochMilliseconds(), so two fixtures for the
    // same id are unequal whenever they straddle a millisecond — which made the enrichment test fail
    // at random.
    private fun short(
        id: String,
        channelId: String = "ch",
    ) = ShortVideo(
        id = id,
        title = "t-$id",
        channelName = "c",
        channelId = channelId,
        thumbnailUrl = "https://i.ytimg.com/vi/$id/oar2.jpg",
        timestamp = 0L,
    )

    private fun shorts(vararg ids: String) = ids.map(::short)

    /** Serves pre-baked pages in order, then reports itself exhausted. */
    private class FakeLoader(
        private val pages: List<List<ShortVideo>>,
    ) : ShortsQueueLoader {
        var initialCalls = 0
            private set
        var moreCalls = 0
            private set

        override suspend fun initial(): ShortsQueuePage {
            initialCalls++
            return page(0)
        }

        override suspend fun more(cursor: String?): ShortsQueuePage {
            moreCalls++
            val next = (cursor?.toIntOrNull() ?: 0) + 1
            return page(next)
        }

        private fun page(index: Int): ShortsQueuePage {
            val items = pages.getOrNull(index).orEmpty()
            val isLast = index >= pages.lastIndex
            return ShortsQueuePage(
                items = items,
                cursor = if (isLast) null else index.toString(),
                exhausted = isLast,
            )
        }
    }

    private fun ids(controller: ShortsQueueController) = controller.items.value.map { it.id }

    // ── Opening position ──

    @Test
    fun opensAtTheTappedShortWithoutReorderingTheList() =
        runTest {
            // The shelf order has to survive so backward swipes still work.
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c", "d"))))

            controller.loadInitial(startVideoId = "c")

            assertEquals(listOf("a", "b", "c", "d"), ids(controller))
            assertEquals(2, controller.currentIndex.value)
            assertEquals("c", controller.currentItem?.id)
        }

    @Test
    fun opensAtTheTopWhenNoStartShortIsNamed() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))

            controller.loadInitial(startVideoId = null)

            assertEquals(0, controller.currentIndex.value)
        }

    // #1123: opening at the top played a short the user never tapped.
    @Test
    fun aStartShortTheSourceDoesNotHoldOpensAsAPlaceholder() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))

            controller.loadInitial(startVideoId = "missing")

            assertEquals(listOf("missing", "a", "b"), ids(controller))
            assertEquals("missing", controller.currentItem?.id)
            assertEquals("https://i.ytimg.com/vi/missing/oar2.jpg", controller.currentItem?.thumbnailUrl)
        }

    @Test
    fun anEmptySourceStillOpensOnTheTappedShort() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(emptyList())))

            controller.loadInitial(startVideoId = "tapped")

            assertEquals(listOf("tapped"), ids(controller))
            assertEquals(0, controller.currentIndex.value)
        }

    @Test
    fun theAnchorIsNeverSearchedForInAContinuation() =
        runTest {
            val feed = FakeLoader(listOf(shorts("f1", "f2")))
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a"))), continuations = listOf(feed))

            controller.loadInitial(startVideoId = "missing")

            assertEquals(0, feed.initialCalls)
            assertEquals("missing", controller.currentItem?.id)
        }

    @Test
    fun aPlaceholderAnchorIsNotAppendedAgainWhenALaterPageReturnsIt() =
        runTest {
            val feed = FakeLoader(listOf(shorts("missing", "f1")))
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a"))), continuations = listOf(feed))
            controller.loadInitial(startVideoId = "missing")

            controller.loadMore()

            assertEquals(listOf("missing", "a", "f1"), ids(controller))
        }

    // #1123: the tapped reel resolved to a blocked channel and was removed, so another played instead.
    @Test
    fun aResolveTimeFilterNeverRemovesTheShortTheQueueOpenedOn() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c"))))
            controller.loadInitial(startVideoId = "b")

            assertEquals(ShortsQueueChange.None, controller.dropFiltered("b"))
            assertEquals(ShortsQueueChange.ListOnly, controller.dropFiltered("c"))

            assertEquals(listOf("a", "b"), ids(controller))
            assertEquals("b", controller.currentItem?.id)
        }

    @Test
    fun aUserRemovalStillTakesTheShortTheQueueOpenedOn() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
            controller.loadInitial(startVideoId = "b")

            assertEquals(ShortsQueueChange.CurrentItemChanged, controller.remove("b"))
        }

    @Test
    fun duplicatesInTheFirstPageAreDropped() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "a"))))

            controller.loadInitial(null)

            assertEquals(listOf("a", "b"), ids(controller))
        }

    // ── Paging and the hand-over to the feed ──

    @Test
    fun finiteSourceWithNoContinuationStopsAtTheEnd() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
            controller.loadInitial(null)

            assertFalse(controller.hasMore)
            controller.loadMore()

            assertEquals(listOf("a", "b"), ids(controller))
        }

    @Test
    fun exhaustedShelfHandsOverToTheFeed() =
        runTest {
            val shelf = FakeLoader(listOf(shorts("s1", "s2")))
            val feed = FakeLoader(listOf(shorts("f1", "f2"), shorts("f3")))
            val controller = ShortsQueueController(primary = shelf, continuations = listOf(feed))

            controller.loadInitial(null)
            assertEquals(listOf("s1", "s2"), ids(controller))
            assertTrue("shelf is finite but a feed follows it", controller.hasMore)

            controller.loadMore()
            assertEquals(listOf("s1", "s2", "f1", "f2"), ids(controller))
            assertEquals("continuation must start with initial(), not more()", 1, feed.initialCalls)

            controller.loadMore()
            assertEquals(listOf("s1", "s2", "f1", "f2", "f3"), ids(controller))
            assertFalse(controller.hasMore)
        }

    @Test
    fun paginatedPrimaryIsDrainedBeforeTheContinuationIsTouched() =
        runTest {
            val primary = FakeLoader(listOf(shorts("p1"), shorts("p2")))
            val feed = FakeLoader(listOf(shorts("f1")))
            val controller = ShortsQueueController(primary, listOf(feed))

            controller.loadInitial(null)
            controller.loadMore()

            assertEquals(listOf("p1", "p2"), ids(controller))
            assertEquals(0, feed.initialCalls)

            controller.loadMore()
            assertEquals(listOf("p1", "p2", "f1"), ids(controller))
        }

    // The subscriptions queue: recent reels, then the subscribed channels' own older reels, and only
    // then anything algorithmic. Each leg has to be spent before the next is touched at all.
    @Test
    fun continuationsAreWalkedInOrderOneAtATime() =
        runTest {
            val recent = FakeLoader(listOf(shorts("r1")))
            val deep = FakeLoader(listOf(shorts("d1"), shorts("d2")))
            val feed = FakeLoader(listOf(shorts("f1")))
            val controller = ShortsQueueController(recent, listOf(deep, feed))

            controller.loadInitial(null)
            assertEquals(listOf("r1"), ids(controller))

            controller.loadMore()
            assertEquals(listOf("r1", "d1"), ids(controller))
            assertEquals("the feed must not be touched while the deep tier has pages", 0, feed.initialCalls)

            controller.loadMore()
            assertEquals(listOf("r1", "d1", "d2"), ids(controller))
            assertTrue(controller.hasMore)

            controller.loadMore()
            assertEquals(listOf("r1", "d1", "d2", "f1"), ids(controller))
            assertFalse(controller.hasMore)
        }

    @Test
    fun aQueueWithAGatedContinuationSwitchedOffEndsAtItsOwnLastShort() =
        runTest {
            val shelf = FakeLoader(listOf(shorts("s1")))
            val feed = FakeLoader(listOf(shorts("f1")))
            val controller = ShortsQueueController(shelf, listOf(GatedShortsLoader(enabled = { false }, delegate = feed)))

            controller.loadInitial(null)
            controller.loadMore()

            assertEquals(listOf("s1"), ids(controller))
            assertEquals(0, feed.initialCalls)
            assertFalse(controller.hasMore)
        }

    @Test
    fun appendNeverReAddsAnIdAlreadyInTheQueue() =
        runTest {
            val primary = FakeLoader(listOf(shorts("a"), shorts("a", "b")))
            val controller = ShortsQueueController(primary)

            controller.loadInitial(null)
            controller.loadMore()

            assertEquals(listOf("a", "b"), ids(controller))
        }

    @Test
    fun anAllDuplicatePageDoesNotEndPaging() =
        runTest {
            val primary = FakeLoader(listOf(shorts("a"), shorts("a"), shorts("b")))
            val controller = ShortsQueueController(primary)

            controller.loadInitial(null)
            controller.loadMore()

            assertEquals("should have skipped the duplicate page", listOf("a", "b"), ids(controller))
        }

    // ── Removal ──

    @Test
    fun removingTheCurrentShortReportsThatThePositionNowHoldsAnother() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c"))))
            controller.loadInitial(null)
            controller.setCurrentIndex(1)

            val change = controller.remove("b")

            assertEquals(ShortsQueueChange.CurrentItemChanged, change)
            assertEquals(listOf("a", "c"), ids(controller))
            assertEquals(1, controller.currentIndex.value)
            assertEquals("c", controller.currentItem?.id)
        }

    @Test
    fun removingAnotherShortLeavesTheCurrentOneAlone() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c"))))
            controller.loadInitial(null)
            controller.setCurrentIndex(1)

            val change = controller.remove("c")

            assertEquals(ShortsQueueChange.ListOnly, change)
            assertEquals("b", controller.currentItem?.id)
        }

    @Test
    fun removingTheLastShortClampsThePosition() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
            controller.loadInitial(null)
            controller.setCurrentIndex(1)

            controller.remove("b")

            assertEquals(0, controller.currentIndex.value)
            assertEquals("a", controller.currentItem?.id)
        }

    @Test
    fun aRemovedShortDoesNotComeBackOnTheNextAppend() =
        runTest {
            val primary = FakeLoader(listOf(shorts("a", "b"), shorts("b", "c")))
            val controller = ShortsQueueController(primary)
            controller.loadInitial(null)

            controller.remove("b")
            controller.loadMore()

            assertEquals(listOf("a", "c"), ids(controller))
        }

    @Test
    fun removingAChannelDropsEveryOneOfItsShortsAndKeepsThePositionOnTheSameShort() =
        runTest {
            val page = listOf(short("a", "UCx"), short("b"), short("c", "UCx"), short("d"), short("e", "UCx"))
            val controller = ShortsQueueController(FakeLoader(listOf(page)))
            controller.loadInitial(null)
            controller.setCurrentIndex(3)

            val change = controller.removeChannel("UCx")

            assertEquals(ShortsQueueChange.ListOnly, change)
            assertEquals(listOf("b", "d"), ids(controller))
            assertEquals("d", controller.currentItem?.id)
            assertEquals(1, controller.currentIndex.value)
        }

    @Test
    fun removingTheCurrentShortSChannelReportsThePositionChange() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(listOf(short("a"), short("b", "UCx"), short("c")))))
            controller.loadInitial(null)
            controller.setCurrentIndex(1)

            assertEquals(ShortsQueueChange.CurrentItemChanged, controller.removeChannel("UCx"))
            assertEquals("c", controller.currentItem?.id)
            assertEquals(ShortsQueueChange.None, controller.removeChannel(""))
        }

    @Test
    fun removingAnUnknownIdChangesNothing() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a"))))
            controller.loadInitial(null)

            assertEquals(ShortsQueueChange.None, controller.remove("nope"))
        }

    // ── Enrichment and discovery ──

    @Test
    fun enrichmentReplacesInPlace() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
            controller.loadInitial(null)

            val change = controller.applyEnrichment(listOf(short("a").copy(title = "enriched")))

            assertEquals(ShortsQueueChange.ListOnly, change)
            assertEquals(listOf("a", "b"), ids(controller))
            assertEquals(
                "enriched",
                controller.items.value
                    .first()
                    .title,
            )
        }

    @Test
    fun enrichmentThatChangesNothingIsReportedAsNoChange() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a"))))
            controller.loadInitial(null)

            assertEquals(ShortsQueueChange.None, controller.applyEnrichment(listOf(short("a"))))
        }

    @Test
    fun discoveryMergesAfterTheCurrentPositionAndNeverBeforeIt() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c"))), acceptsDiscovery = true)
            controller.loadInitial(null)
            controller.setCurrentIndex(1)

            controller.mergeDiscovery(shorts("d"))

            val result = ids(controller)
            assertEquals("watched items must not move", listOf("a", "b"), result.take(2))
            assertTrue("discovery item should be present", "d" in result)
        }

    @Test
    fun discoveryIgnoresIdsAlreadyQueued() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))), acceptsDiscovery = true)
            controller.loadInitial(null)

            assertEquals(ShortsQueueChange.None, controller.mergeDiscovery(shorts("a", "b")))
        }

    // Saved Shorts and a channel tab are not the feed. Background discovery finishes on its own
    // schedule, so without this guard a pass started on the Shorts tab lands in whatever is open.
    @Test
    fun discoveryIsRefusedByAQueueThatIsNotTheAlgorithmicFeed() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
            controller.loadInitial(null)

            assertEquals(ShortsQueueChange.None, controller.mergeDiscovery(shorts("x", "y")))
            assertEquals(listOf("a", "b"), ids(controller))
        }

    // ── Removal ──

    @Test
    fun removingAShortAboveTheCursorKeepsTheSameShortPlaying() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c", "d"))))
            controller.loadInitial(null)
            controller.setCurrentIndex(2)

            val change = controller.remove("a")

            assertEquals(ShortsQueueChange.ListOnly, change)
            assertEquals("c", controller.currentItem?.id)
        }

    @Test
    fun removingAShortBelowTheCursorLeavesThePositionAlone() =
        runTest {
            val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c", "d"))))
            controller.loadInitial(null)
            controller.setCurrentIndex(1)

            val change = controller.remove("d")

            assertEquals(ShortsQueueChange.ListOnly, change)
            assertEquals("b", controller.currentItem?.id)
        }

    // ── Start anchor on a later page ──

    @Test
    fun pagesForwardToFindAStartShortTheFirstPageDidNotContain() =
        runTest {
            // A channel's Shorts grid pages as the user scrolls, so the tap can be well past page one.
            val loader = FakeLoader(listOf(shorts("a", "b"), shorts("c", "d"), shorts("e", "f")))
            val controller = ShortsQueueController(loader)

            controller.loadInitial(startVideoId = "e")

            assertEquals(listOf("a", "b", "c", "d", "e", "f"), ids(controller))
            assertEquals("e", controller.currentItem?.id)
        }

    @Test
    fun givesUpPagingAndOpensOnAPlaceholderWhenTheStartShortNeverAppears() =
        runTest {
            val loader = FakeLoader(listOf(shorts("a"), shorts("b"), shorts("c"), shorts("d"), shorts("e")))
            val controller = ShortsQueueController(loader)

            controller.loadInitial(startVideoId = "zzz")

            assertEquals(3, loader.moreCalls)
            assertEquals(0, controller.currentIndex.value)
            assertEquals(listOf("zzz", "a", "b", "c", "d"), ids(controller))
        }
}
