package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class ShortsFeedQuotasTest {
    @Test
    fun aMatureAccountWithSubscriptionsLeansOnRelatedReelsAndCapsSubscriptions() {
        val quotas = shortsFeedQuotas(16, ShortsFeedProfile(subscribedChannelIds = setOf("UCa"), isColdStart = false))

        assertEquals(5, quotas[ShortsFeedLane.RELATED])
        assertEquals(3, quotas[ShortsFeedLane.DISCOVERY])
        assertEquals(2, quotas[ShortsFeedLane.SUBSCRIPTIONS])
        assertEquals(6, quotas[ShortsFeedLane.EXPLORE])
        assertEquals(16, quotas.values.sum())
    }

    @Test
    fun noSubscriptionsMeansNoSubscriptionSlots() {
        val quotas = shortsFeedQuotas(16, ShortsFeedProfile(subscribedChannelIds = emptySet(), isColdStart = false))

        assertEquals(0, quotas[ShortsFeedLane.SUBSCRIPTIONS])
        assertEquals(16, quotas.values.sum())
    }

    @Test
    fun aColdStartLeansOnTheExploreChain() {
        val quotas = shortsFeedQuotas(16, ShortsFeedProfile(subscribedChannelIds = emptySet(), isColdStart = true))

        assertEquals(2, quotas[ShortsFeedLane.RELATED])
        assertEquals(4, quotas[ShortsFeedLane.DISCOVERY])
        assertEquals(10, quotas[ShortsFeedLane.EXPLORE])
        assertEquals(16, quotas.values.sum())
    }

    @Test
    fun noSlotsMeansNoQuotas() {
        assertEquals(0, shortsFeedQuotas(0, ShortsFeedProfile(setOf("UCa"), false)).values.sum())
    }
}
