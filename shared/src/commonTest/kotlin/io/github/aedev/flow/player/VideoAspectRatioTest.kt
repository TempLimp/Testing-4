package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class VideoAspectRatioTest {
    @Test
    fun ultrawideDisplayRatioIsNotLimitedToTwoToOne() {
        assertThat(sanitizeDisplayAspectRatio(2.35f)).isWithin(0.001f).of(2.35f)
    }

    @Test
    fun displayRatioPreservesSourceDimensionsWithoutAnArbitraryClamp() {
        assertThat(sanitizeDisplayAspectRatio(3.2f)).isWithin(0.001f).of(3.2f)
    }

    @Test
    fun highestResolutionDeterminesSourceRatioWhenLowRenditionIsRounded() {
        val dimensions = listOf(
            256 to 128,
            426 to 182,
            1920 to 818,
            3840 to 1636,
        )

        assertThat(sourceVideoAspectRatio(dimensions))
            .isWithin(0.0001f)
            .of(3840f / 1636f)
    }

    @Test
    fun invalidDisplayRatioFallsBackToWidescreen() {
        assertThat(sanitizeDisplayAspectRatio(Float.NaN))
            .isWithin(0.001f)
            .of(DEFAULT_VIDEO_ASPECT_RATIO)
    }

    @Test
    fun pipRatioStaysInsidePlatformUltrawideLimit() {
        assertThat(sanitizePipAspectRatio(2.5f)).isWithin(0.001f).of(2.39f)
    }

    @Test
    fun pipPreservesSupportedUltrawideRatio() {
        assertThat(sanitizePipAspectRatio(2.35f)).isWithin(0.001f).of(2.35f)
    }
}
