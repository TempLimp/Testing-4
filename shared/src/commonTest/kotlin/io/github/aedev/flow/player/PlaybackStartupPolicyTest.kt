package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class PlaybackStartupPolicyTest {
    @Test
    fun secondaryContentWaitsOnlyForMatchingActivePlayback() {
        assertThat(
            PlaybackStartupPolicy.shouldDelaySecondaryContent(
                isPlaybackLoading = true,
                currentVideoId = "current",
                requestedVideoId = "current",
            ),
        ).isTrue()
        assertThat(
            PlaybackStartupPolicy.shouldDelaySecondaryContent(
                isPlaybackLoading = true,
                currentVideoId = "current",
                requestedVideoId = "other",
            ),
        ).isFalse()
    }
}
