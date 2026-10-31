package io.github.aedev.flow.player.resolver

/**
 * What the player opens for one resolution, in place of a Media3 `MediaSource`: an HLS master that
 * [LocalHlsProxy] serves, YouTube's own HLS manifest, or a progressive URL, which AVPlayer opens as an
 * AVURLAsset ([liveTargetOffsetMs] is AVPlayerItem's offset from the live edge); or, for the bundled
 * engine, the proxy's file URLs: [uri] the video (or the audio when there is none) and [audioUri] the
 * audio beside it, with [mimeType] the container of [uri].
 */
data class MediaSource(
    val uri: String,
    val mimeType: String?,
    val mediaId: String = "",
    val liveTargetOffsetMs: Long? = null,
    val engine: PlaybackEngineKind = PlaybackEngineKind.AVPLAYER,
    val audioUri: String? = null,
    val durationMs: Long = 0,
)

/**
 * Upstream's Media3 source factories. `buildDashSource` has no counterpart: AVPlayer reads no DASH,
 * so a stream that would have played through a generated DASH manifest is published to
 * [LocalHlsProxy] instead ([buildLocalHlsSource]).
 */
object MediaSourceBuilder {
    const val MIME_HLS = "application/x-mpegURL"

    suspend fun buildLocalHlsSource(
        presentation: HlsPresentation,
        mediaId: String = "",
    ): MediaSource = MediaSource(LocalHlsProxy.publish(presentation), MIME_HLS, mediaId)

    /** The same presentation for the bundled engine: one video rendition at most, read as files. */
    suspend fun buildBundledSource(
        presentation: HlsPresentation,
        mediaId: String = "",
    ): MediaSource {
        val files = LocalHlsProxy.publishFiles(presentation)
        val main = presentation.renditions.first()
        return MediaSource(
            uri = files.first(),
            mimeType = main.mimeType,
            mediaId = mediaId,
            engine = PlaybackEngineKind.BUNDLED,
            audioUri = files.getOrNull(1),
            durationMs = presentation.durationMs,
        )
    }

    fun buildProgressiveSource(
        uri: String,
        mimeType: String? = null,
        mediaId: String = "",
    ): MediaSource = MediaSource(uri, mimeType, mediaId)

    fun buildHlsSource(
        uri: String,
        mediaId: String = "",
        liveTargetOffsetMs: Long? = null,
    ): MediaSource = MediaSource(uri, MIME_HLS, mediaId, liveTargetOffsetMs)
}
