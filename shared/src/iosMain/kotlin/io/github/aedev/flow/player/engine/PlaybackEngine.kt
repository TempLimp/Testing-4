@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player.engine

import io.github.aedev.flow.player.resolver.MediaSource
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemAccessLogEvent
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.accessLog
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.presentationSize
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPictureInPictureController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.QuartzCore.CALayer

/**
 * The player contract both iOS engines sit behind: AVPlayer ([AvPlayerEngine]) for what it decodes, and the
 * bundled FFmpeg + dav1d engine (Swift, `iosApp/iosApp/Engine`) for VP9, AV1 without a hardware decoder and
 * Opus. [MediaSource.engine] says which one opens a source; S10's player manager drives either through this.
 * Calls may come from any thread except where noted.
 */
interface PlaybackEngine {
    /** What shows the video: an AVPlayerLayer or an AVSampleBufferDisplayLayer. Add it to a view on the main thread. */
    val layer: CALayer

    fun prepare(source: MediaSource)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun currentPositionMs(): Long

    /** 0 while unknown. */
    fun durationMs(): Long

    fun isPlaying(): Boolean

    /** Why playback failed, or null. */
    fun error(): String?

    fun stats(): PlaybackStats

    /** Picture in Picture over [layer], made on first use. Main thread only. */
    fun pictureInPictureController(): AVPictureInPictureController?

    /** Stops playback and frees the decoders; the engine is not used again. Named so: ObjC reserves release. */
    fun close()
}

/** What an engine measured since [PlaybackEngine.prepare]. The counters only grow. */
data class PlaybackStats(
    /** FFmpeg's decoder name ("vp9", "libdav1d", "opus", "aac"), or "AVPlayer"; null without that track. */
    val videoDecoder: String?,
    val audioDecoder: String?,
    /** The decoded picture, e.g. "3840x2160". */
    val videoSize: String?,
    val framesDecoded: Int,
    /** Frames handed to the display in time for it to show them. */
    val framesRendered: Int,
    /** Frames decoded too late to show (past the clock by more than the engine's tolerance), so never shown. */
    val framesDropped: Int,
    /** The furthest past its time a shown frame was handed to the display: its offset from the audio. */
    val maxVideoLateMs: Double,
    /** Audio handed to the renderer, in milliseconds of sound. */
    val audioQueuedMs: Long,
    /** Whether the audio renderer is playing. */
    val audioRendering: Boolean,
    /** Times the clock caught up with the queued audio while playing (a gap in the sound). */
    val audioUnderruns: Int,
    /** Where the first frame (or audio, with no video) handed over after the last seek starts; -1 before any. */
    val seekLandedMs: Long,
)

object PlaybackEngines {
    /** Set by the app at start-up (iOSApp.swift): the bundled engine is Swift. */
    var bundled: (() -> PlaybackEngine)? = null

    fun create(kind: PlaybackEngineKind): PlaybackEngine =
        when (kind) {
            PlaybackEngineKind.AVPLAYER -> AvPlayerEngine()
            PlaybackEngineKind.BUNDLED -> bundled?.invoke() ?: error("the app registered no bundled engine")
        }
}

/** AVPlayer behind [PlaybackEngine]: the sources [LocalHlsProxy][io.github.aedev.flow.player.resolver.LocalHlsProxy] serves as HLS. */
class AvPlayerEngine : PlaybackEngine {
    val player = AVPlayer()
    private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player)
    override val layer: CALayer get() = playerLayer

    /** The item playing, for what AVFoundation reports on it (tracks, access and error logs). */
    var item: AVPlayerItem? = null
        private set
    private var pictureInPicture: AVPictureInPictureController? = null

    override fun prepare(source: MediaSource) {
        val next = AVPlayerItem(asset = AVURLAsset(uRL = NSURL.URLWithString(source.uri)!!, options = null))
        item = next
        player.replaceCurrentItemWithPlayerItem(next)
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekToTime(CMTimeMake(positionMs, 1000), CMTimeMake(0, 1), CMTimeMake(0, 1))

    override fun currentPositionMs(): Long = (CMTimeGetSeconds(player.currentTime()) * 1000).toLong()

    override fun durationMs(): Long = item?.let { CMTimeGetSeconds(it.duration) * 1000 }?.takeIf { it.isFinite() }?.toLong() ?: 0

    override fun isPlaying(): Boolean = player.rate > 0f

    override fun error(): String? = item?.error?.localizedDescription

    override fun stats(): PlaybackStats {
        val access = item?.accessLog()?.events?.lastOrNull() as? AVPlayerItemAccessLogEvent
        val size = item?.presentationSize?.useContents { "${width.toInt()}x${height.toInt()}" }
        return PlaybackStats(
            videoDecoder = "AVPlayer",
            audioDecoder = "AVPlayer",
            videoSize = size,
            framesDecoded = 0,
            framesRendered = 0,
            framesDropped = access?.numberOfDroppedVideoFrames?.toInt() ?: 0,
            maxVideoLateMs = 0.0,
            audioQueuedMs = 0,
            audioRendering = isPlaying(),
            audioUnderruns = access?.numberOfStalls?.toInt() ?: 0,
            seekLandedMs = -1,
        )
    }

    override fun pictureInPictureController(): AVPictureInPictureController? =
        pictureInPicture ?: AVPictureInPictureController(playerLayer = playerLayer).also { pictureInPicture = it }

    override fun close() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        item = null
    }
}
