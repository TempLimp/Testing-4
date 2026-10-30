package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.player.stream.VideoCodecUtils
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream

/**
 * What this device's player can open, which on iOS decides the VP9 question: AVPlayer reads neither
 * WebM nor VP9, so YouTube's VP9 and Opus streams (all WebM) are left out, and AV1 (in MP4) is kept
 * only where VideoToolbox decodes it in hardware. Upstream's ExoPlayer plays all of them.
 */
expect object PlatformCodecSupport {
    /** Whether a hardware AV1 decoder is present (A17 Pro, M3 and later). */
    val av1: Boolean
}

/** Whether a stream in [containerMimeType] carrying [codecs] (RFC 6381) plays here. */
fun PlatformCodecSupport.canPlay(
    containerMimeType: String?,
    codecs: String?,
): Boolean {
    val container = containerMimeType.orEmpty().lowercase()
    if (!container.endsWith("/mp4")) return false
    val codec = codecs.orEmpty().lowercase().substringBefore(',').trim()
    return when {
        codec.isEmpty() -> container.startsWith("audio/")
        codec.startsWith("avc1") || codec.startsWith("avc3") -> true
        codec.startsWith("hev1") || codec.startsWith("hvc1") -> true
        codec.startsWith("av01") -> av1
        codec.startsWith("mp4a") || codec.startsWith("ac-3") || codec.startsWith("ec-3") -> true
        else -> false
    }
}

fun PlatformCodecSupport.canPlay(stream: Stream): Boolean {
    val codec =
        stream.itagItem?.codec?.takeIf { it.isNotBlank() }
            ?: (stream as? AudioStream)?.codec
    if (codec == null && stream !is AudioStream) {
        // No codec string: judge by the codec the itag and container imply, as upstream ranks them.
        val key = (stream as? org.schabi.newpipe.extractor.stream.VideoStream)?.let(VideoCodecUtils::codecKeyFromStream)
        return stream.format?.mimeType?.endsWith("/mp4") == true && (key == "h264" || key == "hevc" || (key == "av1" && av1))
    }
    return canPlay(stream.format?.mimeType, codec)
}

/**
 * An audio stream the player can serve beside video renditions: decodable here, and described by an
 * itag with its byte ranges. Upstream's bridge leaves the itags NewPipe's table lacks (AC-3 380, EC-3
 * 328) without one, and ExoPlayer plays those progressively next to the video; AVPlayer cannot put a
 * progressive file into an HLS master, so on iOS those tracks are passed over for one it can.
 */
fun PlatformCodecSupport.canMergeAudio(stream: AudioStream): Boolean {
    val itag = stream.itagItem ?: return false
    return canPlay(stream) && itag.initEnd > itag.initStart && itag.indexEnd > itag.indexStart
}
