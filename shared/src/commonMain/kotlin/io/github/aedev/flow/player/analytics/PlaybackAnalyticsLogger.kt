package io.github.aedev.flow.player.analytics

import io.github.aedev.flow.player.engine.EnginePlayer
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.engine.VideoSize
import io.github.aedev.flow.player.error.PlayerDiagnostics

/**
 * Upstream's analytics log of the video format, the decoder and dropped frames, which a diagnostics report is read
 * for. Media3 reports them as analytics events; the port's player reports a new picture size, its first frame and
 * whether it plays, and the engine names its decoder and counts what it dropped, read at those moments.
 */
class PlaybackAnalyticsLogger(
    private val tag: String,
    private val player: () -> EnginePlayer?,
    private val videoIdProvider: () -> String? = { null },
) : FlowPlayer.Listener {
    private var activeDecoderName: String? = null
    private var activeFormatSummary: String = "unknown"
    private var droppedFramesSinceFormatChange: Int = 0
    private var droppedReported: Int = 0
    private var lastCheckMs: Long = 0

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        droppedFramesSinceFormatChange = 0
        activeFormatSummary = "${videoSize.width}x${videoSize.height}"
        PlayerDiagnostics.logInfo(tag, "Video format: videoId=${videoIdProvider() ?: "unknown"} $activeFormatSummary")
    }

    override fun onRenderedFirstFrame() {
        val engine = player()?.engine?.value ?: return
        droppedReported = engine.droppedFrameCount()
        val decoderName = engine.videoDecoderName() ?: return
        if (decoderName == activeDecoderName) return
        activeDecoderName = decoderName
        // AVPlayer decodes in hardware (VideoToolbox); FFmpeg and dav1d in the bundled engine are software.
        val isSoftware = decoderName != "AVPlayer"
        val message = "Video decoder: $decoderName (${if (isSoftware) "SOFTWARE" else "hardware"}) format=$activeFormatSummary"
        if (isSoftware) {
            PlayerDiagnostics.logWarning(tag, "⚠ SOFTWARE video decode — high battery cost. $message")
        } else {
            PlayerDiagnostics.logInfo(tag, message)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            lastCheckMs = io.github.aedev.flow.player.elapsedRealtime()
            return
        }
        val engine = player()?.engine?.value ?: return
        val dropped = engine.droppedFrameCount() - droppedReported
        if (dropped <= 0) return
        droppedReported += dropped
        droppedFramesSinceFormatChange += dropped
        val elapsedMs = io.github.aedev.flow.player.elapsedRealtime() - lastCheckMs
        val severity = if (dropped >= 5 || droppedFramesSinceFormatChange >= 20) "Dropped video frames" else "Minor video frame drops"
        PlayerDiagnostics.logWarning(
            tag,
            "$severity: +$dropped in ${elapsedMs}ms, total=$droppedFramesSinceFormatChange, " +
                "decoder=${activeDecoderName ?: "unknown"}, format=$activeFormatSummary, " +
                "videoId=${videoIdProvider() ?: "unknown"}",
        )
    }
}
