package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.utils.Log
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Specialized resolver for video playback that handles different stream types
 * including generating local manifests to avoid YouTube throttling.
 *
 * YouTube throttles progressive (direct URL) streams to ~50-100 KB/s, which causes
 * buffering even on fast connections. The solution is to read them as segmented manifests,
 * which are not throttled and allow adaptive bitrate switching.
 *
 * Priority order (upstream's, for AVPlayer):
 * 1. YouTube's HLS manifest for live streams. Its DASH manifests (live DVR and VOD) are skipped:
 *    AVPlayer reads no DASH.
 * 2. Streams described as HLS renditions served by [LocalHlsProxy], where upstream generates DASH:
 *    a ladder of qualities, or one quality, with the audio track in the same master playlist where
 *    upstream merges two sources.
 * 3. Direct progressive stream - Last resort, may buffer
 *
 * Streams AVPlayer cannot decode ([PlatformCodecSupport]: WebM, VP9, Opus, AV1 without a hardware
 * decoder) are never handed to it.
 */
class VideoPlaybackResolver(
    private val mediaId: String = "",
) {
    companion object {
        private const val TAG = "VideoPlaybackResolver"
    }

    /** Whether the last [resolve] handed the player a quality ladder to adapt over. */
    var resolvedAdaptiveLadder = false
        private set

    /** One resolved half of what upstream merges: a rendition the proxy serves, or a source as it is. */
    private sealed interface Part {
        data class Rendition(
            val rendition: HlsRendition,
            val durationMs: Long,
        ) : Part

        data class Direct(
            val source: MediaSource,
        ) : Part
    }

    suspend fun resolve(
        videoStreams: List<VideoStream>,
        audioStream: AudioStream?,
        dashManifestUrl: String?,
        hlsUrl: String?,
        durationSeconds: Long,
        isLiveStream: Boolean = false,
        asAdaptiveLadder: Boolean = false,
    ): MediaSource? {
        resolvedAdaptiveLadder = false
        Log.d(
            TAG,
            "Resolving playback: ${videoStreams.size} video streams, audio=${audioStream != null}, " +
                "dash=${!dashManifestUrl.isNullOrEmpty()}, hls=${!hlsUrl.isNullOrEmpty()}, duration=${durationSeconds}s",
        )

        if (isLiveStream && !hlsUrl.isNullOrEmpty()) {
            Log.d(TAG, "Using YouTube HLS manifest for live playback: ${hlsUrl.take(80)}...")
            return MediaSourceBuilder.buildHlsSource(hlsUrl, mediaId, liveTargetOffsetMs = PlayerConfig.LIVE_EDGE_GAP_MS)
        }

        if (isLiveStream && !dashManifestUrl.isNullOrEmpty()) {
            Log.w(TAG, "Live stream offers only a DASH manifest, which AVPlayer cannot read")
        }

        // If only 1 video stream is passed, the user selected a specific quality
        val useSpecificStream = videoStreams.size == 1

        // YouTube's manifest carries only the default audio track, so honouring it would silently
        // ignore a dub the user picked.
        val overridesDefaultAudio = StreamProcessor.overridesDefaultAudioTrack(audioStream)

        if (!dashManifestUrl.isNullOrEmpty() && !useSpecificStream && !overridesDefaultAudio) {
            Log.d(TAG, "YouTube DASH manifest present; AVPlayer reads no DASH, so the streams become HLS")
        } else if (overridesDefaultAudio) {
            Log.d(TAG, "Non-default audio track selected (${audioStream?.audioTrackName}) - bypassing YouTube DASH URL")
        } else if (useSpecificStream) {
            Log.d(
                TAG,
                "User selected specific quality (${videoStreams.firstOrNull()?.let(
                    VideoCodecUtils::qualityHeightFromStream,
                )}p) - bypassing YouTube DASH URL",
            )
        }

        val playableVideo = videoStreams.filter { PlatformCodecSupport.canPlay(it) }
        if (playableVideo.size < videoStreams.size) {
            Log.d(TAG, "Dropped ${videoStreams.size - playableVideo.size} video streams this device cannot decode")
        }
        val playableAudio = audioStream?.takeIf { PlatformCodecSupport.canPlay(it) }
        if (audioStream != null && playableAudio == null) {
            Log.w(TAG, "Audio stream ${audioStream.format?.mimeType} ${audioStream.codec} cannot be decoded here")
        }

        val ladder = if (asAdaptiveLadder) createLadder(playableVideo, durationSeconds) else null
        resolvedAdaptiveLadder = ladder != null
        // A ladder that cannot be built starts on its lowest rung; Auto's own checks step up from there.
        val videoParts =
            ladder?.representations?.map { Part.Rendition(it, ladder.durationMs) }
                ?: listOfNotNull(
                    createVideoPart(
                        videoStreams = if (asAdaptiveLadder) playableVideo.take(1) else playableVideo,
                        durationSeconds = durationSeconds,
                        preferMuxed = playableAudio == null,
                    ),
                )
        val audioPart = createAudioPart(playableAudio, durationSeconds)

        val videoRenditions = videoParts.filterIsInstance<Part.Rendition>()
        val directVideo = videoParts.filterIsInstance<Part.Direct>().firstOrNull()
        return when {
            videoRenditions.isNotEmpty() && audioPart is Part.Rendition -> {
                Log.d(TAG, "Created HLS master with video + audio")
                presentation(videoRenditions, audioPart)
            }

            videoRenditions.isNotEmpty() && audioPart is Part.Direct -> {
                // An HLS master cannot carry a progressive file; upstream merges them with sync adjusted.
                Log.e(TAG, "Audio stream has no index to serve beside the video renditions")
                null
            }

            videoRenditions.isNotEmpty() -> {
                Log.d(TAG, "Using video source only (no separate audio)")
                presentation(videoRenditions, null)
            }

            directVideo != null -> {
                if (audioPart != null) Log.w(TAG, "Progressive video cannot be merged with separate audio; playing the video file")
                directVideo.source
            }

            audioPart is Part.Rendition -> {
                Log.d(TAG, "Using audio source only")
                MediaSourceBuilder.buildLocalHlsSource(HlsPresentation(emptyList(), audioPart.rendition, audioPart.durationMs), mediaId)
            }

            audioPart is Part.Direct -> {
                Log.d(TAG, "Using audio source only")
                audioPart.source
            }

            else -> {
                Log.e(TAG, "Failed to create any media source!")
                null
            }
        }
    }

    private suspend fun presentation(
        video: List<Part.Rendition>,
        audio: Part.Rendition?,
    ): MediaSource =
        MediaSourceBuilder.buildLocalHlsSource(
            HlsPresentation(
                video = video.map { it.rendition },
                audio = audio?.rendition,
                durationMs = video.first().durationMs,
            ),
            mediaId,
        )

    private fun createLadder(
        videoStreams: List<VideoStream>,
        durationSeconds: Long,
    ): AdaptiveLadderManifest? {
        val manifest =
            try {
                AdaptiveDashManifest.build(videoStreams, durationSeconds)
            } catch (e: Exception) {
                Log.w(TAG, "Adaptive ladder manifest failed, using a single stream", e)
                null
            } ?: return null
        Log.d(TAG, "Adaptive ladder: ${videoStreams.map { "${VideoCodecUtils.qualityHeightFromStream(it)}p" }}")
        return manifest
    }

    /**
     * Create video source, preferring generated manifests over raw progressive
     */
    private fun createVideoPart(
        videoStreams: List<VideoStream>,
        durationSeconds: Long,
        preferMuxed: Boolean,
    ): Part? {
        if (videoStreams.isEmpty()) return null

        // Sort by quality and prefer video-only streams (they can use generated manifests)
        val sortedStreams =
            videoStreams.sortedWith(
                compareByDescending<VideoStream> { VideoCodecUtils.qualityHeightFromStream(it) }
                    .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                    .thenByDescending { it.bitrate },
            )

        val bestStream =
            if (preferMuxed) {
                sortedStreams.firstOrNull { !it.isVideoOnly } ?: sortedStreams.firstOrNull()
            } else {
                sortedStreams.firstOrNull { it.isVideoOnly } ?: sortedStreams.firstOrNull()
            }

        if (bestStream == null) return null

        val url = bestStream.content
        if (url.isEmpty()) {
            Log.w(TAG, "Video stream has null/empty URL")
            return null
        }
        Log.d(
            TAG,
            "Creating video source: ${VideoCodecUtils.qualityHeightFromStream(bestStream)}p, " +
                "delivery=${bestStream.deliveryMethod}, videoOnly=${bestStream.isVideoOnly}",
        )
        return when (bestStream.deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> {
                // Progressive streams - generate a manifest to avoid throttling
                if (bestStream.isVideoOnly && durationSeconds > 0) {
                    progressivePart(bestStream, durationSeconds) ?: directVideo(bestStream)
                } else {
                    directVideo(bestStream)
                }
            }

            DeliveryMethod.HLS -> {
                Part.Direct(MediaSourceBuilder.buildHlsSource(url, mediaId))
            }

            else -> {
                Log.w(TAG, "Unknown delivery method: ${bestStream.deliveryMethod}, using progressive")
                directVideo(bestStream)
            }
        }
    }

    /**
     * Create audio source, preferring generated manifests to avoid throttling
     */
    private fun createAudioPart(
        audioStream: AudioStream?,
        durationSeconds: Long,
    ): Part? {
        if (audioStream == null) return null
        if (audioStream.content.isEmpty()) {
            Log.w(TAG, "Audio stream has null/empty URL")
            return null
        }
        Log.d(TAG, "Creating audio source: ${audioStream.averageBitrate}kbps, delivery=${audioStream.deliveryMethod}")
        return if (audioStream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && durationSeconds > 0 && audioStream.itagItem != null) {
            progressivePart(audioStream, durationSeconds) ?: directAudio(audioStream)
        } else {
            directAudio(audioStream)
        }
    }

    private fun progressivePart(
        stream: Stream,
        durationSeconds: Long,
    ): Part.Rendition? {
        val itagItem = stream.itagItem ?: return null
        val single = ManifestGenerator.generateProgressiveManifest(stream, itagItem, durationSeconds)
        if (single == null) {
            Log.w(TAG, "Progressive manifest generation failed, using raw progressive (may throttle)")
            return null
        }
        return Part.Rendition(single.renditions.single(), single.durationMs)
    }

    /**
     * Fallback: Create standard progressive video source (may be throttled!)
     */
    private fun directVideo(stream: VideoStream): Part.Direct {
        Log.d(TAG, "Creating progressive video source for ${VideoCodecUtils.qualityHeightFromStream(stream)}p (WARNING: may be throttled)")
        return Part.Direct(MediaSourceBuilder.buildProgressiveSource(stream.content, stream.format?.mimeType ?: MediaFormat.MPEG_4.mimeType, mediaId))
    }

    /**
     * Fallback: Create standard progressive audio source
     */
    private fun directAudio(stream: AudioStream): Part.Direct {
        Log.d(TAG, "Creating progressive audio source (WARNING: may be throttled)")
        return Part.Direct(MediaSourceBuilder.buildProgressiveSource(stream.content, stream.format?.mimeType ?: MediaFormat.M4A.mimeType, mediaId))
    }
}
