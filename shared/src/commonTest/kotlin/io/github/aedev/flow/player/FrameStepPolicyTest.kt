package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class FrameStepPolicyTest {
    @Test
    fun aFrameLastsTheReciprocalOfTheFrameRate() {
        assertThat(FrameStepPolicy.frameDurationMs(30f)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(60f)).isEqualTo(16L)
        assertThat(FrameStepPolicy.frameDurationMs(24f)).isEqualTo(41L)
    }

    @Test
    fun anUnusableFrameRateFallsBackRatherThanDividingByNothing() {
        assertThat(FrameStepPolicy.frameDurationMs(null)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(0f)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(-30f)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(Float.NaN)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(Float.POSITIVE_INFINITY)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(10_000f)).isEqualTo(33L)
    }

    @Test
    fun steppingForwardAdvancesOneFrame() {
        val target = FrameStepPolicy.stepTarget(positionMs = 1_000L, durationMs = 10_000L, frameRate = 30f, forward = true)

        assertThat(target).isEqualTo(1_033L)
    }

    @Test
    fun steppingBackRetreatsOneFrame() {
        val target = FrameStepPolicy.stepTarget(positionMs = 1_000L, durationMs = 10_000L, frameRate = 30f, forward = false)

        assertThat(target).isEqualTo(967L)
    }

    @Test
    fun steppingBackFromTheStartStaysPut() {
        val target = FrameStepPolicy.stepTarget(positionMs = 0L, durationMs = 10_000L, frameRate = 30f, forward = false)

        assertThat(target).isNull()
    }

    @Test
    fun steppingForwardFromTheEndStaysPut() {
        val target =
            FrameStepPolicy.stepTarget(positionMs = 10_000L, durationMs = 10_000L, frameRate = 30f, forward = true)

        assertThat(target).isNull()
    }

    @Test
    fun aStepNearTheEndClampsToTheDuration() {
        val target = FrameStepPolicy.stepTarget(positionMs = 9_990L, durationMs = 10_000L, frameRate = 30f, forward = true)

        assertThat(target).isEqualTo(10_000L)
    }

    @Test
    fun aStreamWithNoKnownDurationCannotBeStepped() {
        assertThat(FrameStepPolicy.stepTarget(1_000L, durationMs = 0L, frameRate = 30f, forward = true)).isNull()
        assertThat(FrameStepPolicy.stepTarget(1_000L, durationMs = -1L, frameRate = 30f, forward = true)).isNull()
    }

    @Test
    fun aHighFrameRateStillMovesThePlayhead() {
        val target = FrameStepPolicy.stepTarget(positionMs = 1_000L, durationMs = 10_000L, frameRate = 240f, forward = true)

        assertThat(target).isEqualTo(1_004L)
    }
}
