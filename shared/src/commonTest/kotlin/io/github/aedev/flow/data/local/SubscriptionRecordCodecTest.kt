package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class SubscriptionRecordCodecTest {
    private val full =
        ChannelSubscription(
            channelId = "UCuAXFkgsw1L7xaCfnd5JJOw",
            channelName = "Foo | Bar",
            channelThumbnail = "https://yt3.ggpht.com/abc=s88",
            subscribedAt = 1_700_000_000_000L,
            lastVideoId = "dQw4w9WgXcQ",
            lastCheckTime = 1_700_000_100_000L,
            isNotificationEnabled = true,
            isMusic = true,
            lastFeedFetchAt = 1_700_000_200_000L,
        )

    private fun roundTrip(channel: ChannelSubscription) = SubscriptionRecordCodec.decode(SubscriptionRecordCodec.encode(channel))

    @Test
    fun awkwardNamesSurviveARoundTrip() {
        listOf(
            "Foo | Bar",
            "||",
            "Lo-fi 🎵 beats 🌙",
            "He said \"hi\" and 'bye'",
            "back\\slash\\",
            "100% real",
            "literal %7C and %25 and %",
            "%",
            "",
        ).forEach { name ->
            assertThat(roundTrip(full.copy(channelName = name))).isEqualTo(full.copy(channelName = name))
        }
    }

    @Test
    fun anEmptyThumbnailAndNoLastVideoSurviveARoundTrip() {
        val channel = full.copy(channelThumbnail = "", lastVideoId = null)

        assertThat(roundTrip(channel)).isEqualTo(channel)
    }

    @Test
    fun aPipeInAThumbnailIsWrittenAsItsURLEscape() {
        val decoded = roundTrip(full.copy(channelThumbnail = "https://t/a|b.jpg"))

        assertThat(decoded?.channelThumbnail).isEqualTo("https://t/a%7Cb.jpg")
        assertThat(decoded?.channelName).isEqualTo("Foo | Bar")
    }

    @Test
    fun aPlainRecordIsByteIdenticalToTheOldFormat() {
        val channel = full.copy(channelName = "Plain Channel")

        assertThat(SubscriptionRecordCodec.encode(channel)).isEqualTo(
            "UCuAXFkgsw1L7xaCfnd5JJOw|Plain Channel|https://yt3.ggpht.com/abc=s88|1700000000000|dQw4w9WgXcQ|" +
                "1700000100000|true|true|1700000200000",
        )
    }

    @Test
    fun anOlderVersionStillReadsEveryFieldOfAnEscapedRecord() {
        val parts = SubscriptionRecordCodec.encode(full).split("|")

        assertThat(parts).hasSize(9)
        assertThat(parts[1]).isEqualTo("Foo %7C Bar")
        assertThat(parts[3].toLong()).isEqualTo(full.subscribedAt)
    }

    @Test
    fun everyHistoricalLayoutReadsBack() {
        val base = "UC1|Name|https://t/1.jpg|100"
        assertThat(SubscriptionRecordCodec.decode(base)).isEqualTo(ChannelSubscription("UC1", "Name", "https://t/1.jpg", 100L))
        assertThat(SubscriptionRecordCodec.decode("$base|dQw4w9WgXcQ|200"))
            .isEqualTo(ChannelSubscription("UC1", "Name", "https://t/1.jpg", 100L, "dQw4w9WgXcQ", 200L))
        assertThat(SubscriptionRecordCodec.decode("$base||200|true")?.isNotificationEnabled).isTrue()
        assertThat(SubscriptionRecordCodec.decode("$base||200|false|true")?.isMusic).isTrue()
        assertThat(SubscriptionRecordCodec.decode("$base||200|false|true|300")?.lastFeedFetchAt).isEqualTo(300L)
    }

    @Test
    fun aPipeInALegacyNameIsRecoveredForEveryHistoricalLayout() {
        val name = "Lofi | Girl | Radio"
        val head = "UC1|$name|https://t/1.jpg|100"
        val expected = ChannelSubscription("UC1", name, "https://t/1.jpg", 100L)
        listOf(
            head to expected,
            "$head|dQw4w9WgXcQ|200" to expected.copy(lastVideoId = "dQw4w9WgXcQ", lastCheckTime = 200L),
            "$head||200|true" to expected.copy(lastCheckTime = 200L, isNotificationEnabled = true),
            "$head||200|false|true" to expected.copy(lastCheckTime = 200L, isMusic = true),
            "$head|dQw4w9WgXcQ|200|true|true|300" to
                expected.copy(
                    lastVideoId = "dQw4w9WgXcQ",
                    lastCheckTime = 200L,
                    isNotificationEnabled = true,
                    isMusic = true,
                    lastFeedFetchAt = 300L,
                ),
        ).forEach { (stored, channel) ->
            assertThat(SubscriptionRecordCodec.decode(stored)).isEqualTo(channel)
        }
    }

    @Test
    fun aPipeInALegacyNameWithNoThumbnailIsRecovered() {
        val decoded = SubscriptionRecordCodec.decode("UC1|Foo | Bar||100||200|false|false|0")

        assertThat(decoded?.channelName).isEqualTo("Foo | Bar")
        assertThat(decoded?.channelThumbnail).isEmpty()
        assertThat(decoded?.lastCheckTime).isEqualTo(200L)
    }

    @Test
    fun rowsThatAreNotSubscriptionsReadAsNothing() {
        listOf("", "corrupted|data|only", "|Name|https://t|100", "UC1|Name|https://t|notanumber").forEach {
            assertThat(SubscriptionRecordCodec.decode(it)).isNull()
        }
    }
}
