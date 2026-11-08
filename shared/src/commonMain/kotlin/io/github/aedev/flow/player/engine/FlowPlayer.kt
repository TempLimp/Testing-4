package io.github.aedev.flow.player.engine

import io.github.aedev.flow.player.renderer.subtitle.Cue
import io.github.aedev.flow.player.renderer.subtitle.Subtitle
import io.github.aedev.flow.player.resolver.MediaSource

/**
 * What the player manager and its helpers read from and do to the player, where upstream holds Media3's
 * `ExoPlayer`. Member names, listener callbacks and the state, repeat, transition and time constants keep Media3's,
 * so upstream's logic ports line for line; on iOS [EnginePlayer] drives AVPlayer or the bundled engine, and tests
 * use fakes or mockk. Main thread only, as ExoPlayer is.
 */
interface FlowPlayer {
    /** One of [STATE_IDLE], [STATE_BUFFERING], [STATE_READY], [STATE_ENDED]. */
    val playbackState: Int

    var playWhenReady: Boolean

    /** Ready, asked to play, and not suppressed: the clock is moving. */
    val isPlaying: Boolean

    val currentPosition: Long

    val bufferedPosition: Long

    /** [TIME_UNSET] while unknown. */
    val duration: Long

    val mediaItemCount: Int

    val currentMediaItemIndex: Int

    /** The playing item's media id (the video id), or null with nothing loaded. */
    val currentMediaId: String?

    val isCurrentMediaItemLive: Boolean

    /** [REPEAT_MODE_OFF] or [REPEAT_MODE_ONE]. */
    var repeatMode: Int

    /** Speed with pitch kept, Media3's PlaybackParameters.speed. */
    var playbackSpeed: Float

    var volume: Float

    val videoSize: VideoSize

    /** The captions showing now. */
    val currentCues: List<Cue>

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** To the item's start, or a live stream's edge. */
    fun seekToDefaultPosition()

    fun stop()

    fun clearMediaItems()

    /** Replaces the playlist with [source]; it plays once [prepare] is called. */
    fun setMediaSource(source: LazyMediaSource)

    /** Appends [source] after the playing item, prepared ahead so the advance is gapless. */
    fun addMediaSource(source: LazyMediaSource)

    fun removeMediaItem(index: Int)

    /** Starts the item after the current one (prepared ahead by [addMediaSource]), as a SEEK transition. */
    fun seekToNextMediaItem()

    fun prepare()

    fun mute()

    fun unmute()

    /** The caption track to show, decoded; null for none. Upstream's text renderer has it from the media source. */
    fun setSubtitle(subtitle: Subtitle?)

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

    /** Media3's Player.Listener, the callbacks the manager uses. */
    interface Listener {
        fun onPlaybackStateChanged(playbackState: Int) {}

        fun onIsPlayingChanged(isPlaying: Boolean) {}

        fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {}

        fun onMediaItemTransition(
            mediaId: String?,
            reason: Int,
        ) {}

        fun onRenderedFirstFrame() {}

        fun onPlayerError(error: PlaybackException) {}

        fun onVideoSizeChanged(videoSize: VideoSize) {}

        fun onCues(cues: List<Cue>) {}
    }

    companion object {
        // Media3's Player.STATE_*, REPEAT_MODE_*, MEDIA_ITEM_TRANSITION_REASON_*, PLAY_WHEN_READY_CHANGE_REASON_* and C.TIME_UNSET values.
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
        const val TIME_UNSET = Long.MIN_VALUE + 1
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0
        const val MEDIA_ITEM_TRANSITION_REASON_AUTO = 1
        const val MEDIA_ITEM_TRANSITION_REASON_SEEK = 2
        const val MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3
        const val PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1
        const val PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM = 5
    }
}

/** A decoded picture's size, as Media3's `VideoSize` reports it. */
data class VideoSize(
    val width: Int,
    val height: Int,
    val pixelWidthHeightRatio: Float = 1f,
) {
    companion object {
        val UNKNOWN = VideoSize(0, 0)
    }
}

/**
 * A media source the player resolves when it prepares, as Media3 opens a source lazily: on iOS resolving publishes the
 * streams to the loopback proxy (or starts a SABR session), which can fail, and then the player reports a
 * [PlaybackException] as Media3 reports a source error. A SABR session started by resolving belongs to the
 * MediaLoader that built it, which ends it, as upstream's does.
 */
class LazyMediaSource(
    val mediaId: String,
    val isLive: Boolean = false,
    /** The last request the proxy saw refused for this source, made the cause of an IO error as Media3 makes it. */
    val failureCause: () -> Throwable? = { null },
    val resolve: suspend () -> MediaSource,
)

/**
 * Media3's `HttpDataSource.InvalidResponseCodeException`: a request answered with a non-2xx status. On iOS the proxy
 * sees googlevideo's answer, so it is the proxy that records one (with the URL it asked) for the player's error.
 */
class InvalidResponseCodeException(
    val responseCode: Int,
    val url: String,
) : Exception("Response code: $responseCode")

/** Media3's `ParserException`: a container or manifest the player could not read, the cause of a parsing error. */
class ParserException(
    message: String?,
) : Exception(message)
