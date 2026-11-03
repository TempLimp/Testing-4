/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/** I-5: a Short's watch signal reflects what actually happened, not a fabricated full watch. */
class ShortWatchClassifierTest {

    @Test
    fun sub2sFlickIsASkipWithItsRealFraction() {
        val s = ShortWatchClassifier.classify(positionMs = 1_500L, durationMs = 30_000L, videoDurationSec = 30)
        assertThat(s.interaction).isEqualTo(InteractionType.SKIPPED)
        assertThat(s.percent).isWithin(1e-4f).of(0.05f)
        assertThat(s.position).isEqualTo(1_500L)
    }

    @Test
    fun aPartialWatchReportsItsRealFractionNot90Percent() {
        val s = ShortWatchClassifier.classify(positionMs = 9_000L, durationMs = 30_000L, videoDurationSec = 30)
        assertThat(s.interaction).isEqualTo(InteractionType.WATCHED)
        assertThat(s.percent).isWithin(1e-4f).of(0.30f)
    }

    @Test
    fun positionIsClampedToDuration() {
        val s = ShortWatchClassifier.classify(positionMs = 99_000L, durationMs = 30_000L, videoDurationSec = 30)
        assertThat(s.position).isEqualTo(30_000L)
        assertThat(s.percent).isEqualTo(1f)
    }

    @Test
    fun fallsBackToVideoDurationWhenPlayerDurationIsMissing() {
        val s = ShortWatchClassifier.classify(positionMs = 5_000L, durationMs = 0L, videoDurationSec = 20)
        assertThat(s.safeDuration).isEqualTo(20_000L)
        assertThat(s.interaction).isEqualTo(InteractionType.WATCHED)
    }
}
