package io.github.aedev.flow.player.resolver

import kotlin.math.ceil

/**
 * One fragmented-MP4 stream as an HLS rendition: a googlevideo URL plus the byte ranges of its init
 * segment (ftyp + moov) and its `sidx` index. Upstream hands ExoPlayer the same stream as a DASH
 * `SegmentBase` (NewPipe's progressive manifest creator, [AdaptiveDashManifest]); AVPlayer plays
 * neither DASH nor WebM, so on iOS the stream becomes an HLS media playlist whose segments are the
 * sidx's subsegments, read as byte ranges of the same URL ([LocalHlsProxy]).
 */
data class HlsRendition(
    val url: String,
    /** RFC 6381 codec string, e.g. `avc1.4d401f` or `mp4a.40.2`. */
    val codecs: String,
    /** Peak bits per second: the stream's own bitrate until [measuredBy] reads its segments. */
    val bandwidth: Int,
    val isAudio: Boolean,
    val initStart: Long,
    val initEnd: Long,
    val indexStart: Long,
    val indexEnd: Long,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Int = 0,
    val audioLanguage: String? = null,
    val audioName: String? = null,
    val audioChannels: Int = 0,
    /** Mean bits per second over the whole stream, once measured; 0 when unknown. */
    val averageBandwidth: Int = 0,
    /** The container, e.g. `video/webm`: the bundled engine reads WebM renditions as files ([LocalHlsProxy.publishFiles]). */
    val mimeType: String = "",
) {
    /**
     * The rates HLS asks for, read off the sidx: BANDWIDTH is the peak segment bit rate (AVPlayer logs -12318,
     * "Segment exceeds specified bandwidth", when a segment beats it; YouTube's bitrates are averages), and
     * AVERAGE-BANDWIDTH the mean.
     */
    fun measuredBy(segments: List<SidxSegment>): HlsRendition {
        val timed = segments.filter { it.durationSeconds > 0 }
        if (timed.isEmpty()) return this
        val peak = timed.maxOf { it.size * 8 / it.durationSeconds }
        val average = timed.sumOf { it.size } * 8 / timed.sumOf { it.durationSeconds }
        return copy(bandwidth = maxOf(bandwidth, ceil(peak).toInt()), averageBandwidth = ceil(average).toInt())
    }
}

/**
 * What upstream hands ExoPlayer as `MergingMediaSource(video, audio)`: the video renditions (one, or an
 * adaptive ladder) and the audio track, one HLS master playlist for AVPlayer.
 */
data class HlsPresentation(
    val video: List<HlsRendition>,
    val audio: HlsRendition?,
    val durationMs: Long,
) {
    val renditions: List<HlsRendition> get() = video + listOfNotNull(audio)
}

/** One subsegment of a `sidx`: where its bytes are and how long it plays. */
data class SidxSegment(
    val offset: Long,
    val size: Long,
    val durationSeconds: Double,
)

/** ISO/IEC 14496-12 `sidx` box, as YouTube writes it after the moov of every adaptive MP4. */
object SidxParser {
    /**
     * @param box the bytes of [boxStart]..end of the index range, starting at the box header.
     * @param boxStart the box's absolute offset in the file, which its first_offset is measured from.
     * @return the subsegments, or null when [box] is not a well-formed sidx.
     */
    fun parse(
        box: ByteArray,
        boxStart: Long,
    ): List<SidxSegment>? {
        if (box.size < 32) return null
        var position = 0

        fun u8() = box[position++].toInt() and 0xFF

        fun u16() = (u8() shl 8) or u8()

        fun u32() = (u16().toLong() shl 16) or u16().toLong()

        fun u64() = (u32() shl 32) or u32()

        val boxSize = u32()
        if (box.decodeToString(4, 8) != "sidx") return null
        position = 8
        if (boxSize < 32 || boxSize > box.size) return null
        val version = u8()
        position += 3
        u32() // reference_ID
        val timescale = u32()
        if (timescale <= 0) return null
        if (version == 0) {
            u32()
        } else {
            u64()
        }
        val firstOffset = if (version == 0) u32() else u64()
        position += 2
        val count = u16()
        if (position + count * 12L > boxSize) return null
        var offset = boxStart + boxSize + firstOffset
        return List(count) {
            val reference = u32()
            val duration = u32()
            u32() // SAP fields
            if (reference and 0x8000_0000L != 0L) return null // a nested index, which YouTube never writes
            val size = reference and 0x7FFF_FFFFL
            SidxSegment(offset, size, duration.toDouble() / timescale).also { offset += size }
        }
    }
}

/** The HLS text the local proxy serves (RFC 8216, version 7 for fMP4 byte ranges). */
object HlsPlaylists {
    private const val AUDIO_GROUP = "audio"

    /**
     * @param uriOf the playlist URI of a rendition, relative to the master's.
     */
    fun master(
        presentation: HlsPresentation,
        uriOf: (HlsRendition) -> String,
    ): String =
        buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:7")
            appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
            val audio = presentation.audio
            if (presentation.video.isEmpty()) {
                if (audio != null) {
                    append("#EXT-X-STREAM-INF:BANDWIDTH=${audio.bandwidth}")
                    if (audio.averageBandwidth > 0) append(",AVERAGE-BANDWIDTH=${audio.averageBandwidth}")
                    appendLine(",CODECS=\"${audio.codecs}\"")
                    appendLine(uriOf(audio))
                }
                return@buildString
            }
            if (audio != null) {
                append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"$AUDIO_GROUP\",NAME=\"${quoted(audio.audioName ?: audio.audioLanguage ?: "Audio")}\"")
                audio.audioLanguage?.let { append(",LANGUAGE=\"${quoted(it)}\"") }
                if (audio.audioChannels > 0) append(",CHANNELS=\"${audio.audioChannels}\"")
                appendLine(",DEFAULT=YES,AUTOSELECT=YES,URI=\"${uriOf(audio)}\"")
            }
            presentation.video.sortedBy { it.bandwidth }.forEach { video ->
                append("#EXT-X-STREAM-INF:BANDWIDTH=${video.bandwidth + (audio?.bandwidth ?: 0)}")
                if (video.averageBandwidth > 0 && (audio == null || audio.averageBandwidth > 0)) {
                    append(",AVERAGE-BANDWIDTH=${video.averageBandwidth + (audio?.averageBandwidth ?: 0)}")
                }
                append(",CODECS=\"${listOfNotNull(video.codecs, audio?.codecs).joinToString(",")}\"")
                if (video.width > 0 && video.height > 0) append(",RESOLUTION=${video.width}x${video.height}")
                if (video.frameRate > 0) append(",FRAME-RATE=${video.frameRate}.000")
                if (audio != null) append(",AUDIO=\"$AUDIO_GROUP\"")
                appendLine()
                appendLine(uriOf(video))
            }
        }

    /**
     * @param mediaUri where the rendition's bytes are served, relative to this playlist.
     */
    fun media(
        rendition: HlsRendition,
        segments: List<SidxSegment>,
        mediaUri: String,
    ): String =
        buildString {
            val target = segments.maxOfOrNull { ceil(it.durationSeconds).toInt() }?.coerceAtLeast(1) ?: 1
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:7")
            appendLine("#EXT-X-TARGETDURATION:$target")
            appendLine("#EXT-X-MEDIA-SEQUENCE:0")
            appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
            appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
            val initLength = rendition.initEnd - rendition.initStart + 1
            appendLine("#EXT-X-MAP:URI=\"$mediaUri\",BYTERANGE=\"$initLength@${rendition.initStart}\"")
            segments.forEach { segment ->
                appendLine("#EXTINF:${formatDuration(segment.durationSeconds)},")
                appendLine("#EXT-X-BYTERANGE:${segment.size}@${segment.offset}")
                appendLine(mediaUri)
            }
            appendLine("#EXT-X-ENDLIST")
        }

    private fun quoted(value: String) = value.replace("\"", "'").replace("\n", " ")

    // Six decimals, the precision HLS validators expect from EXTINF.
    private fun formatDuration(seconds: Double): String {
        val micros = kotlin.math.round(seconds * 1_000_000).toLong()
        return "${micros / 1_000_000}.${(micros % 1_000_000).toString().padStart(6, '0')}"
    }
}
