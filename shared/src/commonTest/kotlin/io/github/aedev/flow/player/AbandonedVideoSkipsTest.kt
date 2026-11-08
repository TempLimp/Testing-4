package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class AbandonedVideoSkipsTest {
    @Test
    fun aRefusedVideoInAQueueIsSkipped() {
        assertThat(AbandonedVideoSkips().trySkip(hasNextInQueue = true)).isTrue()
    }

    @Test
    fun theLastVideoInTheQueueIsNotSkipped() {
        assertThat(AbandonedVideoSkips().trySkip(hasNextInQueue = false)).isFalse()
    }

    @Test
    fun aRunOfRefusalsStopsTheQueueInsteadOfSkippingThroughIt() {
        val skips = AbandonedVideoSkips(limit = 2)

        assertThat(skips.trySkip(hasNextInQueue = true)).isTrue()
        assertThat(skips.trySkip(hasNextInQueue = true)).isTrue()
        assertThat(skips.trySkip(hasNextInQueue = true)).isFalse()
    }

    @Test
    fun aVideoThatPlaysStartsTheCountOver() {
        val skips = AbandonedVideoSkips(limit = 2)
        skips.trySkip(hasNextInQueue = true)
        skips.trySkip(hasNextInQueue = true)

        skips.onPlaybackStarted()

        assertThat(skips.trySkip(hasNextInQueue = true)).isTrue()
    }
}
