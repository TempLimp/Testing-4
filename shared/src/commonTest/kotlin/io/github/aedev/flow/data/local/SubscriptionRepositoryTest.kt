package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * Upstream reaches SubscriptionRepository's private `serializeChannel`/`deserializeChannel` through Java reflection,
 * which Kotlin/Native lacks; both are one-line calls of [SubscriptionRecordCodec], so the cases call it directly.
 */
class SubscriptionRepositoryTest {
    @Test
    fun serializeAndDeserializeChannelSubscriptionCorrectly() {
        val subscription =
            ChannelSubscription(
                channelId = "UC123",
                channelName = "Test Channel",
                channelThumbnail = "thumb_url",
                subscribedAt = 123456789L,
                lastVideoId = "vid99",
                lastCheckTime = 987654321L,
            )

        val serialized = SubscriptionRecordCodec.encode(subscription)

        assertThat(serialized).contains("UC123")
        assertThat(serialized).contains("Test Channel")
        assertThat(serialized).contains("vid99")

        val deserialized = SubscriptionRecordCodec.decode(serialized)

        assertThat(deserialized).isNotNull()
        assertThat(deserialized?.channelId).isEqualTo(subscription.channelId)
        assertThat(deserialized?.channelName).isEqualTo(subscription.channelName)
        assertThat(deserialized?.lastVideoId).isEqualTo(subscription.lastVideoId)
        assertThat(deserialized?.lastCheckTime).isEqualTo(subscription.lastCheckTime)
    }

    @Test
    fun deserializeChannelHandlesCorruptedDataGracefully() {
        val result = SubscriptionRecordCodec.decode("corrupted|data|only")

        assertThat(result).isNull()
    }
}
