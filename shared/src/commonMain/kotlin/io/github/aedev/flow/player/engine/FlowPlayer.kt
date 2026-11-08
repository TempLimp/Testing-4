package io.github.aedev.flow.player.engine

/**
 * What the player manager and its helpers read from and do to the player, where upstream holds Media3's
 * `ExoPlayer`. Member names and the state and time constants keep Media3's, so upstream's logic ports line for
 * line; on iOS the implementation drives AVPlayer or the bundled engine ([PlaybackEngine] in iosMain), and tests
 * use fakes or mockk.
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

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun stop()

    fun clearMediaItems()

    companion object {
        // Media3's Player.STATE_* and C.TIME_UNSET values.
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
        const val TIME_UNSET = Long.MIN_VALUE + 1
    }
}

/** A decoded picture's size, as Media3's `VideoSize` reports it. */
data class VideoSize(
    val width: Int,
    val height: Int,
    val pixelWidthHeightRatio: Float = 1f,
)
