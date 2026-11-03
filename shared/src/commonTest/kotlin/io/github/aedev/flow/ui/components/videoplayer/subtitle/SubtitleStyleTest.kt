package io.github.aedev.flow.ui.components.videoplayer.subtitle

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class SubtitleStyleTest {
    private val style = SubtitleStyle(bottomPadding = 40f, fullscreenBottomPadding = 120f, verticalFullscreenBottomPadding = 260f)

    @Test
    fun eachLayoutHasItsOwnPosition() {
        assertThat(style.bottomPaddingFor(isFullscreen = false, isVertical = true)).isEqualTo(40f)
        assertThat(style.bottomPaddingFor(isFullscreen = true, isVertical = false)).isEqualTo(120f)
        assertThat(style.bottomPaddingFor(isFullscreen = true, isVertical = true)).isEqualTo(260f)
    }
}
