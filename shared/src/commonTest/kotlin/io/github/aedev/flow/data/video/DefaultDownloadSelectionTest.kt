package io.github.aedev.flow.data.video

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class DefaultDownloadSelectionTest {
    @Test
    fun picksTheTallestQualityAtOrBelowTheDefault() {
        assertThat(DefaultDownloadSelection.pickHeight(listOf(2160, 1080, 720, 480), target = 1080)).isEqualTo(1080)
        assertThat(DefaultDownloadSelection.pickHeight(listOf(1440, 720, 360), target = 1080)).isEqualTo(720)
    }

    @Test
    fun fallsBackToTheSmallestQualityWhenAllAreAboveTheDefault() {
        assertThat(DefaultDownloadSelection.pickHeight(listOf(1080, 720), target = 480)).isEqualTo(720)
    }

    @Test
    fun noDefaultTakesTheTallestQuality() {
        assertThat(DefaultDownloadSelection.pickHeight(listOf(720, 2160, 1080), target = 0)).isEqualTo(2160)
    }

    @Test
    fun nothingToPickFromReturnsNull() {
        assertThat(DefaultDownloadSelection.pickHeight(emptyList(), target = 720)).isNull()
        assertThat(DefaultDownloadSelection.pickHeight(listOf(0), target = 720)).isNull()
    }

    @Test
    fun thePreferredCodecComesFirstThenTheDownloadPriority() {
        assertThat(DefaultDownloadSelection.rankCodecs(listOf("av1", "h264", "vp9"), preferred = "av1"))
            .containsExactly("av1", "vp9", "h264")
            .inOrder()
        assertThat(DefaultDownloadSelection.rankCodecs(listOf("av1", "h264", "vp9"), preferred = null))
            .containsExactly("vp9", "h264", "av1")
            .inOrder()
    }
}
