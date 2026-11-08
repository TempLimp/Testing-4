@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player.engine

import io.github.aedev.flow.player.resolver.MediaSource
import kotlin.concurrent.Volatile
import platform.AVFoundation.AVAudioTimePitchAlgorithmSpectral
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVPlayerItemTrack
import platform.AVFoundation.mediaType
import platform.AVFoundation.preferredForwardBufferDuration
import platform.AVFoundation.preferredMaximumResolution
import platform.AVFoundation.setPreferredForwardBufferDuration
import platform.AVFoundation.preferredPeakBitRate
import platform.AVFoundation.setPreferredMaximumResolution
import platform.AVFoundation.setPreferredPeakBitRate
import platform.AVFoundation.tracks
import platform.CoreGraphics.CGSizeMake
import platform.AVFoundation.AVErrorContentIsUnavailable
import platform.AVFoundation.AVErrorDecodeFailed
import platform.AVFoundation.AVErrorDecoderNotFound
import platform.AVFoundation.AVErrorDecoderTemporarilyUnavailable
import platform.AVFoundation.AVErrorFileFormatNotRecognized
import platform.AVFoundation.AVErrorFormatUnsupported
import platform.AVFoundation.AVErrorMediaServicesWereReset
import platform.AVFoundation.AVErrorNoLongerPlayable
import platform.AVFoundation.AVFoundationErrorDomain
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.CMTimeRangeValue
import platform.AVFoundation.audioTimePitchAlgorithm
import platform.AVFoundation.defaultRate
import platform.AVFoundation.error
import platform.AVFoundation.loadedTimeRanges
import platform.AVFoundation.seekableTimeRanges
import platform.AVFoundation.setAudioTimePitchAlgorithm
import platform.AVFoundation.setDefaultRate
import platform.AVFoundation.setRate
import platform.AVFoundation.setVolume
import platform.AVFoundation.status
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.CoreMedia.kCMTimePositiveInfinity
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSValue
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import kotlinx.cinterop.readValue
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
 * Opus. [MediaSource.engine] says which one opens a source; [EnginePlayer][io.github.aedev.flow.player.engine.EnginePlayer]
 * drives either through [EngineControl], and this adds what only iOS has. Calls may come from any thread except
 * where noted.
 */
interface PlaybackEngine : EngineControl {
    /** What shows the video: an AVPlayerLayer or an AVSampleBufferDisplayLayer. Add it to a view on the main thread. */
    val layer: CALayer

    fun stats(): PlaybackStats

    /** Picture in Picture over [layer], made on first use. Main thread only. */
    fun pictureInPictureController(): AVPictureInPictureController?
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
    /** The pixel buffers handed to the display: "NV12" or "P010", and "full range" when so. */
    val pixelFormat: String?,
    /** The colour primaries and transfer function attached to them, as Core Video names them (PQ is "SMPTE_ST_2084_PQ"). */
    val colorPrimaries: String?,
    val transferFunction: String?,
)

object PlaybackEngines {
    /** Set by the app at start-up (iOSApp.swift): the bundled engine is Swift. */
    var bundled: (() -> PlaybackEngine)? = null

    /** Gives the video player its engines; the app calls it at start-up, after registering [bundled]. */
    fun install() {
        io.github.aedev.flow.player.EnhancedPlayerManager.engineFactory = ::create
    }

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
    private var endObserver: Any? = null

    @Volatile
    private var ended = false

    @Volatile
    private var seekExact = true

    @Volatile
    private var videoEnabled = true
    private var maxWidth = 0
    private var maxHeight = 0
    private var maxBitrate = 0L

    override fun prepare(source: MediaSource) {
        removeEndObserver()
        ended = false
        val next = AVPlayerItem(asset = AVURLAsset(uRL = NSURL.URLWithString(source.uri)!!, options = null))
        // Speed keeps the pitch, as Media3's PlaybackParameters do.
        next.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmSpectral
        if (source.forwardBufferMs > 0) next.preferredForwardBufferDuration = source.forwardBufferMs / 1000.0
        item = next
        applyLimits(next)
        endObserver =
            NSNotificationCenter.defaultCenter.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, next, null) { _ ->
                ended = true
            }
        player.replaceCurrentItemWithPlayerItem(next)
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        ended = false
        val tolerance = if (seekExact) CMTimeMake(0, 1) else kCMTimePositiveInfinity.readValue()
        player.seekToTime(CMTimeMake(positionMs, 1000), tolerance, tolerance)
    }

    override fun setSeekExact(exact: Boolean) {
        seekExact = exact
    }

    override fun currentPositionMs(): Long = (CMTimeGetSeconds(player.currentTime()) * 1000).takeIf { it.isFinite() }?.toLong() ?: 0

    /** A live stream's duration is the end of its seekable window, as Media3 gives a live window's duration. */
    override fun durationMs(): Long {
        val current = item ?: return 0
        val seconds = CMTimeGetSeconds(current.duration)
        if (seconds.isFinite() && seconds > 0) return (seconds * 1000).toLong()
        return current.seekableTimeRanges.lastEndMs()
    }

    override fun bufferedPositionMs(): Long = item?.loadedTimeRanges?.lastEndMs() ?: 0

    override fun isPlaying(): Boolean = player.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override fun playbackState(): Int {
        val current = item ?: return FlowPlayer.STATE_IDLE
        return when {
            current.status == AVPlayerItemStatusFailed || player.error != null -> FlowPlayer.STATE_IDLE
            ended -> FlowPlayer.STATE_ENDED
            current.status != AVPlayerItemStatusReadyToPlay -> FlowPlayer.STATE_BUFFERING
            player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> FlowPlayer.STATE_BUFFERING
            else -> FlowPlayer.STATE_READY
        }
    }

    override fun error(): String? = (item?.error ?: player.error)?.localizedDescription

    override fun errorCode(): Int = (item?.error ?: player.error)?.let(::media3ErrorCode) ?: PlaybackException.ERROR_CODE_UNSPECIFIED

    override fun setPlaybackRate(rate: Float) {
        player.defaultRate = rate
        if (player.rate > 0f) player.rate = rate
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun setVideoEnabled(enabled: Boolean) {
        videoEnabled = enabled
        item?.tracks?.forEach { track ->
            val playerTrack = track as? AVPlayerItemTrack ?: return@forEach
            if (playerTrack.assetTrack?.mediaType == AVMediaTypeVideo) playerTrack.enabled = enabled
        }
    }

    override fun setVideoLimits(
        maxWidth: Int,
        maxHeight: Int,
        maxBitrate: Long,
    ) {
        this.maxWidth = maxWidth
        this.maxHeight = maxHeight
        this.maxBitrate = maxBitrate
        item?.let(::applyLimits)
    }

    private fun applyLimits(target: AVPlayerItem) {
        target.preferredMaximumResolution = CGSizeMake(maxWidth.toDouble(), maxHeight.toDouble())
        target.preferredPeakBitRate = maxBitrate.toDouble()
    }

    override fun videoDecoderName(): String? = "AVPlayer"

    override fun droppedFrameCount(): Int =
        item?.accessLog()?.events?.sumOf { (it as? AVPlayerItemAccessLogEvent)?.numberOfDroppedVideoFrames?.toInt() ?: 0 } ?: 0

    override fun hasRenderedFirstFrame(): Boolean = playerLayer.readyForDisplay

    override fun videoWidth(): Int = item?.presentationSize?.useContents { width.toInt() } ?: 0

    override fun videoHeight(): Int = item?.presentationSize?.useContents { height.toInt() } ?: 0

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
            pixelFormat = null,
            colorPrimaries = null,
            transferFunction = null,
        )
    }

    override fun pictureInPictureController(): AVPictureInPictureController? =
        pictureInPicture ?: AVPictureInPictureController(playerLayer = playerLayer).also { pictureInPicture = it }

    override fun close() {
        removeEndObserver()
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        item = null
    }

    private fun removeEndObserver() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
    }
}

private fun List<*>.lastEndMs(): Long {
    val range = lastOrNull() as? NSValue ?: return 0
    val seconds = CMTimeGetSeconds(CMTimeRangeGetEnd(range.CMTimeRangeValue))
    return if (seconds.isFinite()) (seconds * 1000).toLong() else 0
}

/** AVFoundation's failures in Media3's codes, so upstream's error handling reads them as it reads ExoPlayer's. */
internal fun media3ErrorCode(error: NSError): Int =
    when (error.domain) {
        NSURLErrorDomain ->
            if (error.code == NSURLErrorTimedOut) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            } else {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            }
        // CoreMedia reports a segment's or playlist's HTTP status as -12938 (404), -12660 (403) and their kin.
        "CoreMediaErrorDomain" ->
            when (error.code) {
                -12938L, -12660L, -12661L, -12935L -> PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                -12889L, -12888L, -12971L -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                -12642L, -12645L -> PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                else -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            }
        AVFoundationErrorDomain ->
            when (error.code) {
                AVErrorDecoderNotFound, AVErrorFormatUnsupported, AVErrorFileFormatNotRecognized ->
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                AVErrorDecodeFailed, AVErrorMediaServicesWereReset -> PlaybackException.ERROR_CODE_DECODING_FAILED
                AVErrorDecoderTemporarilyUnavailable -> PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                AVErrorContentIsUnavailable, AVErrorNoLongerPlayable -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                else -> PlaybackException.ERROR_CODE_UNSPECIFIED
            }
        else -> PlaybackException.ERROR_CODE_UNSPECIFIED
    }
