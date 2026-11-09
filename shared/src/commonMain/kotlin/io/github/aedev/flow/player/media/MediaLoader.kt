package io.github.aedev.flow.player.media

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.engine.LazyMediaSource
import io.github.aedev.flow.player.engine.PlaybackException
import io.github.aedev.flow.player.error.PlayerStrings
import io.github.aedev.flow.player.resolver.LocalHlsProxy
import io.github.aedev.flow.player.resolver.MediaSource
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.resolver.VideoPlaybackResolver
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceFactory
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceResult
import io.github.aedev.flow.player.sabr.integration.SabrOrchestrator
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.surface.SurfaceManager
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.error_failed_to_load_media
import io.github.aedev.flow.resources.error_failed_to_load_media_invalid_streams
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Handles media loading and resolution.
 *
 * On iOS a source resolves when the player prepares it ([LazyMediaSource]), as Media3 opens a source lazily: the
 * streams are published to [LocalHlsProxy] then, or a SABR session started. What upstream decides synchronously
 * (which kind of source, whether there is anything to play) is still decided here when loading, so [loadMedia]
 * answers as upstream's does. Captions are not merged into the source: neither engine renders text, so the player
 * manager loads the selected caption itself ([CaptionLoader]).
 */
class MediaLoader(
    private val stateFlow: MutableStateFlow<EnhancedPlayerState>,
    private val surfaceManager: SurfaceManager?,
) {
    companion object {
        private const val TAG = "MediaLoader"
        private const val SUBTITLE_TRACK_ID_PREFIX = "flow-subtitle-"
        private const val MAX_RESOLUTIONS = 4
        private val SubtitleTrackIdPattern = Regex("""(?:^|:)$SUBTITLE_TRACK_ID_PREFIX(\d+)$""")

        internal fun subtitleTrackId(index: Int): String = "$SUBTITLE_TRACK_ID_PREFIX$index"

        /**
         * The caption index a track's format id names, or null for a track Flow did not add.
         * Merging sources prefix every format id with the source's position, so the id is matched
         * at its end.
         */
        internal fun subtitleTrackIndex(formatId: String?): Int? =
            formatId
                ?.let { SubtitleTrackIdPattern.find(it) }
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
    }

    private var activeSabr: SabrMediaSourceResult? = null
    private var lastSourceWasSabr = false

    /**
     * What a source played once it was resolved (which happens when the player prepares, not in [loadMedia]): a
     * quality ladder the player adapts over by itself, or [videoStream], the one rung of a ladder it plays.
     */
    data class Resolution(
        val adaptiveLadder: Boolean,
        val videoStream: VideoStream?,
    )

    private val resolutions = LinkedHashMap<String, Resolution>()

    /** How the source last built for [mediaId] resolved, when it has. */
    fun resolution(mediaId: String): Resolution? = resolutions[mediaId]

    /** The bandwidth estimate a ladder the player cannot adapt over starts from. */
    var bitrateEstimate: () -> Long? = { null }

    var onSabrFallbackNeeded: (() -> Unit)? = null

    /**
     * Load media with video and audio streams.
     *
     * @param audioOnly When true, never selects video streams or video manifests.
     * @param playWhenReady Whether playback should start after the source is prepared.
     * @return false when there is nothing to play, with the reason in the state's error.
     */
    fun loadMedia(
        player: FlowPlayer?,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        availableVideoStreams: List<VideoStream>,
        currentVideoStream: VideoStream?,
        dashManifestUrl: String?,
        hlsUrl: String?,
        isLiveStream: Boolean = false,
        durationSeconds: Long,
        currentDurationSeconds: Long,
        preservePosition: Long? = null,
        localFilePath: String? = null,
        audioOnly: Boolean = false,
        playWhenReady: Boolean = true,
        sabrInfo: SabrStreamInfo? = null,
        sabrVideoId: String? = null,
        sabrPreferred: Boolean = false,
        innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
        innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
        mediaId: String = "",
        adaptiveLadder: List<VideoStream> = emptyList(),
        audioAlternatives: List<AudioStream> = emptyList(),
        onStreams: (videoId: String, urls: Collection<String?>) -> Unit = { _, _ -> },
        onResolved: (Resolution) -> Unit = {},
    ): Boolean {
        val finalDuration =
            when {
                durationSeconds > 0 -> durationSeconds
                currentDurationSeconds > 0 -> currentDurationSeconds
                else -> 0L
            }
        val exoPlayer = player ?: return false
        try {
            Log.d(
                TAG,
                "Preparing media: video=${videoStream?.let(VideoCodecUtils::qualityHeightFromStream) ?: -1}p " +
                    "audioOnly=$audioOnly surfaceReady=${surfaceManager?.isSurfaceReady}",
            )
            if (!audioOnly && surfaceManager?.isSurfaceReady != true && localFilePath == null) {
                Log.w(TAG, "Surface not ready yet, preparing media and waiting for attach")
            }
            Log.d(TAG, "Resolving media with VideoPlaybackResolver for duration ${finalDuration}s")
            lastSourceWasSabr = false
            val mediaSource =
                createMediaSource(
                    videoStream = videoStream,
                    audioStream = audioStream,
                    availableVideoStreams = availableVideoStreams,
                    currentVideoStream = currentVideoStream,
                    dashManifestUrl = dashManifestUrl,
                    hlsUrl = hlsUrl,
                    isLiveStream = isLiveStream,
                    finalDuration = finalDuration,
                    localFilePath = localFilePath,
                    audioOnly = audioOnly,
                    sabrInfo = sabrInfo,
                    sabrVideoId = sabrVideoId,
                    sabrPreferred = sabrPreferred,
                    startPositionMs = preservePosition ?: 0L,
                    innerTubeVideoFormats = innerTubeVideoFormats,
                    innerTubeAudioFormats = innerTubeAudioFormats,
                    mediaId = mediaId,
                    adaptiveLadder = adaptiveLadder,
                    audioAlternatives = audioAlternatives,
                    onResolved = onResolved,
                    onStreams = onStreams,
                    onNothingToPlay = {
                        Log.e(TAG, "Failed to resolve media source - streams invalid")
                        stateFlow.value =
                            stateFlow.value.copy(
                                error = PlayerStrings.get(Res.string.error_failed_to_load_media_invalid_streams),
                                isPrepared = false,
                            )
                        exoPlayer.clearMediaItems()
                    },
                )
            if (mediaSource == null) {
                Log.e(TAG, "Failed to resolve media source - streams invalid")
                stateFlow.value = stateFlow.value.copy(error = PlayerStrings.get(Res.string.error_failed_to_load_media_invalid_streams))
                return false
            }
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            stateFlow.value = stateFlow.value.copy(isPrepared = true)
            // A SABR session starts at the position, but both engines' timelines start at 0 and only what follows the
            // position is served, so the engine seeks there too (S05), where Media3's progressive pipe would not.
            if (preservePosition != null && preservePosition > 0) {
                exoPlayer.seekTo(preservePosition)
                Log.d(TAG, "Seeking to preserved position: ${preservePosition}ms")
            }
            exoPlayer.playWhenReady = playWhenReady
            Log.d(TAG, "Media loaded successfully via VideoPlaybackResolver")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading media", e)
            stateFlow.value = stateFlow.value.copy(error = PlayerStrings.get(Res.string.error_failed_to_load_media, e.message.orEmpty()))
            return false
        }
    }

    /**
     * The next video's source for gapless advance, resolved now: the preload runs in a coroutine already, and a
     * source that cannot be built is upstream's "mediaSource failed" retry, not a player error later.
     */
    suspend fun buildPreloadMediaSource(
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        availableVideoStreams: List<VideoStream>,
        dashManifestUrl: String?,
        durationSeconds: Long,
        mediaId: String = "",
        adaptiveLadder: List<VideoStream> = emptyList(),
        audioAlternatives: List<AudioStream> = emptyList(),
        onStreams: (videoId: String, urls: Collection<String?>) -> Unit = { _, _ -> },
    ): LazyMediaSource? =
        try {
            val lazy =
                createMediaSource(
                    videoStream = videoStream,
                    audioStream = audioStream,
                    availableVideoStreams = availableVideoStreams,
                    currentVideoStream = videoStream,
                    dashManifestUrl = dashManifestUrl,
                    hlsUrl = null,
                    isLiveStream = false,
                    finalDuration = durationSeconds,
                    localFilePath = null,
                    audioOnly = false,
                    mediaId = mediaId,
                    adaptiveLadder = adaptiveLadder,
                    audioAlternatives = audioAlternatives,
                    onStreams = onStreams,
                    onNothingToPlay = null,
                )
            val resolved = lazy?.resolve?.invoke()
            resolved?.let { source -> LazyMediaSource(mediaId, failureCause = lazy.failureCause) { source } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "buildPreloadMediaSource failed", e)
            null
        }

    fun releaseSabr() {
        activeSabr?.session?.close()
        activeSabr = null
    }

    fun getActiveSabrOrchestrator(): SabrOrchestrator? = activeSabr?.orchestrator

    /**
     * What upstream's createMediaSource decides, as a source the player resolves: SABR first when preferred and the
     * default audio is kept, else a local file or the streams through the resolver, and SABR when they give nothing.
     * Null when there is nothing at all to try. [onNothingToPlay] runs, on the player's thread, when resolving finds
     * that the streams give no source after all (upstream's null source); null makes that a player error instead.
     */
    private fun createMediaSource(
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        availableVideoStreams: List<VideoStream>,
        currentVideoStream: VideoStream?,
        dashManifestUrl: String?,
        hlsUrl: String?,
        isLiveStream: Boolean,
        finalDuration: Long,
        localFilePath: String?,
        audioOnly: Boolean,
        sabrInfo: SabrStreamInfo? = null,
        sabrVideoId: String? = null,
        sabrPreferred: Boolean = false,
        startPositionMs: Long = 0L,
        innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
        innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format> = emptyList(),
        mediaId: String = "",
        adaptiveLadder: List<VideoStream> = emptyList(),
        audioAlternatives: List<AudioStream> = emptyList(),
        onResolved: (Resolution) -> Unit = {},
        onStreams: (videoId: String, urls: Collection<String?>) -> Unit,
        onNothingToPlay: (() -> Unit)?,
    ): LazyMediaSource? {
        onStreams(
            mediaId,
            availableVideoStreams.map { it.content } +
                listOfNotNull(videoStream?.content, currentVideoStream?.content, audioStream?.content) +
                innerTubeVideoFormats.map { it.url } +
                innerTubeAudioFormats.map { it.url },
        )
        val sabrAvailable =
            sabrInfo != null && sabrInfo.streamingUrl.isNotEmpty() &&
                sabrVideoId != null && sabrInfo.audioItag > 0 && sabrInfo.videoItag > 0
        val overridesDefaultAudio = StreamProcessor.overridesDefaultAudioTrack(audioStream)

        val useLadder = !audioOnly && adaptiveLadder.size > 1
        val selectedStreams =
            when {
                audioOnly -> emptyList()
                useLadder -> adaptiveLadder
                videoStream != null -> listOf(videoStream)
                !dashManifestUrl.isNullOrEmpty() && availableVideoStreams.size > 1 -> availableVideoStreams
                else -> listOfNotNull(currentVideoStream ?: availableVideoStreams.firstOrNull())
            }
        val hasDirect =
            localFilePath != null ||
                selectedStreams.isNotEmpty() ||
                audioStream != null ||
                (!audioOnly && (!dashManifestUrl.isNullOrEmpty() || (isLiveStream && !hlsUrl.isNullOrEmpty())))
        if (!hasDirect && !sabrAvailable) return null

        val resolver = VideoPlaybackResolver(mediaId)
        var published: String? = null
        return LazyMediaSource(
            mediaId = mediaId,
            isLive = isLiveStream && !audioOnly,
            failureCause = { published?.let(LocalHlsProxy::refusalFor) },
        ) {
            if (sabrAvailable && sabrPreferred && !overridesDefaultAudio) {
                createSabrMediaSource(sabrInfo!!, sabrVideoId!!, finalDuration, startPositionMs, mediaId)?.let { return@LazyMediaSource it }
            }
            val direct =
                if (localFilePath != null) {
                    LocalHlsProxy.localFileSource(localFilePath, mediaId)
                } else if (hasDirect) {
                    Log.d(TAG, "Passing ${selectedStreams.size} stream(s) to resolver: ${selectedStreams.map { "${VideoCodecUtils.qualityHeightFromStream(it)}p" }}")
                    resolver
                        .resolve(
                            selectedStreams,
                            audioStream,
                            dashManifestUrl = if (audioOnly) null else dashManifestUrl,
                            hlsUrl = if (audioOnly) null else hlsUrl,
                            durationSeconds = finalDuration,
                            isLiveStream = isLiveStream && !audioOnly,
                            asAdaptiveLadder = useLadder,
                            bitrateEstimate = bitrateEstimate(),
                            audioAlternatives = audioAlternatives,
                        ).also {
                            val resolution = Resolution(resolver.resolvedAdaptiveLadder, resolver.resolvedVideoStream)
                            resolutions.remove(mediaId)
                            resolutions[mediaId] = resolution
                            // Only the playing and the preloaded videos are ever asked about.
                            while (resolutions.size > MAX_RESOLUTIONS) resolutions.remove(resolutions.keys.first())
                            onResolved(resolution)
                        }
                } else {
                    null
                }
            if (direct != null) {
                published = direct.uri
                return@LazyMediaSource direct
            }
            if (sabrAvailable && !sabrPreferred) {
                Log.w(TAG, "No playable extractor streams — falling back to native SABR session")
                createSabrMediaSource(sabrInfo!!, sabrVideoId!!, finalDuration, startPositionMs, mediaId)?.let { return@LazyMediaSource it }
            }
            if (onNothingToPlay == null) {
                throw PlaybackException("no playable source for $mediaId", null, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED)
            }
            onNothingToPlay()
            throw CancellationException("no playable source for $mediaId")
        }
    }

    private suspend fun createSabrMediaSource(
        info: SabrStreamInfo,
        videoId: String,
        finalDuration: Long,
        startPositionMs: Long,
        mediaId: String,
    ): MediaSource? =
        try {
            releaseSabr()
            val durationMs = info.durationMs.takeIf { it > 0 } ?: (finalDuration * 1000L)
            val result =
                SabrMediaSourceFactory.create(
                    info = info,
                    videoId = videoId,
                    durationMs = durationMs,
                    startPositionMs = startPositionMs,
                    mediaId = mediaId,
                    onError = { session, _, msg, recoverable ->
                        if (!recoverable) {
                            Log.w(TAG, "SABR non-recoverable error: $msg — triggering fallback")
                            // S05: closing the proxy's session fails the engines' waits now; left open, they end only
                            // after its 60 s stall.
                            session.close()
                            onSabrFallbackNeeded?.invoke()
                        }
                    },
                )
            if (result == null) {
                null
            } else {
                activeSabr = result
                lastSourceWasSabr = true
                Log.d(TAG, "Using SABR MediaSource for $videoId (startPos=${startPositionMs}ms)")
                result.mediaSource
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "SABR MediaSource creation failed, falling back to DASH/Progressive", e)
            releaseSabr()
            null
        }
}

/** The engine for a file on the device: its video codec decides, as it does for a stream. */
internal expect fun localFileEngineKind(path: String): PlaybackEngineKind
