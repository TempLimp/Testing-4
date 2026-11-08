package io.github.aedev.flow.player.analytics

import io.github.aedev.flow.player.engine.FlowPlayer

internal fun renderedVideoId(
    renderedMediaId: String?,
    currentVideoId: String?,
): String? = renderedMediaId?.takeIf { it.isNotBlank() } ?: currentVideoId

/**
 * Names the video whose first frame reached the screen. Upstream reads the window of Media3's analytics event, since
 * an appended window can render while the manager still names the old video; the port's player reports a frame once
 * its item is the current one, so [renderedMediaId] is that item's id.
 */
internal class RenderedFirstFrameListener(
    private val renderedMediaId: () -> String?,
    private val currentVideoId: () -> String?,
    private val onRendered: (videoId: String?) -> Unit,
) : FlowPlayer.Listener {
    override fun onRenderedFirstFrame() {
        onRendered(renderedVideoId(renderedMediaId(), currentVideoId()))
    }
}
