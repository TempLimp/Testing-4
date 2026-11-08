package io.github.aedev.flow.player

import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class PlayerSeekPolicyTest {
    @Test
    fun seekAtDurationIsAnEndBoundarySeek() {
        assertThat(isEndBoundarySeek(requestedPositionMs = 120_000L, durationMs = 120_000L)).isTrue()
    }

    @Test
    fun seekBeforeDurationIsNotAnEndBoundarySeek() {
        assertThat(isEndBoundarySeek(requestedPositionMs = 119_999L, durationMs = 120_000L)).isFalse()
    }

    @Test
    fun unknownDurationIsNotAnEndBoundarySeek() {
        assertThat(isEndBoundarySeek(requestedPositionMs = 120_000L, durationMs = FlowPlayer.TIME_UNSET)).isFalse()
    }
}
