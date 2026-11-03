package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

class ShortsFeedAssemblyTest {
    private val quotas =
        mapOf(
            ShortsFeedLane.RELATED to 2,
            ShortsFeedLane.EXPLORE to 2,
            ShortsFeedLane.DISCOVERY to 1,
            ShortsFeedLane.SUBSCRIPTIONS to 1,
        )

    @Test
    fun watchedSuppressedAndExcludedReelsAreDroppedAndConsumed() {
        val pools =
            mapOf(
                lane(ShortsFeedLane.EXPLORE, reel("watched"), reel("suppressed"), reel("blocked", "UCbad"), reel("ok")),
            )
        val filters =
            ShortsFeedFilters(watchedIds = setOf("watched"), suppressedIds = setOf("suppressed"), excludedChannelIds = setOf("UCbad"))

        val assembly =
            assembleShortsPage(pools, quotas, targetSize = 4, filters = filters, usedIds = emptySet(), recentChannels = emptyList())

        assertEquals(listOf("ok"), assembly.page.map { it.short.id })
        assertEquals(setOf("watched", "suppressed", "blocked", "ok"), assembly.consumed)
    }

    @Test
    fun aTitledReelThatMatchesABlockedTopicIsDroppedAndAnUntitledOneIsLeftForResolveTime() {
        val pools =
            mapOf(
                lane(
                    ShortsFeedLane.DISCOVERY,
                    reel("cats").copy(title = "Cats being cats"),
                    reel("dogs").copy(title = "Dogs at the park"),
                    reel("untitled"),
                ),
            )
        val filters = ShortsFeedFilters(isBlockedText = { title, _ -> "cats" in title.lowercase() })

        val assembly =
            assembleShortsPage(pools, quotas, targetSize = 3, filters = filters, usedIds = emptySet(), recentChannels = emptyList())

        assertEquals(listOf("dogs", "untitled"), assembly.page.map { it.short.id })
        assertTrue("cats" in assembly.consumed)
    }

    @Test
    fun theSeenGateIsSkippedWhileThePoolsAreThin() {
        val pools = mapOf(lane(ShortsFeedLane.EXPLORE, reel("seen1"), reel("seen2"), reel("fresh")))
        val filters = ShortsFeedFilters(seenIds = setOf("seen1", "seen2"))

        val assembly =
            assembleShortsPage(pools, quotas, targetSize = 3, filters = filters, usedIds = emptySet(), recentChannels = emptyList())

        assertEquals(3, assembly.page.size)
    }

    @Test
    fun theSeenGateAppliesOnceThePoolsAreDeepAndLeavesEnough() {
        val reels = (1..30).map { reel("e$it") }
        val pools = mapOf(ShortsFeedLane.EXPLORE to reels.map { ShortsLaneItem(it, ShortsFeedLane.EXPLORE) })
        val filters = ShortsFeedFilters(seenIds = (1..12).map { "e$it" }.toSet())

        val assembly =
            assembleShortsPage(pools, quotas, targetSize = 6, filters = filters, usedIds = emptySet(), recentChannels = emptyList())

        assertTrue(assembly.page.none { it.short.id in filters.seenIds })
        assertTrue(assembly.consumed.containsAll(filters.seenIds))
    }

    @Test
    fun reelsAlreadyUsedNeverComeBack() {
        val pools = mapOf(lane(ShortsFeedLane.EXPLORE, reel("old"), reel("new")))

        val assembly =
            assembleShortsPage(
                pools,
                quotas,
                targetSize = 2,
                filters = ShortsFeedFilters(),
                usedIds = setOf("old"),
                recentChannels = emptyList(),
            )

        assertEquals(listOf("new"), assembly.page.map { it.short.id })
        assertFalse("old" in assembly.page.map { it.short.id })
    }
}
