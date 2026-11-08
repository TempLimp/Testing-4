package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class PlaybackResumePolicyTest {
    @Test
    fun queueTransitionAlwaysStartsFromBeginning() {
        val startPosition = PlaybackResumePolicy.resolveStartPosition(
            savedPosition = 180_000L,
            durationMs = 240_000L,
            resumeAllowed = false
        )

        assertThat(startPosition).isEqualTo(0L)
    }

    @Test
    fun directPlaybackResumesAnUnfinishedVideo() {
        val startPosition = PlaybackResumePolicy.resolveStartPosition(
            savedPosition = 120_000L,
            durationMs = 240_000L,
            resumeAllowed = true
        )

        assertThat(startPosition).isEqualTo(120_000L)
    }

    @Test
    fun directPlaybackRestartsACompletedVideo() {
        val startPosition = PlaybackResumePolicy.resolveStartPosition(
            savedPosition = 239_000L,
            durationMs = 240_000L,
            resumeAllowed = true
        )

        assertThat(startPosition).isEqualTo(0L)
    }
}
