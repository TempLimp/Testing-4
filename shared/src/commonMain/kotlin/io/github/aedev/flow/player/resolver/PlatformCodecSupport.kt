package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.player.stream.VideoCodecUtils
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

/** The two engines iOS plays YouTube's streams with (PORTING_PLAN line 20). Upstream's ExoPlayer plays them all. */
enum class PlaybackEngineKind {
    /** AVPlayer through [LocalHlsProxy]'s HLS: H.264, HEVC, AAC, AC-3 and E-AC-3 in MP4, AV1 with a hardware decoder. */
    AVPLAYER,

    /** The bundled FFmpeg + dav1d engine (S04b), reading the proxy's files: VP9, AV1 and Opus, and AAC beside them. */
    BUNDLED,
}

/**
 * What this device's two engines decode, which decides the engine for a stream once upstream's own logic
 * ([VideoCodecUtils], QualityManager) has chosen it. AVPlayer reads neither WebM nor VP9 nor Opus, and AV1
 * only where VideoToolbox decodes it in hardware; those go to the bundled engine.
 */
expect object PlatformCodecSupport {
    /** Whether a hardware AV1 decoder is present (A17 Pro, M3 and later). */
    val av1: Boolean

    /** Whether audio is routed to AirPlay, where only AVPlayer can send the video: sample-buffer layers cannot. */
    val airPlayActive: Boolean
}

/**
 * The engine that plays a stream in [containerMimeType] carrying [codecs] (RFC 6381), or null when neither does.
 * AVPlayer wins where both decode a format (AAC); while [airPlay] is on, only AVPlayer's formats play.
 */
fun PlatformCodecSupport.engineFor(
    containerMimeType: String?,
    codecs: String?,
    airPlay: Boolean = airPlayActive,
): PlaybackEngineKind? = engineFor(containerMimeType, codecs, airPlay, av1)

internal fun engineFor(
    containerMimeType: String?,
    codecs: String?,
    airPlay: Boolean,
    hardwareAv1: Boolean,
): PlaybackEngineKind? =
    when {
        decodes(PlaybackEngineKind.AVPLAYER, containerMimeType, codecs, hardwareAv1) -> PlaybackEngineKind.AVPLAYER
        !airPlay && decodes(PlaybackEngineKind.BUNDLED, containerMimeType, codecs, hardwareAv1) -> PlaybackEngineKind.BUNDLED
        else -> null
    }

internal fun decodes(
    engine: PlaybackEngineKind,
    containerMimeType: String?,
    codecs: String?,
    hardwareAv1: Boolean,
): Boolean {
    val container = containerMimeType.orEmpty().lowercase().substringBefore(';').trim()
    val codec = codecs.orEmpty().lowercase().substringBefore(',').trim()
    return when (engine) {
        PlaybackEngineKind.AVPLAYER ->
            container.endsWith("/mp4") &&
                when {
                    codec.isEmpty() -> container.startsWith("audio/")
                    codec.startsWith("avc1") || codec.startsWith("avc3") -> true
                    codec.startsWith("hev1") || codec.startsWith("hvc1") -> true
                    codec.startsWith("av01") -> hardwareAv1
                    codec.startsWith("mp4a") || codec.startsWith("ac-3") || codec.startsWith("ec-3") -> true
                    else -> false
                }

        // The decoders tools/build_ffmpeg_xcframework.sh builds: vp9, libdav1d, opus and aac, behind the matroska and mov demuxers.
        PlaybackEngineKind.BUNDLED ->
            (container.endsWith("/webm") || container.endsWith("/mp4")) &&
                (codec == "vp9" || codec.startsWith("vp09") || codec.startsWith("av01") || codec == "opus" || codec.startsWith("mp4a"))
    }
}

/** Whether a stream in [containerMimeType] carrying [codecs] plays here, in either engine. */
fun PlatformCodecSupport.canPlay(
    containerMimeType: String?,
    codecs: String?,
): Boolean = engineFor(containerMimeType, codecs) != null

/** The engine that plays [stream], or null when neither does. */
fun PlatformCodecSupport.engineFor(
    stream: Stream,
    airPlay: Boolean = airPlayActive,
): PlaybackEngineKind? = engineFor(stream.format?.mimeType, codecOf(stream), airPlay)

fun PlatformCodecSupport.canPlay(stream: Stream): Boolean = engineFor(stream) != null

/** Whether [engine] decodes [stream], whichever engine would be picked for it on its own. */
fun PlatformCodecSupport.decodes(
    engine: PlaybackEngineKind,
    stream: Stream,
): Boolean = decodes(engine, stream.format?.mimeType, codecOf(stream), av1)

private fun codecOf(stream: Stream): String? =
    stream.itagItem?.codec?.takeIf { it.isNotBlank() }
        ?: (stream as? AudioStream)?.codec
        // No codec string: the codec the itag and container imply, as upstream ranks them.
        ?: (stream as? VideoStream)?.let { CODEC_BY_KEY[VideoCodecUtils.codecKeyFromStream(it)] }

private val CODEC_BY_KEY = mapOf("h264" to "avc1", "hevc" to "hvc1", "av1" to "av01", "vp9" to "vp9")

/**
 * An audio stream [engine] can play beside video: one it decodes, described by an itag with its byte ranges, which
 * [LocalHlsProxy] needs to publish it. Upstream's bridge leaves the itags NewPipe's table lacks (AC-3 380, EC-3 328)
 * without one, and ExoPlayer plays those progressively next to the video; neither engine is handed a progressive
 * file beside a video, so on iOS those tracks are passed over for one that can.
 */
fun PlatformCodecSupport.canMergeAudio(
    stream: AudioStream,
    engine: PlaybackEngineKind = PlaybackEngineKind.AVPLAYER,
): Boolean {
    val itag = stream.itagItem ?: return false
    val playable = decodes(engine, stream) && (engine == PlaybackEngineKind.AVPLAYER || !airPlayActive)
    return playable && itag.initEnd > itag.initStart && itag.indexEnd > itag.indexStart
}
