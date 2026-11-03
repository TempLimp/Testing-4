package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class SeekbarPaddingPolicyTest {
    @Test
    fun portraitModeDefaultsToFullWidth() {
        assertEquals(
            SeekbarPaddingMode.FULL_WIDTH,
            resolvePortraitSeekbarPaddingMode(storedMode = null)
        )
    }

    @Test
    fun legacyPortraitDefaultMapsToSpaced() {
        assertEquals(
            SeekbarPaddingMode.SPACED,
            resolvePortraitSeekbarPaddingMode(SeekbarPaddingMode.DEFAULT.name)
        )
    }

    @Test
    fun fullWidthAlwaysResolvesToZero() {
        assertEquals(
            0,
            resolveSeekbarHorizontalPaddingDp(
                mode = SeekbarPaddingMode.FULL_WIDTH,
                customPaddingDp = 40,
                defaultPaddingDp = 16,
                maxPaddingDp = 64
            )
        )
    }

    @Test
    fun spacedModeUsesOrientationDefault() {
        assertEquals(
            16,
            resolveSeekbarHorizontalPaddingDp(
                mode = SeekbarPaddingMode.SPACED,
                customPaddingDp = 40,
                defaultPaddingDp = 16,
                maxPaddingDp = 64
            )
        )
    }

    @Test
    fun customModeClampsPaddingToOrientationMaximum() {
        assertEquals(
            64,
            resolveSeekbarHorizontalPaddingDp(
                mode = SeekbarPaddingMode.CUSTOM,
                customPaddingDp = 120,
                defaultPaddingDp = 16,
                maxPaddingDp = 64
            )
        )
    }
}
