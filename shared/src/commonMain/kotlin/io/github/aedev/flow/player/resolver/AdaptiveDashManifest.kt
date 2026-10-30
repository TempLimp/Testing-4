package io.github.aedev.flow.player.resolver

import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

/** A whole quality ladder as HLS variants, which AVPlayer switches between itself. */
data class AdaptiveLadderManifest(
    val representations: List<HlsRendition>,
    val durationMs: Long,
)

/**
 * One manifest holding a whole quality ladder, so the player's adaptive selection switches between the
 * rungs chunk by chunk. Upstream builds it straight into Media3's DASH model; on iOS it is the variants
 * of one HLS master playlist. Each rung is described the way [ManifestGenerator] describes a single
 * stream: the progressive URL, plus its init and sidx byte ranges.
 */
internal object AdaptiveDashManifest {
    /** Whether [stream] carries what a rung needs: a progressive URL, its byte ranges and a codec. */
    fun canDescribe(stream: VideoStream): Boolean {
        val itag = stream.itagItem ?: return false
        return stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
            stream.isVideoOnly &&
            stream.content.isNotBlank() &&
            !itag.codec.isNullOrBlank() &&
            itag.bitrate > 0 &&
            itag.initStart >= 0 &&
            itag.initEnd > itag.initStart &&
            itag.indexStart >= 0 &&
            itag.indexEnd > itag.indexStart
    }

    /** Null unless at least two rungs can be described and the duration is known. */
    fun build(
        rungs: List<VideoStream>,
        durationSeconds: Long,
    ): AdaptiveLadderManifest? {
        val representations =
            rungs.filter(::canDescribe).mapNotNull { ManifestGenerator.progressiveRendition(it, it.itagItem!!) }
        if (representations.size < 2) return null
        val durationMs =
            rungs
                .firstNotNullOfOrNull { stream -> stream.itagItem?.approxDurationMs?.takeIf { it > 0 } }
                ?: (durationSeconds * 1_000L)
        if (durationMs <= 0) return null
        return AdaptiveLadderManifest(representations, durationMs)
    }
}
