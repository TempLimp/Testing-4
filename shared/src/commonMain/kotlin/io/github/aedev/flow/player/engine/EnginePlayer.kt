package io.github.aedev.flow.player.engine

import io.github.aedev.flow.player.engine.FlowPlayer.Companion.MEDIA_ITEM_TRANSITION_REASON_AUTO
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.MEDIA_ITEM_TRANSITION_REASON_REPEAT
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.REPEAT_MODE_OFF
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.REPEAT_MODE_ONE
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.STATE_BUFFERING
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.STATE_ENDED
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.STATE_IDLE
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.STATE_READY
import io.github.aedev.flow.player.engine.FlowPlayer.Companion.TIME_UNSET
import io.github.aedev.flow.player.renderer.subtitle.Cue
import io.github.aedev.flow.player.renderer.subtitle.Subtitle
import io.github.aedev.flow.player.resolver.MediaSource
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ExoPlayer's role on iOS: a playlist of lazily resolved sources (the playing item and a next one prepared ahead), each
 * played by the engine its streams need ([MediaSource.engine]), with Media3's states, listener callbacks, repeat mode,
 * speed and captions. The engines are read on a short tick ([TICK_MS] while playing or buffering, [IDLE_TICK_MS]
 * otherwise, sooner when a caption changes), since neither calls back.
 *
 * Main thread only (its [scope] runs there), as ExoPlayer binds to its looper.
 */
class EnginePlayer(
    private val scope: CoroutineScope,
    private val createEngine: (PlaybackEngineKind) -> EngineControl,
) : FlowPlayer {
    private class Item(
        val source: LazyMediaSource,
    ) {
        var engine: EngineControl? = null
        var resolving: Job? = null
        var resolved: MediaSource? = null
        var failure: PlaybackException? = null

        /** Where the item starts, or stands while it has no engine (before preparing, after [stop]). */
        var positionMs: Long = 0
    }

    private val listeners = mutableListOf<FlowPlayer.Listener>()
    private val items = mutableListOf<Item>()
    private val current: Item? get() = items.firstOrNull()

    private val _engine = MutableStateFlow<EngineControl?>(null)

    /** The engine of the playing item, whose picture the player view shows. */
    val engine: StateFlow<EngineControl?> = _engine.asStateFlow()

    private var state = STATE_IDLE
    private var reportedPlaying = false
    private var firstFrameReported = false
    private var reportedSize = VideoSize.UNKNOWN
    private var cues: List<Cue> = emptyList()
    private var subtitle: Subtitle? = null
    private var muted = false
    private var seekExact = false
    private var ticker: Job? = null

    override val playbackState: Int get() = state

    override var playWhenReady: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            current?.engine?.let { if (value) it.play() else it.pause() }
            notify { it.onPlayWhenReadyChanged(value, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
            tick()
        }

    override val isPlaying: Boolean get() = current?.engine?.isPlaying() == true

    override val currentPosition: Long
        get() = current?.let { item -> item.engine?.currentPositionMs() ?: item.positionMs } ?: 0L

    override val bufferedPosition: Long
        get() = current?.engine?.bufferedPositionMs() ?: currentPosition

    override val duration: Long
        get() {
            val item = current ?: return TIME_UNSET
            val known = item.engine?.durationMs()?.takeIf { it > 0 } ?: item.resolved?.durationMs?.takeIf { it > 0 }
            return known ?: TIME_UNSET
        }

    override val mediaItemCount: Int get() = items.size

    override val currentMediaItemIndex: Int get() = 0

    override val currentMediaId: String? get() = current?.source?.mediaId

    override val isCurrentMediaItemLive: Boolean get() = current?.source?.isLive == true

    override var repeatMode: Int = REPEAT_MODE_OFF

    override var playbackSpeed: Float = 1f
        set(value) {
            field = value
            items.forEach { it.engine?.setPlaybackRate(value) }
        }

    override var volume: Float = 1f
        set(value) {
            field = value
            applyVolume()
        }

    override val videoSize: VideoSize get() = reportedSize

    override val currentCues: List<Cue> get() = cues

    /** Media3's SeekParameters: EXACT lands on the frame asked for, CLOSEST_SYNC on the nearest keyframe (faster). */
    fun setSeekParameters(exact: Boolean) {
        seekExact = exact
        items.forEach { it.engine?.setSeekExact(exact) }
    }

    override fun play() {
        playWhenReady = true
    }

    override fun pause() {
        playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        val item = current ?: return
        val target = positionMs.coerceAtLeast(0L)
        val engine = item.engine
        if (engine != null) {
            engine.seekTo(target)
            if (state == STATE_ENDED) setState(STATE_BUFFERING)
        } else {
            item.positionMs = target
        }
        updateCues()
        tick()
    }

    override fun seekToDefaultPosition() {
        val item = current ?: return
        if (!item.source.isLive) {
            seekTo(0)
            return
        }
        val edge = duration.takeIf { it != TIME_UNSET } ?: return
        seekTo((edge - (item.resolved?.liveTargetOffsetMs ?: 0L)).coerceAtLeast(0L))
    }

    override fun stop() {
        for (item in items) {
            item.positionMs = item.engine?.currentPositionMs() ?: item.positionMs
            closeEngine(item)
        }
        _engine.value = null
        resetPictureState()
        setState(STATE_IDLE)
        reportPlaying(false)
    }

    override fun clearMediaItems() {
        val hadItems = items.isNotEmpty()
        items.forEach(::closeEngine)
        items.clear()
        _engine.value = null
        resetPictureState()
        reportPlaying(false)
        if (state != STATE_IDLE) setState(STATE_ENDED)
        if (hadItems) notify { it.onMediaItemTransition(null, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
    }

    override fun setMediaSource(source: LazyMediaSource) {
        items.forEach(::closeEngine)
        items.clear()
        items += Item(source)
        _engine.value = null
        resetPictureState()
        reportPlaying(false)
        notify { it.onMediaItemTransition(source.mediaId, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
        // A prepared player prepares what replaces its playlist, as Media3 does.
        if (state != STATE_IDLE) prepare()
    }

    override fun addMediaSource(source: LazyMediaSource) {
        val item = Item(source)
        items += item
        if (state != STATE_IDLE && items.size > 1) prepareItem(item)
    }

    override fun removeMediaItem(index: Int) {
        val item = items.getOrNull(index) ?: return
        closeEngine(item)
        items.removeAt(index)
        if (index == 0) becomeCurrent(MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
    }

    override fun prepare() {
        val item = current
        if (item == null) {
            setState(STATE_ENDED)
            return
        }
        prepareItem(item)
        items.drop(1).forEach(::prepareItem)
        setState(item.engine?.playbackState() ?: STATE_BUFFERING)
        ensureTicking()
        tick()
    }

    override fun mute() {
        muted = true
        applyVolume()
    }

    override fun unmute() {
        muted = false
        applyVolume()
    }

    override fun setSubtitle(subtitle: Subtitle?) {
        this.subtitle = subtitle
        updateCues()
        ensureTicking()
    }

    override fun addListener(listener: FlowPlayer.Listener) {
        listeners += listener
    }

    override fun removeListener(listener: FlowPlayer.Listener) {
        listeners -= listener
    }

    /** Stops for good: what a released ExoPlayer is. */
    fun release() {
        ticker?.cancel()
        ticker = null
        clearMediaItems()
        listeners.clear()
    }

    // ----- items -----

    private fun prepareItem(item: Item) {
        if (item.engine != null || item.resolving?.isActive == true) return
        item.failure = null
        item.resolving =
            scope.launch {
                val resolved =
                    try {
                        item.source.resolve()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: PlaybackException) {
                        item.failure = e
                        null
                    } catch (e: Exception) {
                        item.failure = PlaybackException(e.message, e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
                        null
                    }
                if (resolved != null && item in items) open(item, resolved)
                tick()
            }
    }

    private fun open(
        item: Item,
        resolved: MediaSource,
    ) {
        val engine = createEngine(resolved.engine)
        item.resolved = resolved
        item.engine = engine
        engine.setPlaybackRate(playbackSpeed)
        engine.setVolume(if (muted) 0f else volume)
        engine.setSeekExact(seekExact)
        engine.prepare(resolved)
        if (item.positionMs > 0) engine.seekTo(item.positionMs)
        if (item === current) {
            _engine.value = engine
            if (playWhenReady) engine.play()
        }
        Log.d(TAG, "opened ${item.source.mediaId} on ${resolved.engine} at ${item.positionMs}ms")
    }

    private fun closeEngine(item: Item) {
        item.resolving?.cancel()
        item.resolving = null
        item.engine?.close()
        item.engine = null
    }

    /** The next item takes over: prepared ahead, it only has to start. */
    private fun becomeCurrent(reason: Int) {
        resetPictureState()
        val item = current
        if (item == null) {
            _engine.value = null
            reportPlaying(false)
            setState(STATE_ENDED)
            return
        }
        prepareItem(item)
        _engine.value = item.engine
        if (playWhenReady) item.engine?.play()
        notify { it.onMediaItemTransition(item.source.mediaId, reason) }
        setState(item.engine?.playbackState()?.takeIf { it != STATE_IDLE } ?: STATE_BUFFERING)
        tick()
    }

    // ----- the tick -----

    private fun ensureTicking() {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch {
                while (isActive && items.isNotEmpty()) {
                    tick()
                    delay(nextTickMs())
                }
                ticker = null
            }
    }

    private fun nextTickMs(): Long {
        val playing = isPlaying
        val base = if (playing || state == STATE_BUFFERING) TICK_MS else IDLE_TICK_MS
        val captions = subtitle ?: return base
        if (!playing) return base
        val nowUs = currentPosition * 1000
        val next = captions.getNextEventTimeIndex(nowUs).takeIf { it >= 0 } ?: return base
        val untilMs = ((captions.getEventTime(next) - nowUs) / 1000 / playbackSpeed.coerceAtLeast(0.1f)).toLong()
        return untilMs.coerceIn(MIN_TICK_MS, base)
    }

    private fun tick() {
        val item = current ?: return
        item.failure?.let { failure ->
            item.failure = null
            fail(item, failure)
            return
        }
        val engine = item.engine
        if (engine == null) {
            if (item.resolving?.isActive == true) setState(STATE_BUFFERING)
            return
        }
        engine.error()?.let { message ->
            fail(item, PlaybackException(message, null, engine.errorCode()))
            return
        }
        val engineState = engine.playbackState()
        if (engineState == STATE_ENDED) {
            onEnded(item, engine)
            return
        }
        if (engineState != STATE_IDLE) setState(engineState)
        reportPlaying(engine.isPlaying())
        if (!firstFrameReported && engine.hasRenderedFirstFrame()) {
            firstFrameReported = true
            notify { it.onRenderedFirstFrame() }
        }
        val size = VideoSize(engine.videoWidth(), engine.videoHeight())
        if (size.width > 0 && size.height > 0 && size != reportedSize) {
            reportedSize = size
            notify { it.onVideoSizeChanged(size) }
        }
        updateCues()
    }

    private fun onEnded(
        item: Item,
        engine: EngineControl,
    ) {
        when {
            repeatMode == REPEAT_MODE_ONE -> {
                engine.seekTo(0)
                if (playWhenReady) engine.play()
                notify { it.onMediaItemTransition(item.source.mediaId, MEDIA_ITEM_TRANSITION_REASON_REPEAT) }
            }
            items.size > 1 -> {
                closeEngine(item)
                items.removeAt(0)
                becomeCurrent(MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
            else -> {
                reportPlaying(false)
                setState(STATE_ENDED)
            }
        }
    }

    /** As Media3 after an error: idle, the playlist kept, ready to [prepare] again at the same place. */
    private fun fail(
        item: Item,
        error: PlaybackException,
    ) {
        item.positionMs = item.engine?.currentPositionMs() ?: item.positionMs
        closeEngine(item)
        _engine.value = null
        resetPictureState()
        reportPlaying(false)
        setState(STATE_IDLE)
        Log.w(TAG, "playback error ${error.errorCodeName}: ${error.message}")
        notify { it.onPlayerError(error) }
    }

    // ----- helpers -----

    private fun setState(newState: Int) {
        if (state == newState) return
        state = newState
        notify { it.onPlaybackStateChanged(newState) }
        if (newState == STATE_BUFFERING || newState == STATE_READY) ensureTicking()
    }

    private fun reportPlaying(playing: Boolean) {
        if (reportedPlaying == playing) return
        reportedPlaying = playing
        notify { it.onIsPlayingChanged(playing) }
    }

    private fun resetPictureState() {
        firstFrameReported = false
        reportedSize = VideoSize.UNKNOWN
    }

    private fun updateCues() {
        val now = subtitle?.getCues(currentPosition * 1000).orEmpty()
        if (now == cues) return
        cues = now
        notify { it.onCues(now) }
    }

    private fun applyVolume() {
        val level = if (muted) 0f else volume
        items.forEach { it.engine?.setVolume(level) }
    }

    private inline fun notify(event: (FlowPlayer.Listener) -> Unit) {
        listeners.toList().forEach(event)
    }

    private companion object {
        const val TAG = "EnginePlayer"
        const val TICK_MS = 250L
        const val IDLE_TICK_MS = 1_000L
        const val MIN_TICK_MS = 16L
    }
}
