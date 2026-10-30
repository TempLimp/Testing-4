package io.github.aedev.flow.player.resolver

/**
 * What the player opens for one resolution, in place of a Media3 `MediaSource`: an HLS master that
 * [LocalHlsProxy] serves, YouTube's own HLS manifest, or a progressive URL. On iOS it becomes an
 * AVURLAsset; [liveTargetOffsetMs] is AVPlayerItem's offset from the live edge.
 */
data class MediaSource(
    val uri: String,
    val mimeType: String?,
    val mediaId: String = "",
    val liveTargetOffsetMs: Long? = null,
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
