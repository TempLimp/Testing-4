package io.github.aedev.flow.player

import io.github.aedev.flow.player.engine.FlowPlayer

/**
 * What the system's media controls show for the video player. Upstream's video MediaSession wraps the player in a
 * ForwardingPlayer whose metadata is the current video's and whose next and previous step the app's queue; on iOS the
 * controls are Now Playing (lock screen, Control Center) and the remote commands (headphones, Siri, CarPlay, accessories).
 */
internal data class VideoSessionSnapshot(
    val metadata: VideoSessionMetadata?,
    /** The player holds media: what upstream's service asks before it lets a media button start it. */
    val hasMediaItem: Boolean,
    val positionMs: Long,
    /** [FlowPlayer.TIME_UNSET] while unknown. */
    val durationMs: Long,
    val playbackSpeed: Float,
    val isPlaying: Boolean,
    val isLive: Boolean,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val controls: VideoSessionControls.State,
    val buttons: List<VideoSessionButtons.Button>,
)

/** What the system's media controls ask of the video player, as Media3's MediaSession hands it to its player. */
internal sealed interface VideoSessionCommand {
    data object Play : VideoSessionCommand

    data object Pause : VideoSessionCommand

    data object TogglePlayPause : VideoSessionCommand

    data object Next : VideoSessionCommand

    data object Previous : VideoSessionCommand

    data class SeekTo(
        val positionMs: Long,
    ) : VideoSessionCommand

    data class SetRepeat(
        val mode: VideoSessionControls.Repeat,
    ) : VideoSessionCommand

    data class SetShuffle(
        val enabled: Boolean,
    ) : VideoSessionCommand
}

/** The platform's media controls for the video session (iOS: `VideoPlaybackService`). Main thread. */
internal interface MediaSessionHost {
    /** Shows [snapshot]; called whenever what it shows may have changed. */
    fun update(snapshot: VideoSessionSnapshot)

    fun release()
}

/** Media3's `Util.shouldShowPlayButton` (play if suppressed): what its session's play/pause key does. */
internal fun FlowPlayer.shouldShowPlayButton(isSuppressed: Boolean): Boolean =
    !playWhenReady || playbackState == FlowPlayer.STATE_IDLE || playbackState == FlowPlayer.STATE_ENDED || isSuppressed

/** Media3's `Util.handlePlayButtonAction`: an idle player prepares and an ended one starts over before playing. */
internal fun FlowPlayer.handlePlayButtonAction() {
    when (playbackState) {
        FlowPlayer.STATE_IDLE -> prepare()
        FlowPlayer.STATE_ENDED -> seekToDefaultPosition()
    }
    play()
}
