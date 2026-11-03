package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class SubscriptionFeedTimestampsTest {
    private val now = 1_791_331_200_000L
    private val hourMs = 3_600_000L
    private val dayMs = 24 * hourMs

    @Test
    fun aPluralAgeIsReadInItsOwnUnitNotAsSeconds() {
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("3 days ago", now)).isEqualTo(now - 3 * dayMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("2 hours ago", now)).isEqualTo(now - 2 * hourMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("5 years ago", now)).isEqualTo(now - 5 * 365 * dayMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("10 seconds ago", now)).isEqualTo(now - 10_000L)
    }

    @Test
    fun scheduledAndLiveTextKeepTheirBoosts() {
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("Premieres in 2 days", now)).isEqualTo(now + dayMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("Streaming live", now)).isEqualTo(now + hourMs)
        assertThat(SubscriptionFeedTimestamps.parseRelativeTime("unknown", now)).isNull()
    }

    @Test
    fun aPlaceholderTimestampNoLongerJumpsADaysOldUploadToTheTop() {
        val video =
            Video(
                id = "v1",
                title = "t",
                channelName = "c",
                channelId = "UCc",
                thumbnailUrl = "",
                duration = 0,
                viewCount = 0,
                uploadDate = "3 days ago",
                timestamp = now,
            )

        assertThat(SubscriptionFeedTimestamps.effectiveUploadTimestamp(video, now)).isEqualTo(now - 3 * dayMs)
    }
}
