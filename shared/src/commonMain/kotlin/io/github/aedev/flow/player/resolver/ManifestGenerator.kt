package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.utils.Log
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream

/**
 * Upstream wraps NewPipe's `YoutubeProgressiveDashManifestCreator` (a DASH MPD with the stream's URL,
 * init range and sidx range) and `YoutubeOtfDashManifestCreator`. AVPlayer reads no DASH, so the same
 * description becomes an [HlsRendition] that [LocalHlsProxy] serves as HLS.
 *
 * OTF streams (`DeliveryMethod.DASH`) have no iOS form: upstream's InnerTube path never produces them
 * ([io.github.aedev.flow.player.stream.InnerTubeStreamBridge] marks every format PROGRESSIVE_HTTP), and
 * [VideoPlaybackResolver] plays one it is handed as a progressive stream.
 */
object ManifestGenerator {
    private const val TAG = "ManifestGenerator"

    /**
     * The stream as one rendition, held to the rules NewPipe's creator enforced: a codec, a bitrate,
     * a size for video, and real init and index ranges. Null when the stream cannot be described.
     */
    fun progressiveRendition(
        stream: Stream,
        itagItem: ItagItem,
    ): HlsRendition? {
        val isAudio = itagItem.itagType == ItagItem.ItagType.AUDIO
        val codec = itagItem.codec
        val problem =
            when {
                itagItem.id <= 0 -> "itag id ${itagItem.id}"
                codec.isNullOrEmpty() -> "no codec"
                itagItem.bitrate <= 0 -> "bitrate ${itagItem.bitrate}"
                !isAudio && itagItem.width <= 0 && itagItem.height <= 0 -> "no size"
                itagItem.initStart < 0 || itagItem.initEnd <= itagItem.initStart -> "init ${itagItem.initStart}-${itagItem.initEnd}"
                itagItem.indexStart < 0 || itagItem.indexEnd <= itagItem.indexStart -> "index ${itagItem.indexStart}-${itagItem.indexEnd}"
                !stream.isUrl || stream.content.isBlank() -> "no URL"
                else -> null
            }
        if (problem != null) {
            Log.w(TAG, "Progressive manifest generation failed for itag=${itagItem.id}: $problem")
            return null
        }
        val audio = stream as? AudioStream
        return HlsRendition(
            url = stream.content,
            codecs = codec!!,
            bandwidth = itagItem.bitrate,
            isAudio = isAudio,
            initStart = itagItem.initStart.toLong(),
            initEnd = itagItem.initEnd.toLong(),
            indexStart = itagItem.indexStart.toLong(),
            indexEnd = itagItem.indexEnd.toLong(),
            width = itagItem.width,
            height = itagItem.height,
            frameRate = itagItem.fps.coerceAtLeast(0),
            audioLanguage = audio?.audioLocale?.toLanguageTag()?.takeIf { it.isNotBlank() && it != "und" },
            audioName = audio?.audioTrackName,
            audioChannels = itagItem.audioChannels.coerceAtLeast(0),
        )
    }

    /**
     * One stream on its own, as upstream's single-stream DASH manifest: its duration is the format's
     * own when known, else [durationSeconds]. Null when either is missing, as NewPipe's creator threw.
     */
    fun generateProgressiveManifest(
        stream: Stream,
        itagItem: ItagItem,
        durationSeconds: Long,
    ): HlsPresentation? {
        val durationMs =
            itagItem.approxDurationMs.takeIf { it != ItagItem.APPROX_DURATION_MS_UNKNOWN }
                ?: (durationSeconds * 1_000L).takeIf { durationSeconds > 0 }
                ?: return null.also { Log.w(TAG, "No duration for itag=${itagItem.id}") }
        val rendition = progressiveRendition(stream, itagItem) ?: return null
        return if (rendition.isAudio) {
            HlsPresentation(video = emptyList(), audio = rendition, durationMs = durationMs)
        } else {
            HlsPresentation(video = listOf(rendition), audio = null, durationMs = durationMs)
        }
    }
}
