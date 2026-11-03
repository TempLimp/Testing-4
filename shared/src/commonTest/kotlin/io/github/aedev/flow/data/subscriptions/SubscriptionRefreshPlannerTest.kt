package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

class SubscriptionRefreshPlannerTest {
    private val now = 1_700_000_000_000L
    private val ttl = SubscriptionRefreshPlanner.CHANNEL_FEED_TTL_MS

    private fun subscription(
        id: String,
        lastFeedFetchAt: Long = now,
        lastCheckTime: Long = 0L,
    ) = ChannelSubscription(
        channelId = id,
        channelName = "Channel $id",
        channelThumbnail = "",
        lastCheckTime = lastCheckTime,
        lastFeedFetchAt = lastFeedFetchAt,
    )

    @Test
    fun noSubscriptionsMeansNothingToDo() {
        val plan = SubscriptionRefreshPlanner.plan(emptyList(), now)

        assertTrue(plan.isEmpty)
        assertFalse(plan.isFullRefresh)
    }

    @Test
    fun freshChannelsAreSkipped() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("a"), subscription("b")),
                now,
            )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun onlyTheAgedOutChannelIsFetched() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(
                    subscription("fresh", lastFeedFetchAt = now - ttl / 2),
                    subscription("stale", lastFeedFetchAt = now - ttl - 1),
                ),
                now,
            )

        assertEquals(listOf("stale"), plan.channelIds)
        assertFalse(plan.isFullRefresh)
    }

    @Test
    fun aNeverFetchedChannelIsAlwaysIncluded() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("known"), subscription("brand-new", lastFeedFetchAt = 0L)),
                now,
            )

        assertEquals(listOf("brand-new"), plan.channelIds)
    }

    @Test
    fun aPendingUploadSignalBeatsTheTtl() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(
                    subscription("quiet", lastFeedFetchAt = now - 1000L),
                    subscription("posted", lastFeedFetchAt = now - 1000L, lastCheckTime = now - 500L),
                ),
                now,
            )

        assertEquals(listOf("posted"), plan.channelIds)
    }

    @Test
    fun anUploadSignalOlderThanTheLastFetchIsAlreadyCovered() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("covered", lastFeedFetchAt = now - 1000L, lastCheckTime = now - 5000L)),
                now,
            )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun aFetchTimestampInTheFutureIsTreatedAsStale() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("clock-skew", lastFeedFetchAt = now + 60_000L)),
                now,
            )

        assertEquals(listOf("clock-skew"), plan.channelIds)
    }

    @Test
    fun forceFetchesEveryChannelAndReportsAFullRefresh() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(subscription("a"), subscription("b")),
                now,
                force = true,
            )

        assertEquals(listOf("a", "b"), plan.channelIds)
        assertTrue(plan.isFullRefresh)
    }

    @Test
    fun everyChannelBeingStaleIsReportedAsAFullRefresh() {
        val plan =
            SubscriptionRefreshPlanner.plan(
                listOf(
                    subscription("a", lastFeedFetchAt = 0L),
                    subscription("b", lastFeedFetchAt = 0L),
                ),
                now,
            )

        assertEquals(listOf("a", "b"), plan.channelIds)
        assertTrue(plan.isFullRefresh)
    }

    @Test
    fun aGroupPlanCoversOnlyItsChannelsAndIsNeverFull() {
        val plan =
            SubscriptionRefreshPlanner.planForGroup(
                listOf(subscription("a"), subscription("b"), subscription("c")),
                groupChannelIds = setOf("a", "b"),
                now = now,
                force = true,
            )

        assertEquals(listOf("a", "b"), plan.channelIds)
        assertFalse(plan.isFullRefresh)
        assertEquals(setOf("a", "b"), plan.scope)
    }

    @Test
    fun aGroupWhoseChannelsAreAllStaleIsStillNotFull() {
        val plan =
            SubscriptionRefreshPlanner.planForGroup(
                listOf(subscription("a", lastFeedFetchAt = 0L), subscription("b")),
                groupChannelIds = setOf("a"),
                now = now,
            )

        assertEquals(listOf("a"), plan.channelIds)
        assertFalse(plan.isFullRefresh)
    }

    @Test
    fun aFreshGroupFetchesNothing() {
        val plan =
            SubscriptionRefreshPlanner.planForGroup(listOf(subscription("a")), groupChannelIds = setOf("a"), now = now)

        assertTrue(plan.isEmpty)
    }

    @Test
    fun aSmallGroupReadsEveryChannelSTabsAndALargeOneDoesNot() {
        val small = (1..SubscriptionRefreshPlanner.DEEP_READ_MAX_CHANNELS).map { subscription("s$it") }
        val large = (1..SubscriptionRefreshPlanner.DEEP_READ_MAX_CHANNELS + 1).map { subscription("l$it") }

        val smallPlan =
            SubscriptionRefreshPlanner.planForGroup(small, small.mapTo(HashSet()) { it.channelId }, now, force = true)
        val largePlan =
            SubscriptionRefreshPlanner.planForGroup(large, large.mapTo(HashSet()) { it.channelId }, now, force = true)

        assertTrue(smallPlan.readAllTabs)
        assertFalse(largePlan.readAllTabs)
    }

    @Test
    fun aPlanForEverySubscriptionReadsTabsOnlyWhereRSSFallsShort() {
        val plan = SubscriptionRefreshPlanner.plan(listOf(subscription("a")), now, force = true)

        assertFalse(plan.readAllTabs)
        assertEquals(null, plan.scope)
    }
}
