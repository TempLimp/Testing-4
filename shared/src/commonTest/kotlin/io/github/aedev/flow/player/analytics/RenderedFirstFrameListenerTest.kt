package io.github.aedev.flow.player.analytics

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

// Upstream's cases, with the rendered window's media id given directly: the port's player names the item it rendered.
class RenderedFirstFrameListenerTest {
    @Test
    fun aFrameFromTheAppendedWindowNamesThatWindowsVideoWhileTheOldOneIsStillCurrent() {
        var rendered: String? = null
        val listener = RenderedFirstFrameListener(renderedMediaId = { "second" }, currentVideoId = { "first" }) { rendered = it }

        listener.onRenderedFirstFrame()

        assertThat(rendered).isEqualTo("second")
    }

    @Test
    fun aFrameFromTheCurrentWindowNamesTheCurrentVideo() {
        var rendered: String? = null
        val listener = RenderedFirstFrameListener(renderedMediaId = { "first" }, currentVideoId = { "first" }) { rendered = it }

        listener.onRenderedFirstFrame()

        assertThat(rendered).isEqualTo("first")
    }

    @Test
    fun anEmptyTimelineFallsBackToTheCurrentVideo() {
        var rendered: String? = null
        val listener = RenderedFirstFrameListener(renderedMediaId = { null }, currentVideoId = { "first" }) { rendered = it }

        listener.onRenderedFirstFrame()

        assertThat(rendered).isEqualTo("first")
    }

    @Test
    fun aWindowWithoutAMediaIdFallsBackToTheCurrentVideo() {
        assertThat(renderedVideoId("", "first")).isEqualTo("first")
        assertThat(renderedVideoId(null, "first")).isEqualTo("first")
        assertThat(renderedVideoId("second", "first")).isEqualTo("second")
    }
}
