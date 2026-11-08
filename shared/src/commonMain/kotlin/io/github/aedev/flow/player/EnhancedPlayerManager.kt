package io.github.aedev.flow.player

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.model.SponsorBlockCategories
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.network.ProxyAwareClient
import io.github.aedev.flow.player.analytics.PlaybackAnalyticsLogger
import io.github.aedev.flow.player.analytics.RenderedFirstFrameListener
import io.github.aedev.flow.player.audio.AudioFeaturesManager
import io.github.aedev.flow.player.cache.PlayerCacheManager
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.config.VideoSizeCap
import io.github.aedev.flow.player.config.clearVideoSizeConstraints
import io.github.aedev.flow.player.config.resetVideoSizeTo
import io.github.aedev.flow.player.engine.EngineControl
import io.github.aedev.flow.player.engine.EnginePlayer
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.engine.PlaybackException
import io.github.aedev.flow.player.engine.VideoSize
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.error.PlayerErrorHandler
import io.github.aedev.flow.player.error.PlayerStrings
import io.github.aedev.flow.player.factory.EngineTrackSelector
import io.github.aedev.flow.player.factory.PlayerFactory
import io.github.aedev.flow.player.factory.ProxyBandwidthMeter
import io.github.aedev.flow.player.media.CaptionLoader
import io.github.aedev.flow.player.media.MediaLoader
import io.github.aedev.flow.player.preload.GaplessPreloadController
import io.github.aedev.flow.player.preload.PreloadTarget
import io.github.aedev.flow.player.quality.AdaptiveLadder
import io.github.aedev.flow.player.quality.LiveQualityPick
import io.github.aedev.flow.player.quality.LiveQualitySelection
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.player.recovery.ClearedMediaRecoveryState
import io.github.aedev.flow.player.recovery.StreamDenialReloader
import io.github.aedev.flow.player.resolver.AdaptiveDashManifest
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sponsorblock.SponsorBlockHandler
import io.github.aedev.flow.player.state.PlaybackCompletion
import io.github.aedev.flow.player.state.SubtitleLoadFailure
import io.github.aedev.flow.player.state.queuePresence
import io.github.aedev.flow.player.stream.CaptionTrackResolver
import io.github.aedev.flow.player.stream.InnerTubeVideoMapper
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.ResolvedCaption
import io.github.aedev.flow.player.stream.ResolvedStreamData
import io.github.aedev.flow.player.stream.ServicePlaybackStreamSelector
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.subtitle.SubtitleAutoPolicy
import io.github.aedev.flow.player.subtitle.SubtitleDelay
import io.github.aedev.flow.player.subtitle.SubtitleTracks
import io.github.aedev.flow.player.surface.SurfaceManager
import io.github.aedev.flow.player.surface.VideoSurface
import io.github.aedev.flow.player.surface.VideoSurfacePolicy
import io.github.aedev.flow.player.tracker.PlaybackTracker
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.error_unable_to_load_next_video
import io.github.aedev.flow.resources.error_unable_to_play_quality_options
import io.github.aedev.flow.utils.Log
import io.github.aedev.flow.utils.NetworkState
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Where upstream's player foreground service starts and stops for video: S11 gives it Now Playing and the
 * background audio session. Null until then, when video plays in the foreground only.
 */
interface VideoBackgroundService {
    fun start(
        videoId: String,
        title: String,
        channel: String,
        thumbnail: String,
    )

    fun stop()
}

/**
 * Upstream's video player manager, ported to iOS: the player it holds is [EnginePlayer] (ExoPlayer's role over
 * AVPlayer and the bundled engine), sources resolve through LocalHlsProxy, and captions are decoded here and handed
 * to the player since neither engine renders text. What Android owns is S11's (the media session, background
 * service, Picture in Picture) or S12's (audio effects); the advance wake lock has no iOS counterpart (S11's
 * background audio session keeps the app running). Decision row "Video player on iOS (S10)".
 */
class EnhancedPlayerManager private constructor() {
    companion object {
        private const val TAG = PlayerConfig.TAG
        private const val LIVE_EDGE_THRESHOLD_MS = 700L
        private const val SABR_QUALITY_KEY_PREFIX = "sabr:"
        private const val LIVE_QUALITY_KEY_PREFIX = "live:"
        private const val AUTO_NEXT_TAG = "FlowVideoAutoNext"

        // Deliberately the error handler's tag: these lines are what a user's diagnostics dump is read for.
        private const val DENIAL_TAG = "PlayerErrorHandler"
        private const val DENIAL_RELOAD_TIMEOUT_MS = 25_000L

        @Volatile
        private var instance: EnhancedPlayerManager? = null
        private val instanceLock = SynchronizedObject()

        fun getInstance(): EnhancedPlayerManager =
            instance ?: synchronized(instanceLock) {
                instance ?: EnhancedPlayerManager().also { instance = it }
            }

        /**
         * The engines sources open on. The iOS app registers its PlaybackEngines (AVPlayer and the bundled engine)
         * before any playback; tests register fakes.
         */
        var engineFactory: ((PlaybackEngineKind) -> EngineControl)? = null
    }

    // Core player components
    private var player: EnginePlayer? = null
    private var trackSelector: EngineTrackSelector? = null
    private var bandwidthMeter: ProxyBandwidthMeter? = null
    private val preferences by lazy { PlayerPreferences() }

    // State management
    private val _playerState = MutableStateFlow(EnhancedPlayerState())
    val playerState: StateFlow<EnhancedPlayerState> = _playerState.asStateFlow()
    val hasQueue: Flow<Boolean> = playerState.queuePresence()

    // Stream data
    private var currentVideoId: String? = null
    private var availableVideoStreams: List<VideoStream> = emptyList()
    private var availableAudioStreams: List<AudioStream> = emptyList()

    // processAudioStreams keeps one stream per track, usually Opus; DLNA casting needs the AAC ones.
    private var castAudioStreams: List<AudioStream> = emptyList()
    private val subtitleTracks = SubtitleTracks()
    private val subtitleDelay = SubtitleDelay()
    private val captionLoader by lazy { CaptionLoader(subtitleDelay) }
    private var shownCaption: ResolvedCaption? = null
    private var captionJob: Job? = null
    private var subtitleDelayVideoId: String? = null
    private val _subtitleLoadFailedEvent = MutableSharedFlow<SubtitleLoadFailure>(extraBufferCapacity = 1)
    private var currentVideoStream: VideoStream? = null
    private var currentAudioStream: AudioStream? = null
    private var innerTubeVideoFormats: List<PlayerResponse.StreamingData.Format> = emptyList()
    private var innerTubeAudioFormats: List<PlayerResponse.StreamingData.Format> = emptyList()

    // Duration and manifest info
    private var currentDurationSeconds: Long = -1
    private var currentDashManifestUrl: String? = null
    private var currentHlsUrl: String? = null
    private var currentIsLiveStream = false
    private var liveQualityHeights: List<Int> = emptyList()
    private var liveIsPortrait = false
    private var liveQualityPick: LiveQualityPick? = null
    private var videoSizeCap = VideoSizeCap.UHD

    private var pendingLiveQualityHeight: Int = 0

    /**
     * The codec the user asked for, kept for the live path.
     *
     * A VOD picks its codec when the stream is chosen, so [QualityManager] carries the preference
     * there. A livestream has no such step — the manifest is handed to the player whole — so the
     * only lever is the track selector's codec order, and it has to be re-applied on every live
     * quality change because each one rebuilds the selector parameters (#727).
     */
    private var preferredVideoCodecKey: String = "auto"
    private var lastLiveEdgeRecoveryMs = 0L
    private var preLivePlaybackSpeed: Float? = null
    private var pendingLiveDisplaySeekPositionMs: Long? = null
    private var pendingLiveDisplaySeekAtMs: Long = 0L
    private var pendingInitialLiveEdgeSeek = false

    private var currentSabrInfo: SabrStreamInfo? = null
    private var sabrPreferred = false

    private val audioOnlyMode = AudioOnlyMode()
    private var currentLocalFilePath: String? = null
    private val clearedMediaRecoveryState = ClearedMediaRecoveryState()
    private var pendingSurfaceFirstFrameStartedAtMs = 0L
    private var surfaceFirstFrameWatchdog: Job? = null

    // Queue management
    private val queue = PlaybackQueueController()
    private val abandonedSkips = AbandonedVideoSkips()
    private var manualLoopEnabled: Boolean = false
    private var globalLoopEnabled: Boolean = false

    @Volatile private var autoplayEnabled: Boolean = true

    @Volatile private var queueAutoplayEnabled: Boolean = true
    private var autoplayCandidates: List<Video> = emptyList()
    private var autoplaySourceVideoId: String? = null

    @Volatile private var relatedCandidatesSnapshot = RelatedCandidatesSnapshot()
    private var autoplayJob: Job? = null

    @Volatile private var autoplayCountdownSeconds: Int = 0

    /** Set by S11: what keeps video going and on Now Playing outside the app. */
    @Volatile
    var backgroundService: VideoBackgroundService? = null

    /** Set by the DI graph; null until then, when queue advance streams as before. */
    @Volatile
    var localCopySource: LocalCopySource? = null
    var localCaptionSource: LocalCaptionSource? = null

    /** Set by the DI graph; null until then, when autoplay hides nothing as before. */
    @Volatile
    var feedExclusionsSource: FeedExclusionsSource? = null

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val buildScope = PlayerBuildScope(scope)

    private val autoplayCountdownController =
        AutoplayCountdownController(
            scope = scope,
            onElapsed = { performAutoAdvance() },
            log = { autoNextLog(it) },
        )
    val autoplayCountdown: StateFlow<AutoplayCountdownState> = autoplayCountdownController.state

    private val preload =
        GaplessPreloadController(
            scope = scope,
            player = { player },
            currentVideoId = { currentVideoId },
            nextTarget = { nextPreloadTarget() },
            isLooping = { _playerState.value.isLooping },
            isLiveStream = { currentIsLiveStream },
            resolveStreams = { video -> resolveStreamsForVideo(video) },
            hasLocalCopy = { video -> localCopySource?.localCopyPath(video.id) != null },
            buildMediaSource = { resolved ->
                val videoStreams = StreamProcessor.processVideoStreams(resolved.videoStreams)
                mediaLoader?.buildPreloadMediaSource(
                    videoStream = resolved.videoStream,
                    audioStream = resolved.audioStream,
                    availableVideoStreams = videoStreams,
                    dashManifestUrl = resolved.dashManifestUrl,
                    durationSeconds = resolved.durationSeconds,
                    mediaId = resolved.enrichedVideo.toVideoSessionMetadata().mediaId,
                    adaptiveLadder = AdaptiveLadder.forPreload(resolved.videoStream, videoStreams, resolved.preferredCodec),
                    onStreams = { videoId, urls -> cacheManager?.registerStreams(videoId, urls) },
                )
            },
            log = { autoNextLog(it) },
        )

    private val denialReloads =
        StreamDenialReloader(
            scope = scope,
            currentVideoId = { currentVideoId },
            positionMs = { player?.currentPosition ?: 0L },
            playDownload = ::playDownloadAfterDenial,
            extract = { videoId -> withTimeoutOrNull(DENIAL_RELOAD_TIMEOUT_MS) { InnerTubeVideoStreamExtractor.extract(videoId) } },
            prepare = { videoId, extraction, startPositionMs ->
                val resume = player?.playWhenReady ?: true
                prepareExtractedStreams(videoId, extraction, startPositionMs, keepAudioOnly = audioOnlyMode.isActive)
                    .also { prepared -> if (prepared && resume) play() }
            },
            evictCache = { clearCacheForCurrentVideo() },
            abandon = ::abandonDeniedVideo,
            log = { message ->
                Log.w(DENIAL_TAG, message)
                PlayerDiagnostics.logWarning(DENIAL_TAG, message)
            },
        )
    private var pendingReloadJob: Job? = null
    private fun playerStateName(state: Int?): String =
        when (state) {
            FlowPlayer.STATE_IDLE -> "IDLE"
            FlowPlayer.STATE_BUFFERING -> "BUFFERING"
            FlowPlayer.STATE_READY -> "READY"
            FlowPlayer.STATE_ENDED -> "ENDED"
            null -> "NO_PLAYER"
            else -> "UNKNOWN($state)"
        }

    private fun autoNextSnapshot(): String {
        if (!isMainThread()) {
            val nextTarget = runCatching { nextPreloadTarget()?.video?.id }.getOrNull()
            return "main=false playerSnapshot=skipped " +
                "stateVideo=${_playerState.value.currentVideoId} managerVideo=$currentVideoId " +
                "queue=${queue.currentIndex}/${queue.size} hasNext=${hasNext()} " +
                "autoplay=$autoplayEnabled candidates=${autoplayCandidates.size} target=$nextTarget " +
                preload.diagnostics
        }
        val p = player
        val nextTarget = runCatching { nextPreloadTarget()?.video?.id }.getOrNull()
        return "video=$currentVideoId exo=${playerStateName(p?.playbackState)} " +
            "pwr=${p?.playWhenReady} playing=${p?.isPlaying} " +
            "pos=${p?.currentPosition}/${p?.duration} idx=${p?.currentMediaItemIndex} count=${p?.mediaItemCount} " +
            "queue=${queue.currentIndex}/${queue.size} hasNext=${hasNext()} " +
            "autoplay=$autoplayEnabled candidates=${autoplayCandidates.size} target=$nextTarget " +
            "${preload.diagnostics} " +
            "audioOnly=${audioOnlyMode.isActive} tracksDisabled=${audioOnlyMode.tracksDisabled} " +
            "surface=$isSurfaceReady live=$currentIsLiveStream"
    }

    private fun autoNextLog(message: String) {
        val full = "$message | ${autoNextSnapshot()}"
        Log.w(AUTO_NEXT_TAG, full)
        PlayerDiagnostics.logWarning(AUTO_NEXT_TAG, full)
    }

    /**
     * Set to true while PlaybackRefocusEffect is recovering from a screen-off/on cycle.
     * Prevents onPlaybackStateChanged(STATE_ENDED) from skipping to the next video or
     * seeking to 0 during the transient states that ExoPlayer goes through during recovery.
     */
    @Volatile private var isRecoveringFromBackground = false

    /** Call at the start of a screen-off recovery sequence (before prepare()). */
    fun beginBackgroundRecovery() {
        isRecoveringFromBackground = true
    }

    /** Call after the recovery sequence completes or is abandoned. */
    fun endBackgroundRecovery() {
        isRecoveringFromBackground = false
    }

    // Modular components
    private val playerFactory = PlayerFactory()
    private var cacheManager: PlayerCacheManager? = null
    private var qualityManager: QualityManager? = null
    private var surfaceManager: SurfaceManager? = null
    private var sponsorBlockHandler: SponsorBlockHandler? = null
    private var playbackTracker: PlaybackTracker? = null
    private var errorHandler: PlayerErrorHandler? = null

    private val _playbackAbandonedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playbackAbandonedEvent: SharedFlow<Unit> = _playbackAbandonedEvent.asSharedFlow()

    private val _decoderFailureEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val decoderFailureEvent: SharedFlow<Unit> = _decoderFailureEvent.asSharedFlow()

    private val _queueAutoAdvanceEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val queueAutoAdvanceEvent: SharedFlow<Unit> = _queueAutoAdvanceEvent.asSharedFlow()

    /** Emitted when a video plays to its end, before looping or autoplay replaces it. */
    private val _playbackCompletedEvent = MutableSharedFlow<PlaybackCompletion>(extraBufferCapacity = 4)
    val playbackCompletedEvent: SharedFlow<PlaybackCompletion> = _playbackCompletedEvent.asSharedFlow()

    private var audioFeaturesManager: AudioFeaturesManager? = null
    private var mediaLoader: MediaLoader? = null

    // Public Queue State
    val queueVideos: StateFlow<List<Video>> = queue.videos
    val currentQueueIndexState: StateFlow<Int> = queue.currentIndexState

    private val _firstFrameVideoId = MutableStateFlow<String?>(null)

    /** The video whose picture is on the surface, set by its first rendered frame. */
    val firstFrameVideoId: StateFlow<String?> = _firstFrameVideoId.asStateFlow()

    // Public surface ready state
    val isSurfaceReady: Boolean
        get() = surfaceManager?.isSurfaceReady ?: false

    // ===== Initialization =====

    fun initialize() {
        if (player == null) {
            val built = buildScope.open()
            initializeComponents()
            initializePlayer()
            setupPlayerListener()
            startPlaybackTracker()
            observePreferences(built)
            Log.d(TAG, "Player initialized")
        }
    }

    /**
     * Cold-start entry point: reads the preferences and the player's strings off the main thread, then builds the
     * player on it.
     */
    suspend fun initializeAsync() {
        if (player != null) return
        withContext(Dispatchers.IO) {
            preferences.videoLoopEnabled.first()
            playerFactory.preloadPreferences(preferences)
            PlayerStrings.preload(listOf(Res.string.error_unable_to_load_next_video, Res.string.error_unable_to_play_quality_options))
        }
        withContext(Dispatchers.Main) { initialize() }
    }

    private fun initializeComponents() {
        bandwidthMeter = playerFactory.createBandwidthMeter()
        // Initialize cache manager
        cacheManager = PlayerCacheManager({ currentVideoId }, bandwidthMeter).also { it.initialize() }

        // Initialize surface manager
        surfaceManager = SurfaceManager()

        // Initialize sponsor block handler
        sponsorBlockHandler = SponsorBlockHandler(scope)

        // Initialize audio features manager
        audioFeaturesManager = AudioFeaturesManager(scope, _playerState)

        // Initialize bandwidth meter and track selector via factory
        videoSizeCap = VideoSizeCap.forDevice()
        trackSelector = playerFactory.createTrackSelector(videoSizeCap) { player }
        mediaLoader =
            MediaLoader(_playerState, surfaceManager).also { loader ->
                loader.bitrateEstimate = { bandwidthMeter?.bitrateEstimate }
                loader.onSabrFallbackNeeded = {
                    scope.launch {
                        Log.w(TAG, "SABR fallback triggered — requesting full re-extraction")
                        // Before the stop, so the reload resumes where the session got to.
                        onStreamsDenied()
                        currentSabrInfo = null
                        loader.releaseSabr()
                        player?.stop()
                        player?.clearMediaItems()
                    }
                }
            }

        qualityManager =
            QualityManager(
                bandwidthMeter = bandwidthMeter,
                trackSelector = trackSelector,
                videoSizeCap = videoSizeCap,
                stateFlow = _playerState,
                onQualitySwitch = { stream, position ->
                    currentVideoStream = stream
                    loadMediaInternal(stream, currentAudioStream, position)
                },
            )

        // Initialize error handler
        errorHandler =
            PlayerErrorHandler(
                stateFlow = _playerState,
                onReloadStream = { position, reason -> reloadCurrentStream(position, reason) },
                onQualityDowngrade = { attemptQualityDowngrade() },
                onPlaybackShutdown = { onPlaybackShutdown() },
                isPlayingDeviceFile = { currentLocalFilePath != null },
                onStreamExpired = { onStreamsDenied() },
                onPlaybackAbandoned = { if (!skipAbandonedVideo()) scope.launch { _playbackAbandonedEvent.emit(Unit) } },
                onDecoderGaveUp = { scope.launch { _decoderFailureEvent.emit(Unit) } },
                onGatedCodecFallback = { position -> qualityManager?.fallbackToAlternateCodec(position) ?: false },
                getFailedStreamUrls = {
                    qualityManager?.let { qm ->
                        availableVideoStreams.filter { qm.hasStreamFailed(it.content) }.map { it.content }.toSet()
                    } ?: emptySet()
                },
                markStreamFailed = { url -> qualityManager?.markStreamFailed(url) },
                incrementStreamErrors = { qualityManager?.let { it.streamErrorCount } },
                getStreamErrorCount = { qualityManager?.streamErrorCount ?: 0 },
                isAdaptiveQualityEnabled = { qualityManager?.isAdaptiveQualityEnabled ?: true },
                getManualQualityHeight = { qualityManager?.manualQualityHeight },
                getCurrentVideoStream = { currentVideoStream },
                getCurrentAudioStream = { currentAudioStream },
                getAvailableAudioStreams = { availableAudioStreams },
                setCurrentAudioStream = { audio -> currentAudioStream = audio },
                setRecoveryState = { errorHandler?.setRecovery() },
                reloadPlaybackManager = { reloadPlaybackManager() },
            )

        // Initialize playback tracker
        playbackTracker =
            PlaybackTracker(
                scope = scope,
                stateFlow = _playerState,
                // skipToSegmentEnd, not a raw seek: a CLOSEST_SYNC seek lands on the keyframe before a
                // short segment, which re-arms the skip and loops the outro forever (#814).
                onSponsorBlockTick = { pos ->
                    sponsorBlockHandler?.checkForSkip(pos)?.let { skipToSegmentEnd(it) }
                },
                onBufferingDetected = {
                    qualityManager?.let { qm ->
                        qm.incrementBufferingCount()
                        if (qm.hasReachedBufferingThreshold()) {
                            qm.checkAdaptiveQualityDowngrade(forceCheck = true, player?.currentPosition ?: 0L)
                            qm.resetBufferingCount()
                        }
                    }
                },
                onSmoothPlayback = { qualityManager?.resetBufferingCount() },
                onBandwidthCheckNeeded = {
                    qualityManager?.let { qm ->
                        if (qm.shouldCheckBandwidth()) {
                            qm.updateBandwidthCheckTime()
                            val position = player?.currentPosition ?: 0L
                            qm.checkAdaptiveQualityDowngrade(forceCheck = false, position)
                            qm.checkAdaptiveQualityUpgrade(position)
                        }
                    }
                },
                onLivePlaybackTick = { exoPlayer ->
                    // The SABR playhead follows what the engines ask the proxy for (S05); setting it here too would fight it.
                    updateLiveEdgeState(exoPlayer)
                    if (currentIsLiveStream &&
                        _playerState.value.playbackSpeed > 1.0f &&
                        isPlayerAtLiveEdge(exoPlayer)
                    ) {
                        setPlaybackSpeed(1.0f)
                    }
                },
            )
    }

    /**
     * Upstream reads the source's kind as loadMedia returns; here the source resolves when the player prepares, so
     * MediaLoader reports it then. A ladder the player adapts over turns QualityManager's own checks off; one rung of a
     * ladder (the bundled engine's) becomes the stream Auto steps from.
     */
    private fun applyResolution(resolution: MediaLoader.Resolution) {
        qualityManager?.isAdaptiveLadderActive = resolution.adaptiveLadder
        val rung = resolution.videoStream ?: return
        if (qualityManager?.isAdaptiveQualityEnabled != true) return
        currentVideoStream = rung
        qualityManager?.setCurrentStream(rung)
    }

    private fun initializePlayer() {
        val engines = engineFactory ?: error("no engine factory registered: the app sets EnhancedPlayerManager.engineFactory")
        player = EnginePlayer(scope, playerFactory.createLoadControl(preferences), engines)
        player?.addListener(PlaybackAnalyticsLogger(TAG, player = { player }) { currentVideoId })
        player?.addListener(
            RenderedFirstFrameListener(renderedMediaId = { player?.currentMediaId }, currentVideoId = { currentVideoId }) { videoId ->
                _firstFrameVideoId.value = videoId
            },
        )
        trackSelector?.apply()
        audioFeaturesManager?.setPlayer(player!!)
        scope.launch {
            val loopEnabled = preferences.videoLoopEnabled.first()
            player?.repeatMode = if (loopEnabled) FlowPlayer.REPEAT_MODE_ONE else FlowPlayer.REPEAT_MODE_OFF
            if (preferences.rememberPlaybackSpeed.first()) {
                val savedSpeed = preferences.playbackSpeed.first()
                if (savedSpeed != 1.0f) setPlaybackSpeed(savedSpeed)
            }
        }
        surfaceManager?.reattachSurfaceIfValid(player)
    }

    fun setVolumeBoost(volume: Float) {
        audioFeaturesManager?.setVolumeBoost(player, volume)
    }

    /** YouTube's loudness for [videoId], in dB; null when the stream carries none. */
    fun setLoudness(
        videoId: String,
        gainDb: Float?,
    ) {
        audioFeaturesManager?.setLoudness(videoId, gainDb)
    }

    private fun observePreferences(built: CoroutineScope) {
        audioFeaturesManager?.observeSkipSilencePreference(preferences, built)
        audioFeaturesManager?.observeVolumeNormalizationPreference(preferences, built)
        val prefs = preferences
        built.launch {
            prefs.sponsorBlockEnabled.collect { isEnabled ->
                sponsorBlockHandler?.setEnabled(isEnabled)
            }
        }
        sponsorBlockHandler?.let { handler ->
            built.launch {
                handler.muteEvent.collect { muted -> if (muted) player?.mute() else player?.unmute() }
            }
        }

        built.launch {
            prefs.videoLoopEnabled.collect { isEnabled ->
                globalLoopEnabled = isEnabled
                player?.repeatMode = if (isEnabled) FlowPlayer.REPEAT_MODE_ONE else FlowPlayer.REPEAT_MODE_OFF
                updateEffectiveLoopState()
            }
        }

        built.launch {
            combine(prefs.autoEnableSubtitles, prefs.preferredSubtitleLanguage) { enabled, language ->
                SubtitleAutoPolicy(enabled, language)
            }.collect { policy ->
                if (subtitleTracks.setPolicy(policy)) {
                    publishSubtitles()
                    applySubtitleTrackSelection()
                }
            }
        }

        built.launch {
            prefs.autoplayEnabled.collect { isEnabled ->
                autoplayEnabled = isEnabled
            }
        }

        built.launch {
            prefs.queueAutoplayEnabled.collect { isEnabled ->
                queueAutoplayEnabled = isEnabled
            }
        }

        built.launch {
            prefs.autoplayCountdownSeconds.collect { seconds ->
                val previousSeconds = autoplayCountdownSeconds
                autoplayCountdownSeconds = seconds
                if (seconds > 0) {
                    preload.clear()
                } else if (previousSeconds > 0 && player?.playbackState == FlowPlayer.STATE_READY) {
                    preload.request("autoplay-countdown-disabled")
                }
            }
        }

        // Collect per-category SponsorBlock actions and update handler
        SponsorBlockCategories.all.forEach { category ->
            built.launch {
                prefs.sbActionForCategory(category).collect { action ->
                    val current = sponsorBlockHandler?.categoryActions?.toMutableMap() ?: mutableMapOf()
                    current[category] = action
                    sponsorBlockHandler?.categoryActions = current
                }
            }
        }
    }

    // ===== Player Listener =====

    private fun setupPlayerListener() {
        player?.addListener(
            object : FlowPlayer.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0) {
                        _playerState.value =
                            _playerState.value.copy(
                                effectiveQuality = VideoCodecUtils.qualityClass(videoSize.width, videoSize.height),
                            )
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val exoLive = player?.isCurrentMediaItemLive == true
                    _playerState.value =
                        _playerState.value.copy(
                            isBuffering = playbackState == FlowPlayer.STATE_BUFFERING,
                            playWhenReady = player?.playWhenReady ?: false,
                            hasEnded = playbackState == FlowPlayer.STATE_ENDED && !isRecoveringFromBackground && !exoLive,
                        )
                    if (playbackState == FlowPlayer.STATE_READY ||
                        playbackState == FlowPlayer.STATE_ENDED ||
                        playbackState == FlowPlayer.STATE_BUFFERING
                    ) {
                        autoNextLog(
                            "onPlaybackStateChanged ${playerStateName(playbackState)} " +
                                "recovering=$isRecoveringFromBackground live=$exoLive",
                        )
                    }

                    if (playbackState == FlowPlayer.STATE_ENDED && !isRecoveringFromBackground && exoLive) {
                        val now = elapsedRealtime()
                        if (now - lastLiveEdgeRecoveryMs < 2500L) {
                            autoNextLog("STATE_ENDED on live again too soon -> stop recovering")
                            _playerState.value = _playerState.value.copy(hasEnded = true)
                        } else {
                            lastLiveEdgeRecoveryMs = now
                            autoNextLog("STATE_ENDED on live -> seekToLiveEdge")
                            seekToLiveEdge(resetSpeed = false)
                            player?.play()
                        }
                        return
                    }

                    if (playbackState == FlowPlayer.STATE_ENDED && !isRecoveringFromBackground) {
                        autoNextLog("STATE_ENDED branch entered")
                        emitPlaybackCompletion()
                        if (_playerState.value.isLooping) {
                            autoNextLog("STATE_ENDED loop replay")
                            player?.seekTo(0)
                            player?.play()
                        } else {
                            maybeStartAutoplayCountdownOrAdvance()
                        }
                    }

                    if (playbackState == FlowPlayer.STATE_BUFFERING) {
                        logBandwidthInfo()
                    }

                    if (playbackState == FlowPlayer.STATE_READY) {
                        autoNextLog("STATE_READY scheduling preload")
                        preload.schedule()
                    }

                    if (playbackState == FlowPlayer.STATE_READY && pendingInitialLiveEdgeSeek) {
                        player?.let { exoPlayer ->
                            if (currentIsLiveStream || exoPlayer.isCurrentMediaItemLive) {
                                pendingInitialLiveEdgeSeek = false
                                exoPlayer.seekToDefaultPosition()
                                val liveEdgePosition =
                                    exoPlayer.duration
                                        .takeIf { it > 0L && it != FlowPlayer.TIME_UNSET }
                                        ?: exoPlayer.currentPosition.coerceAtLeast(0L)
                                markLiveDisplaySeek(liveEdgePosition)
                                updateLiveEdgeState(exoPlayer)
                            }
                        }
                    }
                }

                override fun onMediaItemTransition(
                    mediaId: String?,
                    reason: Int,
                ) {
                    autoNextLog("onMediaItemTransition reason=$reason mediaId=$mediaId")
                    audioFeaturesManager?.onMediaItemChanged()
                    if (reason == FlowPlayer.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == FlowPlayer.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    ) {
                        val idx = player?.currentMediaItemIndex ?: 0
                        if (idx >= 1 && preload.preloaded != null) {
                            promotePreloadedItem()
                        }
                    }
                }

                override fun onRenderedFirstFrame() {
                    Log.d(TAG, "First frame rendered - video renderer working")
                    surfaceManager?.setSurfaceReady(true)
                    errorHandler?.onFirstFrameRendered()
                    surfaceFirstFrameWatchdog?.cancel()
                    surfaceFirstFrameWatchdog = null
                    pendingSurfaceFirstFrameStartedAtMs.takeIf { it > 0L }?.let { startedAtMs ->
                        pendingSurfaceFirstFrameStartedAtMs = 0L
                        Log.w(
                            "FlowVideoLifecycle",
                            "firstFrameAfterSurfaceAttach latencyMs=${elapsedRealtime() - startedAtMs} " +
                                "video=$currentVideoId pos=${player?.currentPosition} pwr=${player?.playWhenReady}",
                        )
                    }
                    val rendererAvailable = isVideoRendererAvailable()
                    Log.d(TAG, "Video renderer confirmed available after first frame: $rendererAvailable")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                    if (isPlaying) abandonedSkips.onPlaybackStarted()
                    autoNextLog("onIsPlayingChanged isPlaying=$isPlaying")
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    _playerState.value = _playerState.value.copy(playWhenReady = playWhenReady)
                    autoNextLog("onPlayWhenReadyChanged playWhenReady=$playWhenReady reason=$reason")
                }

                override fun onPlayerError(error: PlaybackException) {
                    errorHandler?.handleError(error, player)
                }

                override fun onCues(cues: List<io.github.aedev.flow.player.renderer.subtitle.Cue>) {
                    _currentCues.value = cues
                }
            },
        )
    }

    private fun startPlaybackTracker() {
        player?.let { playbackTracker?.start(it) }
    }

    // ===== Offline / Local File Playback =====

    /**
     * Play a local (downloaded) file directly, bypassing all stream requirements.
     * Muxed MP4 files are self-contained with both audio and video tracks.
     *
     * @param savedSegments Optional SponsorBlock segments loaded from local DB.
     *   When non-empty, they are applied directly without a network call, enabling
     *   offline sponsor-skip. Pass null to fall back to fetching from the API.
     */
    fun playLocalFile(
        videoId: String,
        filePath: String,
        savedSegments: List<SponsorBlockSegment>? = null,
        preservePosition: Long? = null,
        subtitles: List<ResolvedCaption> = emptyList(),
    ) {
        Log.d(TAG, "playLocalFile: videoId=$videoId, path=$filePath, offlineSegments=${savedSegments?.size}, resumePos=$preservePosition")
        resetPlaybackStateForNewVideo(videoId)
        updateLivePlaybackMode(isLive = false)
        configureTrackSelectorForLocalFile()
        currentVideoId = videoId
        currentLocalFilePath = filePath
        subtitleTracks.load(videoId, StreamProcessor.processCaptions(subtitles), acceptsEmbedded = true)
        publishSubtitles()
        startPlaybackTracker()

        // Stored segments win even when empty (looked up, none found); a device file has none to look up.
        sponsorBlockHandler?.reset()
        when {
            savedSegments != null -> sponsorBlockHandler?.loadSegmentsFromList(videoId, savedSegments)
            LocalMediaIds.isLocal(videoId) -> Unit
            else -> sponsorBlockHandler?.loadSegments(videoId)
        }

        loadMediaInternal(
            videoStream = null,
            audioStream = null,
            localFilePath = filePath,
            preservePosition = preservePosition,
        )
    }

    /** Adds a subtitle file to the device file that is playing and shows it, where it was. */
    fun addLocalCaption(caption: ResolvedCaption): Boolean {
        val videoId = currentVideoId ?: return false
        val path = currentLocalFilePath ?: return false
        val position = player?.currentPosition ?: 0L
        val playWhenReady = player?.playWhenReady ?: true
        subtitleTracks.load(videoId, StreamProcessor.processCaptions(subtitleTracks.captions + caption), acceptsEmbedded = true)
        subtitleTracks.select(caption.url)
        publishSubtitles()
        return loadMediaInternal(
            videoStream = null,
            audioStream = null,
            localFilePath = path,
            preservePosition = position,
            playWhenReady = playWhenReady,
        )
    }

    // ===== Stream Management =====

    suspend fun setStreams(
        videoId: String,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        subtitles: List<ResolvedCaption>,
        durationSeconds: Long = -1,
        dashManifestUrl: String? = null,
        localFilePath: String? = null,
        hlsUrl: String? = null,
        streamType: StreamType? = null,
        startPosition: Long = 0L,
        sabrInfo: SabrStreamInfo? = null,
        itVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        itAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        preferredVideoCodec: String = "auto",
        keepAudioOnly: Boolean = false,
        preferSabr: Boolean = false,
        preferredLiveQualityHeight: Int = 0,
    ) {
        if (!isMainThread()) {
            autoNextLog("setStreams switching to main id=$videoId")
            withContext(Dispatchers.Main) {
                setStreams(
                    videoId = videoId,
                    videoStream = videoStream,
                    audioStream = audioStream,
                    videoStreams = videoStreams,
                    audioStreams = audioStreams,
                    subtitles = subtitles,
                    durationSeconds = durationSeconds,
                    dashManifestUrl = dashManifestUrl,
                    localFilePath = localFilePath,
                    hlsUrl = hlsUrl,
                    streamType = streamType,
                    startPosition = startPosition,
                    sabrInfo = sabrInfo,
                    itVideoFormats = itVideoFormats,
                    itAudioFormats = itAudioFormats,
                    preferredVideoCodec = preferredVideoCodec,
                    keepAudioOnly = keepAudioOnly,
                    preferSabr = preferSabr,
                    preferredLiveQualityHeight = preferredLiveQualityHeight,
                )
            }
            return
        }
        // Cold start builds the player asynchronously, so a video opened before that lands must
        // finish initialization here rather than have its streams silently dropped.
        if (player == null) initialize()
        Log.d(
            TAG,
            "setStreams(id=$videoId, " +
                "videoHeight=${videoStream?.let(VideoCodecUtils::qualityHeightFromStream)}, " +
                "sabr=${sabrInfo != null}, preferSabr=$preferSabr, " +
                "itVideo=${itVideoFormats.size}, itAudio=${itAudioFormats.size}, " +
                "keepAudioOnly=$keepAudioOnly)",
        )
        resetPlaybackStateForNewVideo(videoId)
        preferredVideoCodecKey = preferredVideoCodec
        currentLocalFilePath = localFilePath
        if (localFilePath != null) configureTrackSelectorForLocalFile()
        currentSabrInfo = sabrInfo
        sabrPreferred = preferSabr
        innerTubeVideoFormats = itVideoFormats
        innerTubeAudioFormats = itAudioFormats
        audioOnlyMode.applyStreams(keepAudioOnly)
        setVideoTracksDisabled(keepAudioOnly)

        // Reset and load SponsorBlock
        sponsorBlockHandler?.reset()
        sponsorBlockHandler?.loadSegments(videoId)

        this.currentDurationSeconds = durationSeconds
        this.currentDashManifestUrl = dashManifestUrl
        val useLiveManifest =
            streamType == StreamType.LIVE_STREAM ||
                streamType == StreamType.POST_LIVE_STREAM
        this.currentHlsUrl = hlsUrl.takeIf { useLiveManifest }
        val liveDurationMs =
            if (!currentHlsUrl.isNullOrEmpty() && durationSeconds > 0) {
                durationSeconds * 1000L
            } else {
                0L
            }
        val isLiveStream =
            useLiveManifest &&
                (!currentHlsUrl.isNullOrEmpty() || !currentDashManifestUrl.isNullOrEmpty())
        liveQualityPick = LiveQualitySelection.retainedPick(videoId, liveQualityPick)
        pendingLiveQualityHeight =
            if (isLiveStream) LiveQualitySelection.targetHeight(videoId, liveQualityPick, preferredLiveQualityHeight) else 0
        if (isLiveStream) {
            applyLiveCodecPreference()
            currentHlsUrl?.let(::loadLiveQualityOptions)
        }
        updateLivePlaybackMode(isLive = isLiveStream, forceLiveSpeedReset = true)
        pendingInitialLiveEdgeSeek = streamType == StreamType.LIVE_STREAM
        currentVideoId = videoId

        // Process streams using StreamProcessor
        availableVideoStreams = StreamProcessor.processVideoStreams(videoStreams)
        availableAudioStreams = StreamProcessor.processAudioStreams(audioStreams)
        castAudioStreams = audioStreams
        subtitleTracks.load(videoId, StreamProcessor.processCaptions(subtitles), acceptsEmbedded = localFilePath != null)
        if (audioStream == null && availableAudioStreams.isEmpty()) {
            Log.w(TAG, "setStreams: no separate audio stream for $videoId; attempting video-only/muxed playback")
        }

        // Ensure playback tracker is running
        startPlaybackTracker()

        // Update quality manager with available streams
        qualityManager?.setAvailableStreams(availableVideoStreams)
        qualityManager?.preferredCodecKey = preferredVideoCodec
        qualityManager?.isDashSource = !currentDashManifestUrl.isNullOrEmpty()

        // Quality selection: respect user preference
        if (videoStream != null) {
            currentVideoStream = videoStream
            qualityManager?.setCurrentStream(currentVideoStream)
            qualityManager?.setManualMode(VideoCodecUtils.qualityHeightFromStream(videoStream))
        } else {
            val smartStream = qualityManager?.selectSmartInitialQuality()
            currentVideoStream = smartStream ?: availableVideoStreams.firstOrNull()
            qualityManager?.setCurrentStream(currentVideoStream)
        }
        currentAudioStream = audioStream

        val isAutoMode = (videoStream == null)

        // Update state with available options
        _playerState.value =
            _playerState.value.copy(
                currentVideoId = videoId,
                sourceVideoAspectRatio = resolveSourceVideoAspectRatio(),
                effectiveQuality =
                    currentVideoStream
                        ?.let(VideoCodecUtils::qualityHeightFromStream)
                        ?.let(QualityManager::normalizeQualityHeight)
                        ?: 0,
                availableQualities = buildAvailableQualityOptions(),
                availableAudioTracks = StreamProcessor.toAudioTrackOptions(availableAudioStreams),
                availableSubtitles = subtitleTracks.options,
                selectedSubtitleUrl = subtitleTracks.selected?.url,
                currentQuality =
                    if (isAutoMode) {
                        0
                    } else {
                        currentVideoStream
                            ?.let(VideoCodecUtils::qualityHeightFromStream)
                            ?.let(QualityManager::normalizeQualityHeight)
                            ?: 0
                    },
                currentQualityKey = if (isAutoMode) null else currentVideoStream?.content?.takeIf { it.isNotBlank() },
                currentAudioTrack = StreamProcessor.indexOfAudioTrack(availableAudioStreams, currentAudioStream),
                isLive = currentIsLiveStream,
                isAtLiveEdge = false,
                liveDurationMs = liveDurationMs,
            )

        val resumePos = startPosition.takeIf { it > 0L }
        when {
            localFilePath != null -> loadMediaInternal(null, audioStream, localFilePath = localFilePath, preservePosition = resumePos)
            currentVideoStream != null -> loadMediaInternal(currentVideoStream, currentAudioStream, preservePosition = resumePos)
            else -> loadMediaInternal(null, currentAudioStream ?: audioStream, preservePosition = resumePos)
        }
    }

    /**
     * The video the viewer opened paused. It waits for [play] however often its media is reloaded
     * (stream expiry, a quality or audio-track change) and stops applying once another video loads.
     */
    private var startPausedVideoId: String? = null

    /** Opens [videoId] paused, or clears that when null. */
    fun armStartPaused(videoId: String?) {
        startPausedVideoId = videoId
    }

    fun isStartPausedArmed(videoId: String?): Boolean = videoId != null && videoId == startPausedVideoId

    private fun resetPlaybackStateForNewVideo(videoId: String) {
        if (startPausedVideoId != videoId) startPausedVideoId = null
        clearAutoplayCountdownInternal()
        currentVideoId = videoId
        liveQualityHeights = emptyList()
        liveIsPortrait = false
        pendingLiveQualityHeight = 0
        lastLiveEdgeRecoveryMs = 0L
        qualityManager?.resetForNewVideo()
        playbackTracker?.reset()
        errorHandler?.resetForNewVideo()
        mediaLoader?.releaseSabr()
        currentSabrInfo = null
        sabrPreferred = false
        currentLocalFilePath = null
        clearedMediaRecoveryState.clear()
        innerTubeVideoFormats = emptyList()
        innerTubeAudioFormats = emptyList()
        currentVideoStream = null
        currentAudioStream = null
        currentDashManifestUrl = null
        currentHlsUrl = null
        subtitleTracks.load(videoId, emptyList(), acceptsEmbedded = false)
        resetSubtitleDelayFor(videoId)
        applySubtitleTrackSelection()
        pendingLiveDisplaySeekPositionMs = null
        pendingLiveDisplaySeekAtMs = 0L
        pendingInitialLiveEdgeSeek = false

        player?.stop()

        _playerState.value =
            _playerState.value.copy(
                currentVideoId = videoId,
                isBuffering = true,
                error = null,
                hasEnded = false,
                isPrepared = false,
                recoveryAttempted = false,
                currentQuality = 0,
                currentQualityKey = null,
                sourceVideoAspectRatio = null,
                playWhenReady = player?.playWhenReady ?: true,
                isAtLiveEdge = false,
                liveDurationMs = 0L,
                availableSubtitles = emptyList(),
                selectedSubtitleUrl = null,
            )
    }

    private fun updateLivePlaybackMode(
        isLive: Boolean,
        forceLiveSpeedReset: Boolean = false,
    ) {
        player?.setSeekParameters(exact = isLive)

        if (isLive == currentIsLiveStream) {
            if (isLive && forceLiveSpeedReset) {
                setPlaybackSpeed(1.0f)
            }
            _playerState.value =
                _playerState.value.copy(
                    isLive = isLive,
                    liveDurationMs = if (isLive) _playerState.value.liveDurationMs else 0L,
                )
            return
        }

        if (isLive) {
            val currentSpeed = _playerState.value.playbackSpeed
            if (currentSpeed != 1.0f) {
                preLivePlaybackSpeed = currentSpeed
            }
            setPlaybackSpeed(1.0f)
        } else {
            preLivePlaybackSpeed?.let { speed ->
                if (speed != 1.0f) {
                    setPlaybackSpeed(speed)
                }
            }
            preLivePlaybackSpeed = null
        }

        currentIsLiveStream = isLive
        _playerState.value =
            _playerState.value.copy(
                isLive = isLive,
                isAtLiveEdge = false,
                liveDurationMs = if (isLive) _playerState.value.liveDurationMs else 0L,
            )
    }

    private fun loadMediaInternal(
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        preservePosition: Long? = null,
        localFilePath: String? = null,
        audioOnly: Boolean = false,
        playWhenReady: Boolean = !isStartPausedArmed(currentVideoId),
    ): Boolean {
        val loaded =
            prepareMediaSource(
                videoStream = videoStream,
                audioStream = audioStream,
                preservePosition = preservePosition,
                localFilePath = localFilePath,
                audioOnly = audioOnly,
                playWhenReady = playWhenReady,
            )
        if (!loaded) {
            // The previous item is still queued (see resetPlaybackStateForNewVideo). Drop it so a
            // failed load doesn't leave the notification advertising the video we just left.
            player?.clearMediaItems()
        }
        return loaded
    }

    private fun prepareMediaSource(
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        preservePosition: Long?,
        localFilePath: String?,
        audioOnly: Boolean,
        playWhenReady: Boolean,
    ): Boolean {
        autoNextLog("loadMediaInternal audioOnly=$audioOnly preserve=$preservePosition local=${localFilePath != null}")
        preload.clear()
        if (audioOnly || audioOnlyMode.isActive) {
            setVideoTracksDisabled(true)
        } else if (videoStream != null || localFilePath != null) {
            setVideoTracksDisabled(false)
        }

        val sessionMetadata = GlobalPlayerState.currentVideo.value?.toVideoSessionMetadata()

        if (localFilePath != null) {
            Log.d(TAG, "loadMediaInternal: Playing local file: $localFilePath")
            qualityManager?.isAdaptiveLadderActive = false
            return mediaLoader?.loadMedia(
                player = player,
                videoStream = videoStream,
                audioStream = audioStream ?: availableAudioStreams.firstOrNull(),
                availableVideoStreams = availableVideoStreams,
                currentVideoStream = currentVideoStream,
                dashManifestUrl = null,
                hlsUrl = null,
                isLiveStream = false,
                durationSeconds = currentDurationSeconds,
                currentDurationSeconds = currentDurationSeconds,
                preservePosition = preservePosition,
                localFilePath = localFilePath,
                audioOnly = false,
                playWhenReady = playWhenReady,
                mediaId = sessionMetadata?.mediaId.orEmpty(),
                onStreams = { videoId, urls -> cacheManager?.registerStreams(videoId, urls) },
            ) ?: false
        }

        val audio = audioStream ?: availableAudioStreams.firstOrNull()
        val hasSabrSession = currentSabrInfo != null
        if (audioOnly && audio == null && !hasSabrSession) {
            Log.w(TAG, "loadMediaInternal: audio-only load requested without an audio stream")
            return false
        }
        val hasPlayableVideo =
            videoStream != null ||
                currentVideoStream != null ||
                availableVideoStreams.isNotEmpty() ||
                hasSabrSession ||
                !currentDashManifestUrl.isNullOrEmpty() ||
                !currentHlsUrl.isNullOrEmpty()
        if (audio == null && !hasPlayableVideo) {
            Log.w(TAG, "loadMediaInternal: no playable audio/video streams")
            return false
        }
        val ladder =
            if (audioOnly || currentIsLiveStream) {
                emptyList()
            } else {
                qualityManager?.adaptiveLadder(videoStream ?: currentVideoStream).orEmpty()
            }
        // Not a ladder until the source says so when it resolves (a SABR session never does), as upstream's flag reads.
        qualityManager?.isAdaptiveLadderActive = false
        val result =
            mediaLoader?.loadMedia(
                player = player,
                videoStream = videoStream,
                audioStream = audio,
                availableVideoStreams = availableVideoStreams,
                currentVideoStream = currentVideoStream,
                dashManifestUrl = currentDashManifestUrl,
                hlsUrl = currentHlsUrl,
                isLiveStream = currentIsLiveStream,
                durationSeconds = currentDurationSeconds,
                currentDurationSeconds = currentDurationSeconds,
                preservePosition = preservePosition,
                localFilePath = localFilePath,
                audioOnly = audioOnly,
                playWhenReady = playWhenReady,
                sabrInfo = currentSabrInfo,
                sabrVideoId = currentVideoId,
                sabrPreferred = sabrPreferred,
                innerTubeVideoFormats = innerTubeVideoFormats,
                innerTubeAudioFormats = innerTubeAudioFormats,
                mediaId = sessionMetadata?.mediaId.orEmpty(),
                adaptiveLadder = ladder,
                onStreams = { videoId, urls -> cacheManager?.registerStreams(videoId, urls) },
                onResolved = ::applyResolution,
            ) ?: false
        if (result) {
            qualityManager?.isDashSource = !currentDashManifestUrl.isNullOrEmpty()
        }
        return result
    }

    private fun setVideoTracksDisabled(disabled: Boolean) {
        if (!audioOnlyMode.setTracksDisabled(disabled)) return
        autoNextLog("setVideoTracksDisabled disabled=$disabled")
        player?.setVideoEnabled(!disabled)
        mediaLoader?.getActiveSabrOrchestrator()?.setAudioOnly(disabled)
    }

    private fun configureTrackSelectorForLocalFile() {
        trackSelector?.let { selector ->
            selector.parameters =
                selector.parameters
                    .copy(preferredVideoMimeTypes = emptyList())
                    .clearVideoSizeConstraints()
                    .clearViewportSizeConstraints()
        }
    }

    // ===== Queue Management =====

    fun setQueue(
        videos: List<Video>,
        startIndex: Int,
        title: String? = null,
        shuffle: Boolean? = null,
    ) {
        if (!isMainThread()) {
            autoNextLog("setQueue posted to main size=${videos.size} start=$startIndex")
            scope.launch { setQueue(videos, startIndex, title, shuffle) }
            return
        }
        val startVideo = queue.setQueue(videos, startIndex, title, shuffle)
        autoNextLog("setQueue size=${videos.size} start=${queue.currentIndex} title=$title")

        updateQueueState()

        if (startVideo != null) {
            startPlaybackFromQueue(startVideo, loadStreamsInPlayer = false)
            preload.request("queue-set")
        }
    }

    fun playNext(loadStreamsInPlayer: Boolean = true): Boolean {
        val nextIndex =
            queue.nextIndex() ?: run {
                autoNextLog("playNext queue unavailable")
                return false
            }
        if (nextIndex == queue.currentIndex) {
            replay()
            return true
        }
        val nextVideo = queue.videoAt(nextIndex) ?: return false
        if (advanceToPreloadedItem(nextVideo.id)) {
            autoNextLog("playNext using preloaded window next=${nextVideo.id}")
            return true
        }
        autoNextLog("playNext queue loadStreams=$loadStreamsInPlayer")
        queue.moveTo(nextIndex)
        startPlaybackFromQueue(nextVideo, loadStreamsInPlayer)
        updateQueueState()
        return true
    }

    fun playPrevious(loadStreamsInPlayer: Boolean = true): Boolean {
        val previousVideo = queue.movePrevious() ?: return false
        startPlaybackFromQueue(previousVideo, loadStreamsInPlayer)
        updateQueueState()
        return true
    }

    fun hasNext(): Boolean = queue.hasNext

    /**
     * The playing video's streams were refused and could not be swapped in place. The player owns
     * the reload, so it runs whether or not a screen is attached (#921).
     */
    private fun onStreamsDenied() = denialReloads.onStreamsDenied()

    /** Plays [videoId]'s download, when it has one, instead of fetching the refused streams again. */
    private suspend fun playDownloadAfterDenial(
        videoId: String,
        startPositionMs: Long,
    ): Boolean {
        val path = localCopySource?.localCopyPath(videoId) ?: return false
        val local = localCaptionSource?.captionsFor(videoId, path)
        val resume = player?.playWhenReady ?: true
        playLocalFile(
            videoId = videoId,
            filePath = path,
            preservePosition = startPositionMs,
            subtitles = local?.captions.orEmpty(),
        )
        local?.offsetMs?.takeIf { it != 0L }?.let(::setSubtitleOffset)
        if (audioOnlyMode.isActive) {
            audioOnlyMode.applyStreams(true)
            setVideoTracksDisabled(true)
        }
        if (resume) play()
        return true
    }

    private fun abandonDeniedVideo(videoId: String) {
        scope.launch(Dispatchers.IO) { preferences.markVideoUnplayable(videoId) }
        if (skipAbandonedVideo()) return
        player?.stop()
        player?.clearMediaItems()
        scope.launch { _playbackAbandonedEvent.emit(Unit) }
    }

    /** A user asked for playback (play, retry): the denied-stream budget starts over. */
    fun resetStreamRecovery() = denialReloads.onPlaybackRequested()

    fun onStreamLoadStarted(videoId: String) = denialReloads.onLoadStarted(videoId)

    /** True while [videoId]'s refused streams are being reloaded; nothing else should prepare it meanwhile. */
    fun isRecoveringStreams(videoId: String?): Boolean = denialReloads.isReloading(videoId)

    /**
     * Moves a queue past a video whose streams could not be recovered, instead of stopping the
     * whole playlist on it. False when there is nothing to move to or too many failed in a row.
     */
    fun skipAbandonedVideo(): Boolean {
        if (!abandonedSkips.trySkip(hasNext())) return false
        PlayerDiagnostics.logWarning(TAG, "Streams for $currentVideoId could not be recovered; moving to the next video in the queue")
        // Posted so the failing load has unwound before the next one starts.
        scope.launch { playNext(loadStreamsInPlayer = true) }
        return true
    }

    fun hasPrevious(): Boolean = queue.hasPrevious || (player?.currentPosition ?: 0) > 3000

    /** True when the queue advanced to [videoId]; such items start from the beginning instead of resuming. */
    fun isReachedByQueueAdvance(videoId: String): Boolean = queue.isReachedByAdvance(videoId)

    /**
     * Insert [video] immediately after the current position (Play Next).
     * If no queue is active but a video is currently playing, silently build a
     * [currentVideo, video] queue starting at index 0 — no playback interruption.
     * If nothing is playing at all, start the video immediately.
     */
    fun addVideoToQueueNext(video: Video) {
        if (!isMainThread()) {
            autoNextLog("addVideoToQueueNext posted to main video=${video.id}")
            scope.launch { addVideoToQueueNext(video) }
            return
        }
        autoNextLog("addVideoToQueueNext ${video.id}")
        when (queue.addNext(video, GlobalPlayerState.currentVideo.value)) {
            QueueAddOutcome.NoActiveQueue -> setQueue(listOf(video), 0)
            QueueAddOutcome.QueueCreated -> onQueueMutated("queue-created-play-next")
            QueueAddOutcome.Inserted -> onQueueMutated("queue-play-next")
        }
    }

    /**
     * Append [video] to the end of the current queue.
     * If no queue is active but a video is currently playing, silently build a
     * [currentVideo, video] queue starting at index 0 — no playback interruption.
     * If nothing is playing at all, start the video immediately.
     */
    fun addVideoToQueue(video: Video) {
        if (!isMainThread()) {
            autoNextLog("addVideoToQueue posted to main video=${video.id}")
            scope.launch { addVideoToQueue(video) }
            return
        }
        autoNextLog("addVideoToQueue ${video.id}")
        when (queue.append(video, GlobalPlayerState.currentVideo.value)) {
            QueueAddOutcome.NoActiveQueue -> setQueue(listOf(video), 0)
            QueueAddOutcome.QueueCreated -> onQueueMutated("queue-created-add")
            QueueAddOutcome.Inserted -> onQueueMutated("queue-add")
        }
    }

    fun playVideoAtIndex(
        index: Int,
        loadStreamsInPlayer: Boolean = true,
    ) {
        if (index == queue.currentIndex) return
        val video = queue.moveTo(index) ?: return
        startPlaybackFromQueue(video, loadStreamsInPlayer)
        updateQueueState()
    }

    /** @return what an undo needs to put the video back; null when nothing was removed or the call was posted. */
    fun removeVideoAtIndex(index: Int): RemovedQueueEntry? {
        if (!isMainThread()) {
            scope.launch { removeVideoAtIndex(index) }
            return null
        }
        val removed = queue.removeAt(index) ?: return null

        preload.clear()
        onQueueMutated("queue-remove")
        return removed
    }

    fun restoreRemovedVideo(entry: RemovedQueueEntry) {
        if (!isMainThread()) {
            scope.launch { restoreRemovedVideo(entry) }
            return
        }
        if (!queue.restore(entry)) return

        preload.clear()
        onQueueMutated("queue-restore")
    }

    fun moveVideoAtIndex(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (!isMainThread()) {
            scope.launch { moveVideoAtIndex(fromIndex, toIndex) }
            return
        }
        if (!queue.move(fromIndex, toIndex)) return

        preload.clear()
        onQueueMutated("queue-move")
    }

    private fun onQueueMutated(reason: String) {
        updateQueueState()
        preload.request(reason)
    }

    private fun startPlaybackFromQueue(
        video: Video,
        loadStreamsInPlayer: Boolean,
    ) {
        // Reset player state for new video
        resetPlaybackStateForNewVideo(video.id)

        _playerState.value =
            _playerState.value.copy(
                currentVideoId = video.id,
                isPlaying = true,
                playWhenReady = true,
                isBuffering = true,
            )

        GlobalPlayerState.setCurrentVideo(video)
        startBackgroundService(
            videoId = video.id,
            title = video.title,
            channel = video.channelName,
            thumbnail = video.thumbnailUrl,
        )
        if (loadStreamsInPlayer) {
            playVideoFromServiceLayer(video, reason = "queue-advance")
        }
    }

    private fun updateQueueState() {
        _playerState.value =
            _playerState.value.copy(
                hasNext = hasNext(),
                hasPrevious = hasPrevious(),
                queueTitle = queue.title,
                queueSize = queue.size,
                isQueueLooping = queue.loopEnabled,
                isQueueShuffled = queue.shuffleEnabled,
            )
    }

    fun toggleQueueLoop(enabled: Boolean) {
        if (!isMainThread()) {
            scope.launch { toggleQueueLoop(enabled) }
            return
        }
        queue.setLoopEnabled(enabled)
        preload.clear()
        onQueueMutated("queue-loop-toggle")
    }

    fun toggleQueueShuffle(enabled: Boolean) {
        if (!isMainThread()) {
            scope.launch { toggleQueueShuffle(enabled) }
            return
        }
        if (!queue.setShuffleEnabled(enabled)) return

        preload.clear()
        onQueueMutated("queue-shuffle-toggle")
    }

    fun setAutoplayCandidates(
        sourceVideoId: String,
        videos: List<Video>,
        enabled: Boolean = autoplayEnabled,
    ) {
        if (!isMainThread()) {
            autoNextLog("setAutoplayCandidates posted to main source=$sourceVideoId")
            scope.launch { setAutoplayCandidates(sourceVideoId, videos, enabled) }
            return
        }
        autoplayEnabled = enabled
        autoplaySourceVideoId = sourceVideoId
        val existingRelatedCandidates =
            relatedCandidatesSnapshot
                .takeIf { it.sourceVideoId == sourceVideoId }
                ?.videos
                .orEmpty()
        val relatedCandidates =
            PlayerRelatedVideosPolicy.select(
                videoId = sourceVideoId,
                primary = videos,
                fallback = emptyList(),
                current = existingRelatedCandidates,
            )
        relatedCandidatesSnapshot = RelatedCandidatesSnapshot(sourceVideoId, relatedCandidates)
        autoplayCandidates = relatedCandidates.filter { !it.isLive && !it.isUpcoming }
        Log.d(TAG, "Autoplay candidates for $sourceVideoId: ${autoplayCandidates.size}, enabled=$enabled")
        autoNextLog("setAutoplayCandidates source=$sourceVideoId input=${videos.size} filtered=${autoplayCandidates.size} enabled=$enabled")
        if (sourceVideoId == currentVideoId) {
            preload.request("autoplay-candidates")
        }
    }

    private fun hasNextForSession(): Boolean = hasNext() || (autoplayEnabled && autoplayCandidates.isNotEmpty())

    internal fun skipToNextFromSession(): Boolean {
        val expectedVideoId = nextSessionVideo()?.id
        autoNextLog("skipToNextFromSession expected=$expectedVideoId")
        if (expectedVideoId != null && advanceToPreloadedItem(expectedVideoId)) {
            autoNextLog("skipToNextFromSession using preloaded window")
            return true
        }
        return playNext(loadStreamsInPlayer = true) || playNextAutoplayCandidate()
    }

    private fun advanceToPreloadedItem(expectedVideoId: String): Boolean {
        val preloaded = preload.preloaded ?: return false
        if (preloaded.data.enrichedVideo.id != expectedVideoId) return false
        val p = player ?: return false
        if (p.currentMediaItemIndex >= p.mediaItemCount - 1) return false

        p.seekToNextMediaItem()
        p.playWhenReady = true
        p.play()
        return true
    }

    private fun playNextAutoplayCandidate(): Boolean {
        if (!autoplayEnabled || autoplayJob?.isActive == true || _playerState.value.isLooping) {
            autoNextLog("playNextAutoplayCandidate blocked")
            return false
        }

        val nextVideo =
            autoplayCandidates.firstOrNull() ?: run {
                autoNextLog("playNextAutoplayCandidate no candidate")
                return false
            }

        autoNextLog("playNextAutoplayCandidate starting ${nextVideo.id}")
        autoplayCandidates = autoplayCandidates.drop(1)
        playVideoFromServiceLayer(nextVideo, reason = "related-autoplay")
        return true
    }

    // ===== Autoplay countdown (delay before switching to the next video) =====

    internal fun nextSessionVideo(): Video? =
        when {
            hasNext() -> if (queueAutoplayEnabled) queue.nextVideo() else null
            autoplayEnabled -> autoplayCandidates.firstOrNull()
            else -> null
        }

    private fun emitPlaybackCompletion() {
        val videoId = currentVideoId ?: return
        val exoPlayer = player ?: return
        val durationMs = exoPlayer.duration.takeIf { it > 0L && it != FlowPlayer.TIME_UNSET } ?: return
        // A refocus glitch can report ENDED near the start; only a position at the end counts.
        if (!PlaybackResumePolicy.shouldRestartCompletedPlayback(exoPlayer.currentPosition, durationMs)) return
        _playbackCompletedEvent.tryEmit(PlaybackCompletion(videoId, durationMs))
    }

    private fun maybeStartAutoplayCountdownOrAdvance() {
        val delaySeconds = autoplayCountdownSeconds
        val nextVideo = nextSessionVideo()
        if (delaySeconds > 0 && nextVideo != null) {
            startAutoplayCountdown(delaySeconds, nextVideo)
        } else {
            performAutoAdvance()
        }
    }

    private fun performAutoAdvance() {
        if (hasNext()) {
            // Queue autoplay can be disabled independently; manual next still works.
            if (!queueAutoplayEnabled) {
                autoNextLog("auto-advance queue disabled")
                return
            }
            autoNextLog("auto-advance queue playNext")
            _queueAutoAdvanceEvent.tryEmit(Unit)
            playNext(loadStreamsInPlayer = true)
        } else {
            autoNextLog("auto-advance related-autoplay")
            playNextAutoplayCandidate()
        }
    }

    private fun startAutoplayCountdown(
        totalSeconds: Int,
        nextVideo: Video,
    ) {
        autoplayCountdownController.start(totalSeconds, nextVideo)
    }

    fun skipAutoplayCountdown() {
        if (!autoplayCountdownController.stop()) return
        autoNextLog("autoplay countdown skipped -> advance now")
        performAutoAdvance()
    }

    fun cancelAutoplayCountdown() {
        if (!autoplayCountdownController.stop()) return
        autoNextLog("autoplay countdown cancelled")
    }

    /** Comments hold the countdown until they close; the overlay stays up with its remaining time. */
    fun setAutoplayHold(hold: Boolean) {
        autoplayCountdownController.setHold(hold)
    }

    fun restartFromAutoplayCountdown() {
        autoplayCountdownController.stop()
        player?.seekTo(0)
        player?.play()
        autoNextLog("autoplay countdown -> restart current")
    }

    private fun clearAutoplayCountdownInternal() {
        autoplayCountdownController.stop()
    }

    private fun playVideoFromServiceLayer(
        video: Video,
        reason: String,
    ) {
        val resumeInAudioOnly = audioOnlyMode.isActive
        preload.clear()
        autoNextLog("playVideoFromServiceLayer start video=${video.id} reason=$reason resumeAudioOnly=$resumeInAudioOnly")
        denialReloads.onLoadStarted(video.id)
        autoplayJob?.cancel()
        autoplayJob =
            scope.launch {
                Log.d(TAG, "Service-layer playback start: ${video.id} ($reason)")
                try {
                    initialize()
                    GlobalPlayerState.setCurrentVideo(video)
                    startBackgroundService(
                        videoId = video.id,
                        title = video.title,
                        channel = video.channelName,
                        thumbnail = video.thumbnailUrl,
                    )
                    _playerState.value =
                        _playerState.value.copy(
                            currentVideoId = video.id,
                            isBuffering = true,
                            isPlaying = false,
                            playWhenReady = true,
                            isPrepared = false,
                            hasEnded = false,
                            error = null,
                        )

                    val localCopyPath = localCopySource?.localCopyPath(video.id)
                    if (localCopyPath != null) {
                        if (shouldAbortServicePlaybackLoad(video.id, reason, checkpoint = "local-copy")) {
                            return@launch
                        }
                        setAutoplayCandidates(sourceVideoId = video.id, videos = emptyList(), enabled = autoplayEnabled)
                        val local = localCaptionSource?.captionsFor(video.id, localCopyPath)
                        if (shouldAbortServicePlaybackLoad(video.id, reason, checkpoint = "local-captions")) {
                            return@launch
                        }
                        playLocalFile(
                            videoId = video.id,
                            filePath = localCopyPath,
                            savedSegments = null,
                            preservePosition = null,
                            subtitles = local?.captions.orEmpty(),
                        )
                        local?.offsetMs?.takeIf { it != 0L }?.let(::setSubtitleOffset)
                        if (resumeInAudioOnly) {
                            audioOnlyMode.applyStreams(true)
                            setVideoTracksDisabled(true)
                        }
                        play()
                        autoNextLog("playVideoFromServiceLayer played download video=${video.id} reason=$reason")
                        return@launch
                    }

                    val extractionDeferred =
                        async(Dispatchers.IO) {
                            try {
                                withTimeoutOrNull(25000L) {
                                    io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
                                        .extract(video.id)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                        }

                    val extraction =
                        extractionDeferred.await() ?: run {
                            autoNextLog("playVideoFromServiceLayer extraction failed video=${video.id}")
                            if (io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
                                    .isGone(video.id) && hasNext()
                            ) {
                                // Posted so this job has finished and cleared itself before the next one starts.
                                scope.launch { playNext(loadStreamsInPlayer = true) }
                                return@launch
                            }
                            _playerState.value =
                                _playerState.value.copy(
                                    isBuffering = false,
                                    error = PlayerStrings.get(Res.string.error_unable_to_load_next_video),
                                )
                            return@launch
                        }
                    if (shouldAbortServicePlaybackLoad(video.id, reason, checkpoint = "streams-resolved")) {
                        return@launch
                    }
                    val enrichedVideo = InnerTubeVideoMapper.videoFromResult(video.id, extraction, fallback = video)
                    GlobalPlayerState.setCurrentVideo(enrichedVideo)
                    startBackgroundService(
                        videoId = enrichedVideo.id,
                        title = enrichedVideo.title,
                        channel = enrichedVideo.channelName,
                        thumbnail = enrichedVideo.thumbnailUrl,
                    )
                    setAutoplayCandidates(
                        sourceVideoId = enrichedVideo.id,
                        videos = visibleRelatedCandidates(enrichedVideo.id),
                        enabled = autoplayEnabled,
                    )

                    val committed =
                        prepareExtractedStreams(
                            videoId = enrichedVideo.id,
                            extraction = extraction,
                            startPositionMs = 0L,
                            keepAudioOnly = resumeInAudioOnly,
                            beforeCommit = { !shouldAbortServicePlaybackLoad(video.id, reason, checkpoint = "before-commit") },
                        )
                    if (!committed) return@launch
                    play()
                    autoNextLog("playVideoFromServiceLayer loaded video=${video.id} reason=$reason")
                } catch (e: CancellationException) {
                    Log.d(TAG, "Service-layer playback cancelled for ${video.id} ($reason)")
                    autoNextLog("playVideoFromServiceLayer cancelled video=${video.id} reason=$reason")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Service-layer playback failed for ${video.id}", e)
                    autoNextLog(
                        "playVideoFromServiceLayer failed video=${video.id} reason=$reason " +
                            "error=${e::class.simpleName}:${e.message}",
                    )
                    _playerState.value =
                        _playerState.value.copy(
                            isBuffering = false,
                            error =
                                e.message ?: PlayerStrings.get(Res.string.error_unable_to_load_next_video),
                        )
                } finally {
                    autoplayJob = null
                }
            }
    }

    /**
     * Turns an extraction into the viewer's preferred streams and prepares them at [startPositionMs].
     * Shared by background advances and denied-stream reloads, so both pick streams the same way.
     * False when [beforeCommit] declines, or there is no context to read preferences from.
     */
    private suspend fun prepareExtractedStreams(
        videoId: String,
        extraction: InnerTubeVideoStreamExtractor.VideoExtractionResult,
        startPositionMs: Long,
        keepAudioOnly: Boolean,
        beforeCommit: () -> Boolean = { true },
    ): Boolean {
        val prefs = preferences
        val preferredQuality =
            if (NetworkState.isOnWifi()) {
                prefs.defaultQualityWifi.first()
            } else {
                prefs.defaultQualityCellular.first()
            }
        val preferredAudioLanguage = prefs.preferredAudioLanguage.first()
        val preferredSubtitleLanguage = prefs.preferredSubtitleLanguage.first()
        val preferredCodecKey = prefs.videoCodecPriority.first()
        val mergedVideoStreams =
            io.github.aedev.flow.player.stream.InnerTubeStreamBridge
                .convertVideoFormats(extraction.videoFormats)
        val mergedAudioStreams =
            io.github.aedev.flow.player.stream.InnerTubeStreamBridge
                .convertAudioFormats(extraction.audioFormats)
        Log.d(
            TAG,
            "Extracted streams for $videoId: ${mergedVideoStreams.size} video, " +
                "${mergedAudioStreams.size} audio (client=${extraction.usedClient.clientName})",
        )

        val selected =
            ServicePlaybackStreamSelector.selectStreams(
                videoCandidates = mergedVideoStreams,
                audioCandidatesAll = mergedAudioStreams,
                preferredQuality = preferredQuality,
                preferredAudioLanguage = preferredAudioLanguage,
                preferredCodecKey = preferredCodecKey,
            )
        if (!beforeCommit()) return false
        setStreams(
            videoId = videoId,
            videoStream = selected.first,
            audioStream = selected.second,
            videoStreams = mergedVideoStreams,
            audioStreams = mergedAudioStreams,
            subtitles =
                CaptionTrackResolver
                    .resolve(
                        extraction.playerResponse,
                        translateTo = preferredSubtitleLanguage,
                    ),
            durationSeconds = InnerTubeVideoMapper.durationSeconds(extraction),
            dashManifestUrl = extraction.liveDashUrl,
            hlsUrl = extraction.liveHlsUrl,
            streamType = InnerTubeVideoMapper.streamType(extraction),
            startPosition = startPositionMs,
            sabrInfo = extraction.sabrInfo,
            itVideoFormats = extraction.videoFormats,
            itAudioFormats = extraction.audioFormats,
            preferredVideoCodec = preferredCodecKey,
            keepAudioOnly = keepAudioOnly,
        )
        return true
    }

    fun relatedCandidatesFor(videoId: String): List<Video> =
        relatedCandidatesSnapshot.takeIf { it.sourceVideoId == videoId }?.videos.orEmpty()

    private fun shouldAbortServicePlaybackLoad(
        videoId: String,
        reason: String,
        checkpoint: String,
    ): Boolean {
        val decision =
            ServicePlaybackLoadPolicy.decide(
                requestedVideoId = videoId,
                playerVideoId = _playerState.value.currentVideoId,
                globalVideoId = GlobalPlayerState.currentVideo.value?.id,
                isPreparedForRequestedVideo = isPreparedForPlayback(videoId),
            )
        if (decision == ServicePlaybackCommitDecision.COMMIT) return false

        autoNextLog(
            "playVideoFromServiceLayer skipped video=$videoId reason=$reason " +
                "checkpoint=$checkpoint decision=$decision",
        )
        return true
    }

    private data class RelatedCandidatesSnapshot(
        val sourceVideoId: String? = null,
        val videos: List<Video> = emptyList(),
    )

    private suspend fun visibleRelatedCandidates(videoId: String): List<Video> {
        val candidates = YouTubeRepository.getInstance().getRelatedCandidates(videoId)
        return withContext(Dispatchers.Default) {
            PlayerRelatedVideosPolicy.sanitizeHidden(videoId, candidates, feedExclusionsSource)
        }
    }

    private suspend fun resolveStreamsForVideo(video: Video): ResolvedStreamData? =
        coroutineScope {
            val extractionDeferred =
                async(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(25000L) {
                            io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
                                .extract(video.id)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            val extraction = extractionDeferred.await() ?: return@coroutineScope null
            val enrichedVideo = InnerTubeVideoMapper.videoFromResult(video.id, extraction, fallback = video)
            val prefs = preferences
            val preferredQuality =
                if (NetworkState.isOnWifi()) {
                    prefs.defaultQualityWifi.first()
                } else {
                    prefs.defaultQualityCellular.first()
                }
            val preferredAudioLanguage = prefs.preferredAudioLanguage.first()
            val preferredSubtitleLanguage = prefs.preferredSubtitleLanguage.first()
            val preferredCodecKey = prefs.videoCodecPriority.first()
            val mergedVideoStreams =
                io.github.aedev.flow.player.stream.InnerTubeStreamBridge
                    .convertVideoFormats(extraction.videoFormats)
            val mergedAudioStreams =
                io.github.aedev.flow.player.stream.InnerTubeStreamBridge
                    .convertAudioFormats(extraction.audioFormats)
            val selected =
                ServicePlaybackStreamSelector.selectStreams(
                    videoCandidates = mergedVideoStreams,
                    audioCandidatesAll = mergedAudioStreams,
                    preferredQuality = preferredQuality,
                    preferredAudioLanguage = preferredAudioLanguage,
                    preferredCodecKey = preferredCodecKey,
                )
            ResolvedStreamData(
                enrichedVideo = enrichedVideo,
                videoStream = selected.first,
                audioStream = selected.second,
                videoStreams = mergedVideoStreams,
                audioStreams = mergedAudioStreams,
                subtitles =
                    CaptionTrackResolver
                        .resolve(
                            extraction.playerResponse,
                            translateTo = preferredSubtitleLanguage,
                        ),
                durationSeconds = InnerTubeVideoMapper.durationSeconds(extraction),
                dashManifestUrl = extraction.liveDashUrl,
                streamType = InnerTubeVideoMapper.streamType(extraction),
                relatedVideos = visibleRelatedCandidates(video.id),
                preferredCodec = preferredCodecKey,
                itVideoFormats = extraction.videoFormats,
                itAudioFormats = extraction.audioFormats,
            )
        }

    private fun nextPreloadTarget(): PreloadTarget? {
        if (autoplayCountdownSeconds > 0) return null
        val fromQueue = hasNext()
        val nextVideo =
            when {
                fromQueue -> if (queueAutoplayEnabled) queue.nextVideo() else null
                autoplayEnabled && autoplayCandidates.isNotEmpty() -> autoplayCandidates.first()
                else -> null
            } ?: return null
        if (nextVideo.isLive || nextVideo.isUpcoming) return null
        return PreloadTarget(nextVideo, fromQueue)
    }

    private fun promotePreloadedItem() {
        val pre = preload.consume() ?: return
        autoNextLog("promotePreloadedItem ${pre.data.enrichedVideo.id} fromQueue=${pre.fromQueue}")
        val data = pre.data
        Log.d(TAG, "Gapless: promoting auto-advanced item ${data.enrichedVideo.id} (fromQueue=${pre.fromQueue})")

        mediaLoader?.releaseSabr()
        currentSabrInfo = null
        sabrPreferred = false

        if (pre.fromQueue) {
            queue.nextIndex()?.let { nextIndex -> queue.moveTo(nextIndex) }
        } else {
            autoplayCandidates = autoplayCandidates.filter { it.id != data.enrichedVideo.id }
        }

        innerTubeVideoFormats = data.itVideoFormats
        innerTubeAudioFormats = data.itAudioFormats
        currentDurationSeconds = data.durationSeconds
        currentDashManifestUrl = data.dashManifestUrl
        currentHlsUrl = null
        currentIsLiveStream = false
        pendingInitialLiveEdgeSeek = false
        currentVideoId = data.enrichedVideo.id

        availableVideoStreams = StreamProcessor.processVideoStreams(data.videoStreams)
        availableAudioStreams = StreamProcessor.processAudioStreams(data.audioStreams)
        castAudioStreams = data.audioStreams
        subtitleTracks.load(data.enrichedVideo.id, StreamProcessor.processCaptions(data.subtitles), acceptsEmbedded = false)
        resetSubtitleDelayFor(data.enrichedVideo.id)
        val ladder = AdaptiveLadder.forPreload(data.videoStream, availableVideoStreams, data.preferredCodec)
        currentVideoStream = data.videoStream ?: ladder.lastOrNull() ?: availableVideoStreams.firstOrNull()
        currentAudioStream = data.audioStream
        applySubtitleTrackSelection()

        qualityManager?.resetForNewVideo()
        qualityManager?.setAvailableStreams(availableVideoStreams)
        qualityManager?.preferredCodecKey = data.preferredCodec
        qualityManager?.isDashSource = !currentDashManifestUrl.isNullOrEmpty()
        qualityManager?.setCurrentStream(currentVideoStream)
        // The preloaded source resolved ahead; on iOS a ladder may be one rung the bundled engine plays.
        val preloaded = mediaLoader?.resolution(data.enrichedVideo.toVideoSessionMetadata().mediaId)
        if (preloaded != null) applyResolution(preloaded) else qualityManager?.isAdaptiveLadderActive = AdaptiveDashManifest.build(ladder, data.durationSeconds) != null
        if (data.videoStream != null) {
            qualityManager?.setManualMode(VideoCodecUtils.qualityHeightFromStream(data.videoStream))
        }

        playbackTracker?.reset()
        startPlaybackTracker()

        sponsorBlockHandler?.reset()
        sponsorBlockHandler?.loadSegments(data.enrichedVideo.id)

        GlobalPlayerState.setCurrentVideo(data.enrichedVideo)
        startBackgroundService(
            videoId = data.enrichedVideo.id,
            title = data.enrichedVideo.title,
            channel = data.enrichedVideo.channelName,
            thumbnail = data.enrichedVideo.thumbnailUrl,
        )
        setAutoplayCandidates(data.enrichedVideo.id, data.relatedVideos, autoplayEnabled)

        val isAutoMode = data.videoStream == null
        _playerState.value =
            _playerState.value.copy(
                currentVideoId = data.enrichedVideo.id,
                sourceVideoAspectRatio = resolveSourceVideoAspectRatio(),
                isBuffering = false,
                isPlaying = player?.isPlaying ?: false,
                playWhenReady = player?.playWhenReady ?: true,
                hasEnded = false,
                isPrepared = true,
                error = null,
                effectiveQuality =
                    currentVideoStream
                        ?.let(VideoCodecUtils::qualityHeightFromStream)
                        ?.let(QualityManager::normalizeQualityHeight)
                        ?: 0,
                availableQualities = buildAvailableQualityOptions(),
                availableAudioTracks = StreamProcessor.toAudioTrackOptions(availableAudioStreams),
                availableSubtitles = subtitleTracks.options,
                selectedSubtitleUrl = subtitleTracks.selected?.url,
                currentQuality =
                    if (isAutoMode) {
                        0
                    } else {
                        currentVideoStream
                            ?.let(VideoCodecUtils::qualityHeightFromStream)
                            ?.let(QualityManager::normalizeQualityHeight)
                            ?: 0
                    },
                currentQualityKey = if (isAutoMode) null else currentVideoStream?.content?.takeIf { it.isNotBlank() },
                currentAudioTrack = StreamProcessor.indexOfAudioTrack(availableAudioStreams, currentAudioStream),
                isLive = false,
                isAtLiveEdge = false,
                liveDurationMs = 0L,
            )
        updateQueueState()
        _queueAutoAdvanceEvent.tryEmit(Unit)

        player?.let { p ->
            val idx = p.currentMediaItemIndex
            for (i in idx - 1 downTo 0) {
                runCatching { p.removeMediaItem(i) }
            }
        }

        preload.restartAfterAdvance()
    }

    // ===== Playback Controls =====

    fun play() {
        startPausedVideoId = null
        if (audioOnlyMode.needsVideoRestore) {
            restoreVideoOutput()
        }
        val p = player ?: return
        if (p.playbackState == FlowPlayer.STATE_IDLE) {
            if (reloadClearedMediaIfNeeded(playWhenReadyOverride = true)) return
            if (p.mediaItemCount > 0) {
                Log.d(TAG, "play(): player IDLE with media — calling prepare()")
                p.prepare()
            }
        }
        p.play()
    }

    fun recoverClearedMediaAfterForeground(): Boolean = reloadClearedMediaIfNeeded()

    private fun reloadClearedMediaIfNeeded(playWhenReadyOverride: Boolean? = null): Boolean {
        val p = player ?: return false
        if (p.playbackState != FlowPlayer.STATE_IDLE || p.mediaItemCount > 0) return false
        val recovery = clearedMediaRecoveryState.pendingFor(currentVideoId) ?: return false
        val resumePosition = recovery.positionMs.takeIf { it > 0L }
        val shouldPlay = playWhenReadyOverride ?: recovery.playWhenReady
        Log.w(
            TAG,
            "reloadClearedMedia: reloading ${recovery.videoId} " +
                "(resumePos=$resumePosition playWhenReady=$shouldPlay local=${recovery.localFilePath != null})",
        )
        val loaded =
            loadMediaInternal(
                videoStream = currentVideoStream,
                audioStream = currentAudioStream,
                preservePosition = resumePosition,
                localFilePath = recovery.localFilePath,
                playWhenReady = shouldPlay,
            )
        if (loaded) {
            clearedMediaRecoveryState.complete(recovery)
        }
        return loaded
    }

    fun pause() = player?.pause()

    /**
     * Nudges the playhead one frame.
     *
     * Only while paused: stepping a running player just fights playback. VOD seeks are
     * CLOSEST_SYNC, which would snap back to the same keyframe every time, so the step is made
     * EXACT and the parameter restored afterwards. Stepping backwards is slower than forwards
     * because it decodes forward from the preceding keyframe, which on YouTube can be seconds back.
     */
    fun stepFrame(forward: Boolean) {
        val p = player ?: return
        if (p.isPlaying || currentIsLiveStream || p.isCurrentMediaItemLive) return
        val target =
            FrameStepPolicy.stepTarget(
                positionMs = p.currentPosition,
                durationMs = p.duration,
                // Media3's playing format's rate; on iOS the stream's, which is the format playing.
                frameRate = currentVideoStream?.fps?.takeIf { it > 0 }?.toFloat(),
                forward = forward,
            ) ?: return
        p.setSeekParameters(exact = true)
        p.seekTo(target)
        p.setSeekParameters(exact = false)
    }

    fun seekTo(position: Long) {
        val p = player ?: return
        val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
        val target = resolveSeekTarget(p, position)
        val isEndBoundary = !isLive && isEndBoundarySeek(position, p.duration)
        if (!isLive && !isEndBoundary && mediaLoader?.getActiveSabrOrchestrator() != null) {
            sabrSeekTo(target)
            return
        }
        if (isLive || isEndBoundary) {
            p.setSeekParameters(exact = true)
        }
        if (isLive) {
            markLiveDisplaySeek(target)
        }
        p.seekTo(target)
        if (isLive) {
            updateLiveEdgeState(p)
        } else if (isEndBoundary) {
            p.setSeekParameters(exact = false)
        }
    }

    fun skipToSegmentEnd(endPositionMs: Long) {
        val p = player ?: return
        val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
        if (!isLive && mediaLoader?.getActiveSabrOrchestrator() != null) {
            sabrSeekTo(resolveSeekTarget(p, endPositionMs))
            return
        }
        val target = resolveSeekTarget(p, endPositionMs)
        p.setSeekParameters(exact = true)
        if (isLive) {
            markLiveDisplaySeek(target)
        }
        p.seekTo(target)
        if (isLive) {
            updateLiveEdgeState(p)
        } else {
            p.setSeekParameters(exact = false)
        }
    }

    private fun sabrSeekTo(positionMs: Long) {
        val shouldPlay = player?.playWhenReady ?: true
        Log.d(TAG, "SABR seek: rebuilding session at ${positionMs}ms")
        _playerState.value = _playerState.value.copy(isBuffering = true)
        scope.launch {
            mediaLoader?.releaseSabr()
            player?.stop()
            player?.clearMediaItems()
            val loaded = loadMediaInternal(currentVideoStream, currentAudioStream, positionMs)
            if (loaded) {
                player?.playWhenReady = shouldPlay
            }
        }
    }

    fun seekToLiveTimeline(position: Long) {
        val p = player ?: return
        val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
        val target = resolveSeekTarget(p, position)
        if (isLive) {
            p.setSeekParameters(exact = true)
            markLiveDisplaySeek(target)
        }
        p.seekTo(target)
        if (isLive) {
            updateLiveEdgeState(p)
        }
    }

    private fun resolveSeekTarget(
        player: FlowPlayer,
        requestedPositionMs: Long,
    ): Long {
        val playerDuration =
            player.duration
                .takeIf { it > 0L && it != FlowPlayer.TIME_UNSET }
                ?: return requestedPositionMs.coerceAtLeast(0L)

        return requestedPositionMs.coerceIn(0L, playerDuration)
    }

    fun seekToLiveEdge(resetSpeed: Boolean = true) {
        val p = player ?: return
        if (resetSpeed) {
            setPlaybackSpeed(1.0f)
        }
        p.seekToDefaultPosition()
        val liveEdgePosition =
            p.duration
                .takeIf { it > 0L && it != FlowPlayer.TIME_UNSET }
                ?: p.currentPosition.coerceAtLeast(0L)
        markLiveDisplaySeek(liveEdgePosition)
        updateLiveEdgeState(p)
    }

    private fun markLiveDisplaySeek(positionMs: Long) {
        pendingLiveDisplaySeekPositionMs = positionMs.coerceAtLeast(0L)
        pendingLiveDisplaySeekAtMs = elapsedRealtime()
    }

    fun consumeRecentLiveDisplaySeek(maxAgeMs: Long = 2_000L): Long? {
        val position = pendingLiveDisplaySeekPositionMs ?: return null
        if (elapsedRealtime() - pendingLiveDisplaySeekAtMs > maxAgeMs) {
            pendingLiveDisplaySeekPositionMs = null
            return null
        }
        pendingLiveDisplaySeekPositionMs = null
        return position
    }

    fun setScrubbingModeEnabled(enabled: Boolean) {
        player?.let { p ->
            val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
            p.setSeekParameters(exact = enabled || isLive)
        }
    }

    fun replay() {
        player?.let { exoPlayer ->
            if (exoPlayer.currentMediaId != null) {
                exoPlayer.seekToDefaultPosition()
            } else {
                exoPlayer.seekTo(0L)
            }
            _playerState.value = _playerState.value.copy(hasEnded = false)
            exoPlayer.play()
        }
    }

    fun toggleLoop(enabled: Boolean) {
        manualLoopEnabled = enabled
        updateEffectiveLoopState()
    }

    private fun updateEffectiveLoopState() {
        _playerState.value = _playerState.value.copy(isLooping = manualLoopEnabled || globalLoopEnabled)
    }

    fun stop() {
        autoplayJob?.cancel()
        autoplayJob = null
        preload.clear()
        currentLocalFilePath = null
        clearedMediaRecoveryState.clear()
        audioOnlyMode.reset()
        pendingInitialLiveEdgeSeek = false
        setVideoTracksDisabled(false)
        updateLivePlaybackMode(isLive = false)
        playbackTracker?.stop()
        player?.stop()
        player?.clearMediaItems()
        qualityManager?.resetForNewVideo()
        _playerState.value =
            _playerState.value.copy(
                isPlaying = false,
                playWhenReady = false,
                isBuffering = false,
                isPrepared = false,
                hasEnded = false,
                currentVideoId = null,
                liveDurationMs = 0L,
            )
    }

    fun getPlayer(): EnginePlayer? = player

    fun getCastVideoStreams(): List<VideoStream> = availableVideoStreams

    fun getCastAudioStreams(): List<AudioStream> = StreamProcessor.streamsOfTrack(castAudioStreams, currentAudioStream)

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration ?: 0L

    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun isPreparedForPlayback(videoId: String): Boolean {
        val p = player ?: return false
        val state = _playerState.value
        return state.currentVideoId == videoId &&
            state.isPrepared &&
            p.currentMediaId != null &&
            p.playbackState != FlowPlayer.STATE_IDLE
    }

    fun hasAbandonedPlayback(): Boolean = errorHandler?.hasGivenUp() == true || denialReloads.hasGivenUpOn(currentVideoId)

    private fun resolveSourceVideoAspectRatio(): Float? {
        val innerTubeDimensions =
            innerTubeVideoFormats
                .asSequence()
                .filterNot { it.isAudio }
                .mapNotNull { format ->
                    val width = format.width ?: return@mapNotNull null
                    val height = format.height ?: return@mapNotNull null
                    width to height
                }
        val extractedDimensions =
            availableVideoStreams
                .asSequence()
                .map { stream -> stream.width to stream.height }
        return sourceVideoAspectRatio((innerTubeDimensions + extractedDimensions).asIterable())
    }

    private fun buildAvailableQualityOptions(): List<QualityOption> {
        val directOptions = qualityManager?.buildQualityOptions().orEmpty()
        val autoOptions = directOptions.filter { it.height == 0 }
        val playableOptions = directOptions.filter { it.height != 0 }
        val existingKeys =
            playableOptions
                .map { VideoCodecUtils.streamSizeKey(it.height, it.codecKey) }
                .toHashSet()

        val sabrOptions =
            if (currentSabrInfo != null) {
                innerTubeVideoFormats
                    .filter { !it.isAudio && it.itag > 0 }
                    .groupBy {
                        VideoCodecUtils.streamSizeKey(
                            qualityHeightFromFormat(it),
                            VideoCodecUtils.codecKeyFromMimeType(it.mimeType),
                        )
                    }.values
                    .mapNotNull { formats ->
                        val best = formats.maxByOrNull { it.averageBitrate ?: it.bitrate } ?: return@mapNotNull null
                        val height = qualityHeightFromFormat(best)
                        val codecKey = VideoCodecUtils.codecKeyFromMimeType(best.mimeType)
                        if (VideoCodecUtils.streamSizeKey(height, codecKey) in existingKeys) return@mapNotNull null
                        QualityOption(
                            height = height,
                            label = "${qualityLabelFromFormat(best)} ${VideoCodecUtils.codecLabelFromKey(codecKey)}",
                            bitrate = (best.averageBitrate ?: best.bitrate).toLong(),
                            codecKey = codecKey,
                            streamKey = "$SABR_QUALITY_KEY_PREFIX${best.itag}",
                        )
                    }
            } else {
                emptyList()
            }

        val auto = autoOptions.ifEmpty { listOf(QualityOption(height = 0, label = "Auto", bitrate = 0L)) }
        val sortedOptions =
            (playableOptions + sabrOptions).sortedWith(
                compareByDescending<QualityOption> { it.height }
                    .thenBy { VideoCodecUtils.playbackCodecRank(it.codecKey) }
                    .thenByDescending { it.bitrate },
            )
        return auto + sortedOptions
    }

    private fun switchSabrQuality(option: QualityOption): Boolean {
        val streamKey = option.streamKey ?: return false
        val itag = streamKey.removePrefix(SABR_QUALITY_KEY_PREFIX).toIntOrNull() ?: return false
        val baseSabr = currentSabrInfo ?: return false
        val format = innerTubeVideoFormats.firstOrNull { it.itag == itag && !it.isAudio }
        if (format == null) {
            Log.w(TAG, "No SABR video format found for itag=$itag")
            return false
        }

        val position = player?.currentPosition ?: 0L
        val shouldPlay = player?.playWhenReady ?: true
        currentSabrInfo =
            baseSabr.copy(
                videoItag = format.itag,
                videoLmt = format.lastModified ?: 0L,
                videoMimeType = format.mimeType,
                durationMs = format.approxDurationMs?.toLongOrNull() ?: baseSabr.durationMs,
            )

        _playerState.value =
            _playerState.value.copy(
                currentQuality = option.height,
                effectiveQuality = option.height,
                currentQualityKey = streamKey,
                isBuffering = true,
            )

        Log.d(TAG, "Switching SABR quality to ${option.label} (itag=$itag)")
        scope.launch {
            mediaLoader?.releaseSabr()
            player?.stop()
            player?.clearMediaItems()
            val loaded = loadMediaInternal(currentVideoStream, currentAudioStream, position)
            if (loaded) {
                player?.playWhenReady = shouldPlay
            }
        }
        return true
    }

    private fun qualityHeightFromFormat(format: PlayerResponse.StreamingData.Format): Int =
        VideoCodecUtils.qualityHeightFromFormat(format.qualityLabel, format.height ?: format.width ?: 0)

    private fun qualityLabelFromFormat(format: PlayerResponse.StreamingData.Format): String =
        format.qualityLabel
            ?.takeIf { it.isNotBlank() }
            ?: VideoCodecUtils.qualityLabelWithFrameRate(qualityHeightFromFormat(format), format.fps ?: 0)

    // ===== Quality & Audio Management =====

    fun switchQualityByHeight(height: Int) =
        if (currentIsLiveStream) {
            switchLiveQuality(height)
        } else {
            qualityManager?.switchQualityByHeight(height, player?.currentPosition ?: 0L)
        }

    fun switchQuality(height: Int) = switchQualityByHeight(height)

    fun switchQuality(option: QualityOption): Boolean? {
        if (option.streamKey?.startsWith(SABR_QUALITY_KEY_PREFIX) == true) {
            return switchSabrQuality(option)
        }
        if (currentIsLiveStream) return switchLiveQuality(option.height)
        return qualityManager?.switchQuality(option, player?.currentPosition ?: 0L)
    }

    /**
     * Media3 lists a live stream's renditions from the manifest's tracks; AVPlayer reports none, so the master
     * playlist's variants (RESOLUTION and FRAME-RATE of each EXT-X-STREAM-INF) are read here once per stream.
     */
    private fun loadLiveQualityOptions(hlsUrl: String) {
        val videoId = currentVideoId
        scope.launch {
            val variants =
                withContext(Dispatchers.IO) {
                    runCatching { ProxyAwareClient().get(Url(hlsUrl)).get(hlsUrl).bodyAsText() }
                        .map(::liveVariants)
                        .getOrElse {
                            Log.w(TAG, "Live manifest unreadable for quality options: ${it.message}")
                            emptyList()
                        }
                }
            if (videoId == currentVideoId && currentIsLiveStream) updateLiveQualityOptions(variants)
        }
    }

    private fun updateLiveQualityOptions(variants: List<LiveVariant>) {
        val heightToFps = HashMap<Int, Int>()
        var portrait = false
        variants.forEach { format ->
            val height = VideoCodecUtils.qualityClass(format.width, format.height).takeIf { it > 0 } ?: return@forEach
            if (format.height > format.width && format.width > 0) portrait = true
            val fps = if (format.frameRate > 0f) format.frameRate.toInt() else 0
            heightToFps[height] = maxOf(heightToFps[height] ?: 0, fps)
        }
        val heights = heightToFps.keys.sortedDescending()
        if (heights.isEmpty() || heights == liveQualityHeights) return
        liveQualityHeights = heights
        liveIsPortrait = portrait
        val options =
            listOf(QualityOption(height = 0, label = "Auto", bitrate = 0L)) +
                heights.map { h ->
                    val label = VideoCodecUtils.qualityLabelWithFrameRate(h, heightToFps[h] ?: 0)
                    QualityOption(height = h, label = label, bitrate = 0L, streamKey = "$LIVE_QUALITY_KEY_PREFIX$h")
                }
        val manualHeight =
            _playerState.value.currentQualityKey
                ?.removePrefix(LIVE_QUALITY_KEY_PREFIX)
                ?.toIntOrNull()
        _playerState.value = _playerState.value.copy(availableQualities = options)
        if (pendingLiveQualityHeight > 0 && manualHeight == null) {
            val target = LiveQualitySelection.snapToOffered(pendingLiveQualityHeight, heights) ?: return
            pendingLiveQualityHeight = 0
            Log.d(TAG, "Applying live quality: ${target}p (portrait=$liveIsPortrait)")
            switchLiveQuality(target, byUser = false)
        }
    }

    private fun applyLiveCodecPreference() {
        val selector = trackSelector ?: return
        selector.parameters =
            selector.parameters.copy(preferredVideoMimeTypes = VideoCodecUtils.preferredVideoMimeTypes(preferredVideoCodecKey).toList())
    }

    /** Leaves `effectiveQuality` to `onVideoSizeChanged`, which reports the frame actually decoded. */
    private fun switchLiveQuality(
        height: Int,
        byUser: Boolean = true,
    ): Boolean {
        val selector = trackSelector ?: return false
        val builder = selector.parameters.copy(preferredVideoMimeTypes = VideoCodecUtils.preferredVideoMimeTypes(preferredVideoCodecKey).toList())
        // AVPlayer adapts within the cap; forcing the highest bit rate under it is Media3's own.
        selector.parameters =
            if (height <= 0) {
                builder.resetVideoSizeTo(videoSizeCap).copy(forceHighestSupportedBitrate = false)
            } else {
                val (maxWidth, maxHeight) = LiveQualitySelection.maxVideoSize(height, liveIsPortrait)
                builder.copy(
                    minVideoWidth = 0,
                    minVideoHeight = 0,
                    maxVideoWidth = maxWidth,
                    maxVideoHeight = maxHeight,
                    forceHighestSupportedBitrate = true,
                )
            }
        if (byUser) currentVideoId?.let { liveQualityPick = LiveQualityPick(it, height.coerceAtLeast(0)) }
        _playerState.value =
            _playerState.value.copy(
                currentQuality = if (height <= 0) 0 else height,
                currentQualityKey = if (height <= 0) null else "$LIVE_QUALITY_KEY_PREFIX$height",
            )
        return true
    }

    fun switchAudioTrack(index: Int) {
        if (index in availableAudioStreams.indices) {
            currentAudioStream = availableAudioStreams[index]
            val position = player?.currentPosition ?: 0L
            val wasPlaying = player?.isPlaying ?: false
            if (audioOnlyMode.isActive) {
                loadMediaInternal(null, currentAudioStream, audioOnly = true)
            } else {
                loadMediaInternal(currentVideoStream, currentAudioStream)
            }
            player?.seekTo(position)
            if (wasPlaying) player?.play()
            _playerState.value = _playerState.value.copy(currentAudioTrack = index)
        }
    }

    /** Shows the track whose option has [url], or turns captions off for null. */
    fun selectSubtitle(url: String?) {
        subtitleTracks.select(url)
        Log.d(TAG, "Subtitle selected: ${subtitleTracks.selected?.label}")
        publishSubtitles()
        applySubtitleTrackSelection()
    }

    /**
     * Shifts captions by [offsetMs] for the video that is playing: positive shows them later. The
     * track on screen is reloaded so a caption already showing moves too.
     */
    fun setSubtitleOffset(offsetMs: Long) {
        subtitleDelayVideoId = currentVideoId
        subtitleDelay.offsetUs = offsetMs * 1_000L
        publishSubtitles()
        // The decoded caption reads the offset live; the player recomputes what shows at once.
        player?.setSubtitle(shownSubtitle)
    }

    /** A new video starts in sync; the same video prepared again keeps its shift. */
    private fun resetSubtitleDelayFor(videoId: String) {
        if (videoId == subtitleDelayVideoId) return
        subtitleDelayVideoId = videoId
        subtitleDelay.offsetUs = 0L
    }

    private var shownSubtitle: io.github.aedev.flow.player.renderer.subtitle.Subtitle? = null

    /**
     * Shows the caption the selection names: upstream hands the choice to Media3's track selector, which loads the
     * sidecar track; here the caption is fetched and decoded, and given to the player.
     */
    private fun applySubtitleTrackSelection() {
        val caption = subtitleTracks.selectedCaption()
        if (caption == shownCaption) return
        shownCaption = caption
        captionJob?.cancel()
        shownSubtitle = null
        player?.setSubtitle(null)
        if (caption == null) return
        val index = subtitleTracks.captions.indexOf(caption)
        val label = caption.label ?: caption.languageTag.ifBlank { "Unknown" }
        captionJob =
            scope.launch {
                val subtitle =
                    captionLoader.load(caption, if (caption.isAutoGenerated) "$label (Auto)" else label) {
                        _subtitleLoadFailedEvent.tryEmit(
                            SubtitleLoadFailure(
                                index = index,
                                label = label,
                                language = caption.languageTag,
                                isTranslated = caption.isTranslated,
                            ),
                        )
                    }
                if (shownCaption == caption) {
                    shownSubtitle = subtitle
                    player?.setSubtitle(subtitle)
                }
            }
    }

    private fun publishSubtitles() {
        _playerState.value =
            _playerState.value.copy(
                availableSubtitles = subtitleTracks.options,
                selectedSubtitleUrl = subtitleTracks.selected?.url,
                subtitleOffsetMs = subtitleDelay.offsetUs / 1_000L,
            )
    }

    // ===== Audio Features =====

    fun setPlaybackSpeed(speed: Float) = audioFeaturesManager?.setPlaybackSpeed(player, speed)

    fun toggleSkipSilence(isEnabled: Boolean) = audioFeaturesManager?.toggleSkipSilence(isEnabled, preferences)

    fun toggleVolumeNormalization(isEnabled: Boolean) = audioFeaturesManager?.toggleVolumeNormalization(isEnabled, preferences)

    fun toggleSponsorBlock(isEnabled: Boolean) {
        sponsorBlockHandler?.setEnabled(isEnabled)
        scope.launch { preferences.setSponsorBlockEnabled(isEnabled) }
    }

    val sponsorSegments: StateFlow<List<SponsorBlockSegment>>
        get() = sponsorBlockHandler?.sponsorSegments ?: MutableStateFlow(emptyList())

    val sponsorBlockOffForVideo: StateFlow<Boolean>
        get() = sponsorBlockHandler?.disabledForCurrentVideo ?: MutableStateFlow(false)

    fun setSponsorBlockOffForVideo(off: Boolean) {
        sponsorBlockHandler?.setDisabledForCurrentVideo(off, getCurrentPosition())
    }

    fun reloadSponsorSegments(videoId: String) = sponsorBlockHandler?.reloadSegments(videoId)

    /** Emits the display label of a subtitle track whose fetch failed and will not be retried. */
    val subtitleLoadFailedEvent: SharedFlow<SubtitleLoadFailure>
        get() = _subtitleLoadFailedEvent

    private val _currentCues = MutableStateFlow<List<io.github.aedev.flow.player.renderer.subtitle.Cue>>(emptyList())

    /** The captions showing now, for the player screen to draw (upstream's SubtitleView reads the player's cues). */
    val currentCues: StateFlow<List<io.github.aedev.flow.player.renderer.subtitle.Cue>> = _currentCues.asStateFlow()

    val skipEvent: SharedFlow<SponsorBlockSegment>
        get() = sponsorBlockHandler?.skipEvent ?: MutableSharedFlow()

    val sbToastEvent: SharedFlow<SponsorBlockSegment>
        get() = sponsorBlockHandler?.toastEvent ?: MutableSharedFlow()

    val sbCategoryActions: Map<String, SponsorBlockAction>
        get() = sponsorBlockHandler?.categoryActions ?: emptyMap()

    // ===== Surface Management =====

    fun attachVideoSurface(
        holder: VideoSurface?,
        forceAttach: Boolean = false,
    ): Boolean? {
        val wasSurfaceValid = surfaceManager?.isSurfaceValid() == true
        val attached = surfaceManager?.attachVideoSurface(holder, player, forceAttach)
        if (attached == true) {
            if (!wasSurfaceValid) {
                pendingSurfaceFirstFrameStartedAtMs = elapsedRealtime()
                Log.w(
                    "FlowVideoLifecycle",
                    "surfaceAttached video=$currentVideoId pos=${player?.currentPosition} " +
                        "pwr=${player?.playWhenReady} force=$forceAttach",
                )
            }
            val p = player
            val reenablesVideo = audioOnlyMode.isActive
            val resyncPausedVideo =
                p != null && !wasSurfaceValid && !audioOnlyMode.isActive &&
                    p.currentMediaId != null &&
                    VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                        playWhenReady = p.playWhenReady,
                        isLive = currentIsLiveStream,
                        playbackState = p.playbackState,
                    )
            if (p != null && currentVideoStream != null) {
                if (audioOnlyMode.isActive) {
                    Log.d(TAG, "attachVideoSurface: was in audio-only mode — restoring video stream")
                    restoreVideoOutput()
                } else if (p.currentMediaId == null) {
                    Log.d(TAG, "attachVideoSurface: no media item — loading media now")
                    loadMediaInternal(currentVideoStream, currentAudioStream)
                } else if (p.playbackState == FlowPlayer.STATE_IDLE) {
                    Log.d(TAG, "attachVideoSurface: surface back and player IDLE — calling prepare()")
                    p.prepare()
                    if (p.playWhenReady) p.play()
                }
            }
            if (resyncPausedVideo && p != null) {
                resyncAfterSurfaceReattach(p)
            } else if (
                p != null &&
                VideoSurfacePolicy.shouldWatchFirstFrame(
                    wasSurfaceValid = wasSurfaceValid,
                    reenabledVideo = reenablesVideo,
                    isAudioOnly = audioOnlyMode.isActive,
                    isLive = currentIsLiveStream,
                )
            ) {
                armSurfaceFirstFrameWatchdog(p)
            }
        }
        return attached
    }

    /**
     * Catches a surface that came back but never drew.
     *
     * While playing, a stale read-ahead corrects itself as the clock advances, so the re-attach
     * above deliberately leaves playback alone. What does not correct itself is a codec whose
     * output surface was swapped and which then renders nothing at all — the picture stays black
     * while the audio keeps going, and only another surface change brings it back (#1064). A first
     * frame normally lands in about a tenth of a second, so silence well past that is evidence of
     * that state rather than a slow device, and the same flush the paused path uses recovers it.
     */
    private fun armSurfaceFirstFrameWatchdog(p: EnginePlayer) {
        surfaceFirstFrameWatchdog?.cancel()
        surfaceFirstFrameWatchdog =
            scope.launch {
                delay(SURFACE_FIRST_FRAME_TIMEOUT_MS)
                if (pendingSurfaceFirstFrameStartedAtMs == 0L) return@launch
                if (surfaceManager?.isSurfaceValid() != true) return@launch
                if (p.playbackState != FlowPlayer.STATE_READY || !p.playWhenReady) return@launch
                Log.w(
                    "FlowVideoLifecycle",
                    "surfaceFirstFrameTimeout video=$currentVideoId pos=${p.currentPosition} — forcing a flush",
                )
                resyncAfterSurfaceReattach(p)
            }
    }

    /**
     * Realigns the video codec with the playhead after its surface came back.
     *
     * The seek is one millisecond short of the current position on purpose: a seek that resolves to
     * the position the player already reports never reaches the code that disables the renderers,
     * so the codec keeps decoding from wherever its read-ahead had got to. While paused that can be
     * ten seconds past the playhead, and playback then shows a frozen frame until the clock catches
     * up. Exact seek parameters keep the one-millisecond step from snapping to a sync frame.
     */
    private fun resyncAfterSurfaceReattach(p: EnginePlayer) {
        val position = p.currentPosition
        if (mediaLoader?.getActiveSabrOrchestrator() != null) {
            // A SABR seek tears the session down and rebuilds it, which is far more than a surface
            // swap should cost.
            Log.w("FlowVideoLifecycle", "surfaceReattachResync skipped sabr video=$currentVideoId pos=$position")
            return
        }
        val target = VideoSurfacePolicy.resyncSeekTargetMs(position)
        Log.w(
            "FlowVideoLifecycle",
            "surfaceReattachResync video=$currentVideoId pos=$position target=$target",
        )
        p.setSeekParameters(exact = true)
        p.seekTo(target)
        p.setSeekParameters(exact = false)
    }

    fun detachVideoSurface(holder: VideoSurface? = null) {
        val hadManagedSurface = surfaceManager?.getSurfaceHolder() != null
        surfaceManager?.detachVideoSurface(holder, player)
        if (hadManagedSurface) {
            _firstFrameVideoId.value = null
            surfaceFirstFrameWatchdog?.cancel()
            surfaceFirstFrameWatchdog = null
            pendingSurfaceFirstFrameStartedAtMs = 0L
            Log.w(
                "FlowVideoLifecycle",
                "surfaceDetached video=$currentVideoId pos=${player?.currentPosition} pwr=${player?.playWhenReady}",
            )
        }
    }

    suspend fun awaitSurfaceReady(timeoutMillis: Long = 1000) = surfaceManager?.awaitSurfaceReady(timeoutMillis) ?: false

    fun continueVideoPlaybackInBackground() {
        autoNextLog("continueVideoPlaybackInBackground")
        switchToAudioOnly()
        val p = player
        val current = GlobalPlayerState.currentVideo.value
        val state = _playerState.value
        if (current != null &&
            BackgroundHandoffPolicy.needsServiceLayerReload(
                isSameVideo = state.currentVideoId == current.id,
                hasMediaItem = p?.currentMediaId != null,
                playbackState = p?.playbackState,
                isPrepared = state.isPrepared,
            )
        ) {
            autoNextLog("continueVideoPlaybackInBackground service-load current=${current.id}")
            playVideoFromServiceLayer(current, reason = "background-handoff-unprepared")
            return
        }
        resumePlaybackIfStalled(p)
    }

    fun handleMemoryPressure(
        trimLevel: Int,
        videoVisible: Boolean,
    ) {
        when (MemoryPressurePolicy.responseTo(trimLevel, videoVisible)) {
            MemoryPressureResponse.NONE -> {}

            MemoryPressureResponse.DROP_PRELOAD -> {
                Log.w(TAG, "Memory pressure level=$trimLevel while video is visible; dropping the preload only")
                preload.clear()
            }

            MemoryPressureResponse.RELEASE_VIDEO -> {
                releaseVideoForMemoryPressure(trimLevel)
            }
        }
    }

    private fun releaseVideoForMemoryPressure(trimLevel: Int) {
        Log.w(TAG, "Critical memory pressure level=$trimLevel; releasing video-heavy player state")
        mediaLoader?.releaseSabr()
        val p = player ?: return
        val shouldKeepPlaying = p.playWhenReady || p.isPlaying
        if (shouldKeepPlaying) {
            switchToAudioOnly()
        } else {
            if (p.mediaItemCount > 0) {
                clearedMediaRecoveryState.capture(
                    videoId = currentVideoId,
                    positionMs = p.currentPosition,
                    playWhenReady = p.playWhenReady,
                    localFilePath = currentLocalFilePath,
                )
            }
            p.stop()
            p.clearMediaItems()
        }
    }

    fun switchToAudioOnly() {
        val p = player ?: return
        autoNextLog("switchToAudioOnly")
        enterAudioOnly(p)
        resumePlaybackIfStalled(p)
        preload.schedule()
    }

    /**
     * Turns video off with a pause on leaving the app, so Play from the notification resumes audio
     * only, as background playback does (#1298). It neither resumes nor preloads: a pause fetches
     * nothing. Coming back to the app restores video as it does after background playback.
     */
    fun parkVideoForBackground() {
        val p = player ?: return
        autoNextLog("parkVideoForBackground")
        enterAudioOnly(p)
    }

    private fun enterAudioOnly(p: FlowPlayer) {
        audioOnlyMode.enter()
        setVideoTracksDisabled(true)
    }

    fun restoreVideoOutput() {
        val p = player ?: return
        val displayInteractive = isDisplayInteractive()
        val surfaceValid = surfaceManager?.isSurfaceValid() == true
        val canRestore =
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = VideoSurfacePolicy.IOS_SURFACE_SDK,
                isDisplayInteractive = displayInteractive,
                isSurfaceValid = surfaceValid,
            )
        if (!audioOnlyMode.restore(canRestore)) {
            autoNextLog(
                "restoreVideoOutput deferred interactive=$displayInteractive surfaceValid=$surfaceValid",
            )
            return
        }
        autoNextLog("restoreVideoOutput")
        setVideoTracksDisabled(false)
        resumePlaybackIfStalled(p)
    }

    private fun resumePlaybackIfStalled(p: FlowPlayer?) {
        if (p != null && p.playWhenReady && !p.isPlaying && p.playbackState != FlowPlayer.STATE_ENDED) {
            p.play()
        }
    }

    fun isInAudioOnlyMode(): Boolean = audioOnlyMode.isActive

    fun isVideoSurfaceRestorePending(): Boolean = audioOnlyMode.restorePending

    fun setSurfaceReady(ready: Boolean) {
        surfaceManager?.setSurfaceReady(ready)
        if (ready) {
            val p = player
            if (p != null && currentVideoStream != null) {
                when {
                    p.currentMediaId == null -> {
                        Log.d(TAG, "setSurfaceReady: no media item yet, loading media")
                        loadMediaInternal(currentVideoStream, currentAudioStream)
                    }

                    p.playbackState == FlowPlayer.STATE_IDLE -> {
                        Log.d(TAG, "setSurfaceReady: player idle, calling prepare() to recover")
                        p.prepare()
                        if (p.playWhenReady) p.play()
                    }
                }
            }
        }
    }

    // ===== Cache & Background Service =====

    fun getCacheSize(): Long = cacheManager?.getCacheSize() ?: 0L

    suspend fun clearCacheForCurrentVideo() {
        val videoId = currentVideoId ?: return
        Log.d(TAG, "Clearing $videoId from the media cache due to persistent stream errors")
        withContext(Dispatchers.IO) { cacheManager?.clearVideo(videoId) }
    }

    fun startBackgroundService(
        videoId: String,
        title: String,
        channel: String,
        thumbnail: String,
    ) {
        backgroundService?.start(videoId, title, channel, thumbnail)
    }

    fun stopBackgroundService() {
        backgroundService?.stop()
    }

    // ===== Bandwidth & Renderer Info =====

    fun getBandwidthEstimate(): Long = bandwidthMeter?.bitrateEstimate ?: 0L

    fun logBandwidthInfo() {
        val mbps = getBandwidthEstimate() / 1_000_000.0
        Log.d(TAG, "Bandwidth: ${(mbps * 100).toLong() / 100.0} Mbps")
    }

    /** Media3 reads its renderers' track groups; the port's player has a picture once its engine reports a size. */
    fun isVideoRendererAvailable(): Boolean {
        player?.let { p ->
            if (p.playbackState == FlowPlayer.STATE_IDLE || p.playbackState == FlowPlayer.STATE_BUFFERING) return true
            if (p.videoSize.width > 0) return true
        }
        return false
    }

    fun clearCurrentVideo() {
        preload.clear()
        audioOnlyMode.reset()
        setVideoTracksDisabled(false)
        updateLivePlaybackMode(isLive = false)
        mediaLoader?.releaseSabr()
        player?.stop()
        player?.clearMediaItems()
        qualityManager?.resetForNewVideo()
        currentVideoId = null
        currentVideoStream = null
        currentAudioStream = null
        currentLocalFilePath = null
        clearedMediaRecoveryState.clear()
        _playerState.value =
            _playerState.value.copy(
                isPlaying = false,
                currentVideoId = null,
                currentQuality = 0,
                currentQualityKey = null,
                bufferedPercentage = 0f,
                isBuffering = false,
                isPrepared = false,
                hasEnded = false,
                isLive = false,
                isAtLiveEdge = false,
                liveDurationMs = 0L,
            )
    }

    fun clearAll() {
        clearCurrentVideo()
        manualLoopEnabled = false
        autoplayCandidates = emptyList()
        autoplaySourceVideoId = null
        queue.clear()
        _playerState.value =
            _playerState.value.copy(
                hasNext = false,
                hasPrevious = false,
                queueTitle = null,
                queueSize = 0,
                isQueueLooping = false,
                isQueueShuffled = false,
                isLooping = globalLoopEnabled,
            )
    }

    private fun updateLiveEdgeState(player: FlowPlayer) {
        if (!currentIsLiveStream && !player.isCurrentMediaItemLive) return
        val atLiveEdge = isPlayerAtLiveEdge(player)
        if (_playerState.value.isAtLiveEdge != atLiveEdge || !_playerState.value.isLive) {
            _playerState.value =
                _playerState.value.copy(
                    isLive = true,
                    isAtLiveEdge = atLiveEdge,
                )
        }
    }

    /**
     * Media3 measures the live offset against the wall clock and the window's default position. AVPlayer's live
     * window ends at the newest segment it can play, so the distance from that end stands in for the offset.
     */
    private fun isPlayerAtLiveEdge(player: FlowPlayer): Boolean {
        if (!currentIsLiveStream && !player.isCurrentMediaItemLive) return false
        val duration = player.duration
        if (duration <= 0L || duration == FlowPlayer.TIME_UNSET) return false
        val liveOffset = duration - player.currentPosition
        return liveOffset <= PlayerConfig.LIVE_EDGE_GAP_MS + LIVE_EDGE_THRESHOLD_MS
    }

    fun release() {
        Log.d(TAG, "release() called")
        preload.clear()
        pendingReloadJob?.cancel()
        pendingReloadJob = null
        mediaLoader?.releaseSabr()
        clearedMediaRecoveryState.clear()
        playbackTracker?.stop()
        audioFeaturesManager?.clearPlayer()
        buildScope.close()
        surfaceManager?.release(player)
        captionJob?.cancel()
        shownCaption = null
        player?.release()
        player = null
        playerFactory.clearPreferences()
        trackSelector = null
        cacheManager?.release()
        cacheManager = null
        _playerState.value = EnhancedPlayerState()
        Log.d(TAG, "Player released")
    }

    // ===== Error Recovery =====

    private fun reloadCurrentStream(
        preservePosition: Long?,
        reason: String,
    ) {
        val video = currentVideoStream ?: return
        val audio = currentAudioStream ?: availableAudioStreams.firstOrNull()
        val pos = preservePosition ?: player?.currentPosition ?: 0L
        Log.d(TAG, "Reloading ${VideoCodecUtils.qualityHeightFromStream(video)}p at ${pos}ms ($reason)")
        player?.stop()
        player?.clearMediaItems()
        loadMediaInternal(video, audio, pos)
    }

    private fun reloadPlaybackManager() {
        pendingReloadJob?.cancel()
        pendingReloadJob =
            scope.launch {
                try {
                    delay(PlayerConfig.ERROR_RETRY_DELAY_MS)

                    val pos = player?.currentPosition ?: 0L
                    player?.stop()
                    player?.clearMediaItems()

                    if (qualityManager?.isAdaptiveQualityEnabled == false) {
                        reloadCurrentStream(pos, "manual-quality-reload")
                        return@launch
                    }

                    currentVideoStream?.let { stream ->
                        if (qualityManager?.hasStreamFailed(stream.content) == true) {
                            val working =
                                qualityManager?.getWorkingStreams()?.maxByOrNull {
                                    VideoCodecUtils.qualityHeightFromStream(it)
                                }
                            if (working != null) {
                                currentVideoStream = working
                                qualityManager?.resetStreamErrors()
                            } else {
                                onPlaybackShutdown()
                                return@launch
                            }
                        }
                    }

                    currentVideoStream?.let { loadMediaInternal(it, currentAudioStream, pos) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reloading", e)
                    onPlaybackShutdown()
                } finally {
                    pendingReloadJob = null
                }
            }
    }

    private fun attemptQualityDowngrade() {
        val newStream = qualityManager?.attemptQualityDowngrade()
        if (newStream != null) {
            currentVideoStream = newStream
            loadMediaInternal(newStream, currentAudioStream)
        } else {
            _playerState.value =
                _playerState.value.copy(
                    error = PlayerStrings.get(Res.string.error_unable_to_play_quality_options),
                    isPlaying = false,
                    isBuffering = false,
                )
            onPlaybackShutdown()
        }
    }

    private fun onPlaybackShutdown() {
        clearAutoplayCountdownInternal()
        preload.clear()
        errorHandler?.handlePlaybackShutdown(player)
    }

    /**
     * Called by [PlaybackRefocusEffect] when the player is stuck in an unrecoverable state
     * after a screen-off/on cycle (duration still 0 after all poll attempts).
     */
    fun handleRefocusStuck(videoId: String?) {
        val p = player ?: return
        if (isRecoveringStreams(videoId) || hasAbandonedPlayback()) return
        if (reloadClearedMediaIfNeeded()) {
            return
        }
        errorHandler?.handleRefocusStuck(p, videoId)
    }
}

// Backward compatibility type aliases
typealias EnhancedPlayerState = io.github.aedev.flow.player.state.EnhancedPlayerState
typealias QualityOption = io.github.aedev.flow.player.state.QualityOption
typealias AudioTrackOption = io.github.aedev.flow.player.state.AudioTrackOption
typealias SubtitleOption = io.github.aedev.flow.player.state.SubtitleOption

/**
 * How long a re-attached surface may stay blank before it is treated as stuck rather than slow. A
 * first frame measured on a real device lands around 120 ms.
 */
private const val SURFACE_FIRST_FRAME_TIMEOUT_MS = 1_200L

/** A rendition of a live HLS master playlist, what Media3 reads from its video track formats. */
internal data class LiveVariant(
    val width: Int,
    val height: Int,
    val frameRate: Float,
)

/** The variants of an HLS master playlist, from each EXT-X-STREAM-INF's RESOLUTION and FRAME-RATE. */
internal fun liveVariants(master: String): List<LiveVariant> =
    master
        .lineSequence()
        .filter { it.startsWith("#EXT-X-STREAM-INF:") }
        .mapNotNull { line ->
            val resolution = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line) ?: return@mapNotNull null
            val frameRate = Regex("""FRAME-RATE=([0-9.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            LiveVariant(resolution.groupValues[1].toInt(), resolution.groupValues[2].toInt(), frameRate)
        }.toList()
