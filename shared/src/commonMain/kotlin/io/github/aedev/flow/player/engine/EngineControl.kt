package io.github.aedev.flow.player.engine

import io.github.aedev.flow.player.resolver.MediaSource

/**
 * What [EnginePlayer] drives on either iOS engine: AVPlayer (`AvPlayerEngine`) and the bundled FFmpeg + dav1d engine
 * (Swift). The iOS `PlaybackEngine` adds the layer, the counters and Picture in Picture. Calls may come from any
 * thread; each engine plays one source and is not used again after [close].
 */
interface EngineControl {
    fun prepare(source: MediaSource)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun currentPositionMs(): Long

    /** 0 while unknown. */
    fun durationMs(): Long

    /** How far media is loaded ahead of the clock, as a position. */
    fun bufferedPositionMs(): Long

    fun isPlaying(): Boolean

    /**
     * Media3's state for the source: [FlowPlayer.STATE_BUFFERING] while opening or waiting for media,
     * [FlowPlayer.STATE_READY] when it can play (paused or not), [FlowPlayer.STATE_ENDED] at its end, and
     * [FlowPlayer.STATE_IDLE] before [prepare] and after a failure.
     */
    fun playbackState(): Int

    /** Why playback failed, or null. */
    fun error(): String?

    /** The Media3 error code ([PlaybackException]) for [error]; [PlaybackException.ERROR_CODE_UNSPECIFIED] when unknown. */
    fun errorCode(): Int

    /** Speed, pitch kept: 1 is normal. Applies whenever the engine plays. */
    fun setPlaybackRate(rate: Float)

    /** 0 (silent) to 1. */
    fun setVolume(volume: Float)

    /**
     * Media3's SeekParameters for later seeks: exact lands on the frame asked for, otherwise on a nearby keyframe,
     * which is faster. The bundled engine always lands exactly (it decodes from the keyframe and shows nothing
     * before the target).
     */
    fun setSeekExact(exact: Boolean)

    /** A picture has been shown since [prepare]. */
    fun hasRenderedFirstFrame(): Boolean

    /** The decoded picture's size; 0 without video or before the first frame. */
    fun videoWidth(): Int

    fun videoHeight(): Int

    /** Stops playback and frees the decoders; the engine is not used again. Named so: ObjC reserves release. */
    fun close()
}
