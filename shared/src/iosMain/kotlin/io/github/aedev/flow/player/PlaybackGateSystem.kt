@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.engine.AvPlayerEngine
import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.quality.AdaptiveLadder
import io.github.aedev.flow.player.resolver.PlatformCodecSupport
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.resolver.engineFor
import io.github.aedev.flow.player.stream.InnerTubeVideoMapper
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.VideoExtractionResult
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.ui.components.videoplayer.gesture.videoPlayerControls
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.schabi.newpipe.extractor.stream.VideoStream
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.currentRoute
import platform.AVFAudio.outputVolume
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVPlayerItemTrack
import platform.AVFoundation.externalPlaybackActive
import platform.AVFoundation.mediaType
import platform.AVFoundation.tracks
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowScene
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * S11's gate items: what only a phone proves about the system around the players. Each needs the person holding the
 * phone, and says on screen what to do before it waits: lock the phone while a video plays on each engine (background
 * playback), use the lock screen's controls (Now Playing and the remote commands), open Picture in Picture by a tap and
 * by leaving the app (on both engines, and keep the window when the next video opens on the other engine), swipe the
 * player's gestures and press a volume key, and pick an AirPlay receiver if one is at hand. The players run as the app
 * runs them: the manager with SystemIntegration installed (iOSApp.swift).
 */
internal class SystemGate(
    private val players: PlayerGate,
    private val stage: (String) -> Unit,
    private val extraction: suspend (String) -> VideoExtractionResult?,
    private val button: (String, () -> Unit) -> UIView?,
) {
    private val manager = EnhancedPlayerManager.getInstance()
    private val clock = TimeSource.Monotonic.markNow()
    private val events = mutableListOf<String>()
    private var listening = false
    private val listener =
        object : FlowPlayer.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) = note("playWhenReady=$playWhenReady reason=$reason")

            override fun onMediaItemTransition(
                mediaId: String?,
                reason: Int,
            ) = note("item=$mediaId")
        }

    private fun note(event: String) {
        events += "${clock.elapsedNow().inWholeMilliseconds} ${appState()} $event"
    }

    private fun appState(): String =
        when (UIApplication.sharedApplication.applicationState) {
            UIApplicationState.UIApplicationStateActive -> "active"
            UIApplicationState.UIApplicationStateInactive -> "inactive"
            else -> "background"
        }

    private suspend fun <T> main(block: suspend () -> T): T = players.main(block)

    private suspend fun inBackground(): Boolean = main { UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateBackground }

    private suspend fun listen() =
        main {
            if (!listening) manager.getPlayer()?.addListener(listener)
            listening = true
        }

    private suspend fun engineName(): String? = main { manager.getPlayer()?.engine?.value?.let { it::class.simpleName } }

    /** AVPlayer's item has an enabled audio track: the resolver gave it a format it decodes. Null on the bundled engine. */
    private suspend fun avPlayerHasAudio(): Boolean? =
        main {
            val engine = manager.getPlayer()?.engine?.value as? AvPlayerEngine ?: return@main null
            engine.item?.tracks?.any { (it as? AVPlayerItemTrack)?.let { track -> track.enabled && track.assetTrack?.mediaType == AVMediaTypeAudio } == true } == true
        }

    /** Shows [label] as a button with [instruction] above it and waits for the tap. */
    private suspend fun tap(
        label: String,
        instruction: String,
        timeout: Duration = 5.minutes,
        onTap: () -> Unit = {},
    ): Boolean {
        val tapped = CompletableDeferred<Unit>()
        val view =
            main {
                // Inside the tap: iOS starts Picture in Picture only for one.
                button(label) {
                    onTap()
                    tapped.complete(Unit)
                }
            }
        stage(instruction)
        val done = withTimeoutOrNull(timeout) { tapped.await() } != null
        main { view?.removeFromSuperview() }
        return done
    }

    private fun h264(streams: List<VideoStream>): VideoStream? =
        streams
            .filter { VideoCodecUtils.codecKeyFromStream(it) == "h264" && VideoCodecUtils.qualityHeightFromStream(it) <= 1080 }
            .maxByOrNull(VideoCodecUtils::qualityHeightFromStream)

    private fun vp9(streams: List<VideoStream>): VideoStream? =
        streams
            .filter {
                VideoCodecUtils.codecKeyFromStream(it) == "vp9" && !AdaptiveLadder.isHighBitDepth(it) &&
                    VideoCodecUtils.qualityHeightFromStream(it) <= 1080 && PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.BUNDLED
            }.maxByOrNull(VideoCodecUtils::qualityHeightFromStream)

    private fun failed(
        why: String,
        extra: JsonObject? = null,
    ) = buildJsonObject {
        put("verdict", "FAIL")
        put("error", why)
        extra?.forEach { (key, value) -> put(key, value) }
    }

    /** Loads [videoId] on [onBundled]'s engine and waits until it plays; null when it does. */
    private suspend fun start(
        item: String,
        videoId: String,
        onBundled: Boolean,
    ): JsonObject? {
        stage("$item: extraction")
        val result = extraction(videoId) ?: return players.noVideo(item, videoId)
        stage("$item: loading")
        // The player screen's PlaybackPreparer.beginSession (S14): the session is armed before the streams arrive.
        main {
            manager.initialize()
            manager.startBackgroundService(videoId, title = videoId, channel = "", thumbnail = "")
        }
        players.load(videoId, result, pick = if (onBundled) ::vp9 else ::h264)
        listen()
        val playing = players.waitFor(40.seconds) { players.position() > 2_000 && main { manager.isPlaying() } }
        return if (playing) null else failed("the video did not start", buildJsonObject { put("engine", engineName()) })
    }

    // ----- background playback -----

    /** F05: a video keeps playing with the phone locked, audio only, and its picture is back after unlocking. */
    suspend fun background(
        videoId: String,
        onBundled: Boolean,
    ): JsonObject {
        val item = if (onBundled) "s11BackgroundBundled" else "s11BackgroundAvPlayer"
        start(item, videoId, onBundled)?.let { return it }
        val engine = engineName()
        val audio = avPlayerHasAudio()
        if (!tap("Ready to lock", "$item: tap \"Ready to lock\", then LOCK THE PHONE (side button). Keep it locked about 20 s and listen. Then unlock.")) {
            return failed("nobody tapped within 5 minutes")
        }
        if (!players.waitFor(60.seconds) { inBackground() }) return failed("the phone was not locked within 60 s")
        stage("$item: LOCKED — listen for about 20 s, then unlock")
        val from = players.position()
        val locked = TimeSource.Monotonic.markNow()
        delay(3_000)
        val audioOnlyWhileLocked = main { manager.isInAudioOnlyMode() }
        val nowPlaying = nowPlaying()
        players.waitFor(4.minutes) { !inBackground() }
        val seconds = locked.elapsedNow().inWholeMilliseconds / 1000.0
        val to = players.position()
        val rate = (to - from) / 1000.0 / seconds
        stage("$item: unlocked, checking the picture came back")
        val videoBack = players.waitFor(10.seconds) { main { !manager.isInAudioOnlyMode() && manager.isPlaying() } }
        val pass = seconds >= 15 && rate in 0.9..1.1 && audioOnlyWhileLocked && videoBack && audio != false
        return buildJsonObject {
            put("verdict", if (pass) "PASS" else "FAIL")
            put("engine", engine)
            put("avPlayerAudioTrack", audio)
            put("lockedSeconds", seconds)
            put("clockRateWhileLocked", rate)
            put("audioOnlyWhileLocked", audioOnlyWhileLocked)
            put("videoBackAfterUnlock", videoBack)
            put("nowPlaying", nowPlaying)
        }
    }

    private suspend fun nowPlaying(): JsonObject =
        main {
            val info = MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo.orEmpty()
            buildJsonObject {
                put("title", info[MPMediaItemPropertyTitle]?.toString())
                put("artist", info[MPMediaItemPropertyArtist]?.toString())
                put("duration", info[MPMediaItemPropertyPlaybackDuration]?.toString())
                put("artwork", info[MPMediaItemPropertyArtwork] != null)
            }
        }

    // ----- lock screen -----

    private suspend fun queueOf(vararg ids: String): List<Video>? =
        ids.map { id ->
            val result = extraction(id) ?: return null
            InnerTubeVideoMapper.videoFromResult(id, result, Video(id = id, title = "", channelName = "", channelId = "", thumbnailUrl = "", duration = 0, viewCount = 0L, uploadDate = ""))
        }

    /** [videoId] is the current video and plays: it opened, not only took its place in the queue. */
    private suspend fun playing(videoId: String): Boolean =
        main { manager.playerState.value.currentVideoId == videoId && manager.isPlaying() && manager.getCurrentPosition() > 1_000 }

    private fun remoteCommandsSince(mark: Int): List<String> =
        PlayerDiagnostics.entries().drop(mark).map { it.toString() }.filter { "remote command" in it }.map { it.substringAfter("remote command ").trim() }

    /** Now Playing on the lock screen and its buttons: pause, play, next, previous and the progress bar. */
    suspend fun lockScreen(
        firstId: String,
        secondId: String,
    ): JsonObject {
        val item = "s11LockScreen"
        stage("$item: extraction")
        val videos = queueOf(firstId, secondId) ?: return players.noVideo(item, firstId)
        main { manager.setQueue(videos, 0, title = "S11 gate") }
        start(item, firstId, onBundled = false)?.let { return it }
        val engine = engineName()
        if (!tap(
                "Ready to lock",
                "$item: tap \"Ready to lock\", then LOCK THE PHONE and wake the screen (don't unlock). On the lock screen press, in this order: " +
                    "1 Pause, 2 Play, 3 Next (skip forward), 4 Previous (skip back), 5 drag the progress bar. Then unlock.",
            )
        ) {
            return failed("nobody tapped within 5 minutes")
        }
        val mark = PlayerDiagnostics.entries().size
        val eventsMark = main { events.size }
        if (!players.waitFor(60.seconds) { inBackground() }) return failed("the phone was not locked within 60 s")
        stage("$item: LOCKED — press Pause, Play, Next, Previous, then drag the bar; then unlock")
        delay(2_000)
        val nowPlaying = nowPlaying()
        var videoAfterNext: String? = null
        var videoAfterPrevious: String? = null
        players.waitFor(6.minutes) {
            val commands = remoteCommandsSince(mark)
            if (videoAfterNext == null && commands.any { it.startsWith("Next") }) {
                // The next video opens through the service layer, as upstream's session does, and has to play.
                if (players.waitFor(40.seconds) { playing(secondId) }) videoAfterNext = secondId
            }
            if (videoAfterPrevious == null && commands.any { it.startsWith("Previous") }) {
                if (players.waitFor(40.seconds) { playing(firstId) }) videoAfterPrevious = firstId
            }
            !inBackground()
        }
        val commands = remoteCommandsSince(mark)
        val seen = { name: String -> commands.any { it.startsWith(name) } }
        // The lock screen sends play/pause as either pair of commands; what counts is the player paused and played on.
        val locked = main { events.drop(eventsMark) }.filter { " background " in it }
        val paused = locked.indexOfFirst { "playWhenReady=false" in it }
        val playedAgain = paused >= 0 && locked.drop(paused + 1).any { "playWhenReady=true" in it }
        val pass =
            paused >= 0 && playedAgain && seen("Next") && seen("Previous") && seen("SeekTo") &&
                videoAfterNext == secondId && videoAfterPrevious == firstId && nowPlaying["title"]?.toString()?.trim('"').orEmpty().isNotBlank()
        return buildJsonObject {
            put("verdict", if (pass) "PASS" else "FAIL")
            put("engine", engine)
            put("nowPlaying", nowPlaying)
            put("commands", buildJsonArray { commands.forEach { add(JsonPrimitive(it)) } })
            put("pausedWhileLocked", paused >= 0)
            put("playedAgainWhileLocked", playedAgain)
            put("videoAfterNext", videoAfterNext)
            put("videoAfterPrevious", videoAfterPrevious)
            put("events", buildJsonArray { main { events.drop(eventsMark) }.takeLast(30).forEach { add(JsonPrimitive(it)) } })
        }
    }

    // ----- Picture in Picture -----

    private suspend fun pipActive(): Boolean = main { PictureInPicture.isActive }

    private fun remoteChanges(from: Int): List<String> = events.drop(from).filter { "reason=${FlowPlayer.PLAY_WHEN_READY_CHANGE_REASON_REMOTE}" in it }

    /**
     * F06 by the player's button: the window opens over AVPlayer, its own pause and play drive the player, and when the
     * queue moves to the next video (on the bundled engine when Auto picks VP9) the window stays and shows it; its
     * restore button brings the app back.
     */
    suspend fun pictureInPicture(
        firstId: String,
        secondId: String,
    ): JsonObject {
        val item = "s11Pip"
        stage("$item: extraction")
        val videos = queueOf(firstId, secondId) ?: return players.noVideo(item, firstId)
        main { manager.setQueue(videos, 0, title = "S11 gate") }
        start(item, firstId, onBundled = false)?.let { return it }
        val firstEngine = engineName()
        var requested = false
        if (!tap("Start Picture in Picture", "$item: tap \"Start Picture in Picture\"") { requested = PictureInPictureHelper.requestPlayerPipMode() }) {
            return failed("nobody tapped within 5 minutes")
        }
        val opened = players.waitFor(10.seconds) { pipActive() }
        if (!opened) return failed("the window did not open", buildJsonObject { put("requested", requested) })
        val mark = main { events.size }
        stage("$item: go to the HOME SCREEN (swipe up). Tap the small window and press its PAUSE, then PLAY.")
        players.waitFor(2.minutes) { inBackground() }
        val from = players.position()
        players.waitFor(2.minutes) {
            val remote = main { remoteChanges(mark) }
            remote.any { "playWhenReady=false" in it } && remote.any { "playWhenReady=true" in it }
        }
        val remote = main { remoteChanges(mark) }
        delay(4_000)
        val advancedInWindow = players.position() - from
        stage("$item: the gate opens the next video in the window; keep watching it")
        main { manager.playNext() }
        val nextPlays =
            players.waitFor(40.seconds) {
                main { manager.playerState.value.currentVideoId == secondId && manager.isPlaying() } && players.position() > 2_000
            }
        delay(3_000)
        val secondEngine = engineName()
        val keptAcrossEngines = nextPlays && pipActive()
        stage("$item: tap the small window and press its RESTORE button (top right) to come back to the app")
        val restored = players.waitFor(3.minutes) { !inBackground() && !pipActive() }
        val pausedFromWindow = remote.any { "playWhenReady=false" in it }
        val playedFromWindow = remote.any { "playWhenReady=true" in it }
        val pass = pausedFromWindow && playedFromWindow && advancedInWindow > 2_000 && keptAcrossEngines && restored
        return buildJsonObject {
            put("verdict", if (pass) "PASS" else "FAIL")
            put("firstEngine", firstEngine)
            put("secondEngine", secondEngine)
            put("pausedFromWindow", pausedFromWindow)
            put("playedFromWindow", playedFromWindow)
            put("advancedInWindowMs", advancedInWindow)
            put("nextVideoPlaysInWindow", nextPlays)
            put("windowKeptAcrossEngines", keptAcrossEngines)
            put("restored", restored)
        }
    }

    /** Upstream's auto PiP setting: leaving the app while a video plays opens the window by itself, here on the bundled engine. */
    suspend fun autoPictureInPicture(videoId: String): JsonObject {
        val item = "s11AutoPip"
        val preferences = PlayerPreferences()
        preferences.setAutoPipEnabled(true)
        try {
            start(item, videoId, onBundled = true)?.let { return it }
            val engine = engineName()
            if (!tap("Ready", "$item: tap \"Ready\", then go to the HOME SCREEN (swipe up) and tap nothing else: the video should go on in a small window by itself.")) {
                return failed("nobody tapped within 5 minutes")
            }
            players.waitFor(60.seconds) { inBackground() }
            val opened = players.waitFor(5.seconds) { pipActive() }
            val from = players.position()
            delay(6_000)
            val advanced = players.position() - from
            stage("$item: tap the small window and press its RESTORE button to come back")
            val restored = players.waitFor(3.minutes) { !inBackground() && !pipActive() }
            return buildJsonObject {
                put("verdict", if (opened && advanced > 4_000 && restored) "PASS" else "FAIL")
                put("engine", engine)
                put("openedByItself", opened)
                put("advancedInWindowMs", advanced)
                put("restored", restored)
            }
        } finally {
            withContext(NonCancellable) { preferences.setAutoPipEnabled(false) }
        }
    }

    // ----- gestures -----

    private class GestureLog {
        val brightness = mutableListOf<Float>()
        val volume = mutableListOf<Float>()
        var seekDrags = 0
        var seekDragTarget = -1L
        var doubleTapSeconds = 0
    }

    /**
     * F10: the player's own gestures on a fullscreen surface over the video (upstream's videoPlayerControls): brightness
     * on the left half, volume on the right (through the hidden MPVolumeView, without iOS's indicator), a sideways drag to
     * seek, a double-tap to skip, and a volume key shown on the player's overlay.
     */
    suspend fun gestures(videoId: String): JsonObject {
        val item = "s11Gestures"
        start(item, videoId, onBundled = false)?.let { return it }
        val log = GestureLog()
        val brightnessBefore = main { UIScreen.mainScreen.brightness }
        val volumeBefore = main { AVAudioSession.sharedInstance().outputVolume }
        val keysBefore = PlayerHardwareController.volumeKeySignal.value
        val done = CompletableDeferred<Unit>()
        val surface =
            main {
                PlayerHardwareController.setFullscreenVideoActive(true)
                PlayerHardwareController.setInAppVolumeOverlayEnabled(true)
                showCompose { GestureSurface(log) { done.complete(Unit) } }
            }
        stage(
            "$item: on the video: 1 swipe UP on the LEFT half (brightness), 2 swipe DOWN on the RIGHT half (volume), " +
                "3 drag SIDEWAYS across the middle (seek), 4 DOUBLE-TAP the right side (+10 s), 5 press the VOLUME UP key once. Then tap Done.",
        )
        withTimeoutOrNull(6.minutes) { done.await() }
        val brightnessDuring = main { UIScreen.mainScreen.brightness }
        val volumeAfter = main { AVAudioSession.sharedInstance().outputVolume }
        val keys = PlayerHardwareController.volumeKeySignal.value - keysBefore
        main {
            close(surface)
            PlayerHardwareController.setFullscreenVideoActive(false)
            // Leaving the player gives the screen its own brightness back (upstream's PlayerWindowEffects, S14).
            ScreenBrightness.apply(-1f)
        }
        val brightnessAfter = main { UIScreen.mainScreen.brightness }
        val brightnessChanged = log.brightness.isNotEmpty() && abs(brightnessDuring - brightnessBefore) > 0.02
        val brightnessRestored = abs(brightnessAfter - brightnessBefore) < 0.02
        val volumeChanged = log.volume.isNotEmpty() && abs(volumeAfter - volumeBefore) > 0.03f
        val pass = brightnessChanged && brightnessRestored && volumeChanged && log.seekDrags > 0 && log.doubleTapSeconds > 0 && keys > 0
        return buildJsonObject {
            put("verdict", if (pass) "PASS" else "FAIL")
            put("brightnessBefore", brightnessBefore)
            put("brightnessDuring", brightnessDuring)
            put("brightnessRestoredTo", brightnessAfter)
            put("brightnessSteps", log.brightness.size)
            put("volumeBefore", volumeBefore)
            put("volumeAfter", volumeAfter)
            put("volumeSteps", log.volume.size)
            put("seekDrags", log.seekDrags)
            put("seekDragTargetMs", log.seekDragTarget)
            put("doubleTapSeconds", log.doubleTapSeconds)
            put("volumeKeySignals", keys)
        }
    }

    @Composable
    private fun GestureSurface(
        log: GestureLog,
        onDone: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        var brightness by remember { mutableStateOf(-1f) }
        var volume by remember { mutableStateOf((SystemVolume.steps() ?: 0).toFloat() / SystemVolume.maxSteps) }
        var readout by remember { mutableStateOf("") }
        val keys by PlayerHardwareController.volumeKeySignal.collectAsState()
        Box(
            Modifier
                .fillMaxSize()
                .videoPlayerControls(
                    isSpeedBoostActive = false,
                    onSpeedBoostChange = {},
                    showControls = false,
                    onShowControlsChange = {},
                    onShowSeekBackChange = {},
                    onShowSeekForwardChange = {},
                    onSeekAccumulate = { seconds ->
                        if (seconds > 0) log.doubleTapSeconds = seconds
                        readout = "double-tap ${seconds}s"
                    },
                    currentPosition = { manager.getCurrentPosition() },
                    duration = manager.getPlayer()?.duration?.takeIf { it > 0 } ?: 0L,
                    scope = scope,
                    isFullscreen = true,
                    onBrightnessChange = {
                        brightness = it
                        log.brightness += it
                    },
                    onShowBrightnessChange = {},
                    onVolumeChange = {
                        volume = it
                        log.volume += it
                    },
                    onShowVolumeChange = {},
                    onSeekDragChange = { dragging -> if (!dragging) log.seekDrags++ },
                    onSeekDragUpdate = { target, _ -> log.seekDragTarget = target },
                    brightnessLevel = { brightness },
                    volumeLevel = { volume },
                    maxVolume = SystemVolume.maxSteps,
                ),
        ) {
            val style = TextStyle(color = Color.White, fontSize = 16.sp)
            Column(Modifier.align(Alignment.TopCenter).padding(top = 300.dp).background(Color.Black.copy(alpha = 0.6f)).padding(8.dp)) {
                BasicText("brightness ${(brightness * 100).toInt()}%  volume ${(volume * 100).toInt()}%  volume keys $keys", style = style)
                BasicText("seek drag to ${log.seekDragTarget / 1000} s  $readout", style = style)
            }
            BasicText(
                "Done",
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp)
                        .clickable(onClick = onDone)
                        .background(Color(0xFF2962FF))
                        .padding(horizontal = 48.dp, vertical = 16.dp),
                style = TextStyle(color = Color.White, fontSize = 22.sp),
            )
        }
    }

    /**
     * A see-through Compose surface over the video, in a window of its own above the app's: SwiftUI's hosting controller
     * owns the app window's root, and UIKit refuses a child controller whose view lives elsewhere (phone run 1's crash).
     * It is not made key, so the gate's overlay and buttons stay in the app's window, shown through it.
     */
    private fun showCompose(content: @Composable () -> Unit): UIWindow? {
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene ?: return null
        // opaque = false: CMP's opt-in for a transparent surface.
        @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
        val controller = ComposeUIViewController(configure = { opaque = false }) { content() }
        controller.view.backgroundColor = UIColor.clearColor
        return UIWindow(windowScene = scene).apply {
            windowLevel = UIWindowLevelNormal + 1.0
            backgroundColor = UIColor.clearColor
            rootViewController = controller
            hidden = false
        }
    }

    private fun close(window: UIWindow?) {
        window ?: return
        window.hidden = true
        window.rootViewController = null
    }

    // ----- AirPlay -----

    /**
     * AirPlay, when a receiver is at hand: the route picker, a VP9 video on the bundled engine reopening on AVPlayer
     * while AirPlay is the route, and AVPlayer sending the video out. Without a receiver it is NOT_TESTED.
     */
    suspend fun airPlay(videoId: String): JsonObject {
        val item = "s11AirPlay"
        start(item, videoId, onBundled = true)?.let { return it }
        val engineBefore = engineName()
        val picker =
            main {
                showCompose {
                    Box(Modifier.fillMaxSize()) {
                        AirPlayRouteButton(Modifier.align(Alignment.Center).size(88.dp), tint = Color.White, activeTint = Color(0xFF2962FF))
                    }
                }
            }
        stage("$item: if an AirPlay receiver (Apple TV, AirPlay TV) is at hand, tap the AirPlay button in the middle and pick it within 90 s. Otherwise just wait.")
        val routed = players.waitFor(90.seconds) { main { PlatformCodecSupport.airPlayActive } }
        val route = main { AVAudioSession.sharedInstance().currentRoute.outputs.joinToString { (it as? AVAudioSessionPortDescription)?.portName.orEmpty() } }
        var engineOnAirPlay: String? = null
        var external = false
        if (routed) {
            stage("$item: AirPlay picked; the video should reopen on AVPlayer and show on the receiver")
            players.waitFor(30.seconds) { engineName() == AvPlayerEngine::class.simpleName && main { manager.isPlaying() } }
            delay(8_000)
            engineOnAirPlay = engineName()
            external = main { (manager.getPlayer()?.engine?.value as? AvPlayerEngine)?.player?.externalPlaybackActive == true }
            stage("$item: now turn AirPlay off again with the same button (pick this iPhone)")
            players.waitFor(90.seconds) { main { !PlatformCodecSupport.airPlayActive } }
        }
        main {
            close(picker)
        }
        return buildJsonObject {
            put("verdict", if (!routed) "NOT_TESTED" else if (engineOnAirPlay == AvPlayerEngine::class.simpleName) "PASS" else "FAIL")
            if (!routed) put("why", "no AirPlay receiver was picked")
            put("engineBefore", engineBefore)
            put("route", route)
            put("engineOnAirPlay", engineOnAirPlay)
            put("externalPlaybackActive", external)
        }
    }
}
