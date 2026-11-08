package io.github.aedev.flow.player

import io.github.aedev.flow.data.local.KeystoreSecretBox
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.player.engine.EngineControl
import io.github.aedev.flow.player.engine.PlaybackEngine
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.stream.CaptionTrackResolver
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoMapper
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.VideoExtractionResult
import io.github.aedev.flow.player.stream.ServicePlaybackStreamSelector
import io.github.aedev.flow.player.surface.VideoSurface
import io.github.aedev.flow.utils.appFilesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.QuartzCore.CALayer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// A TED talk with manual captions and a SponsorBlock sponsor segment early on (18.5-24.8 s, looked up 2026-11-08).
internal const val CAPTIONED_VIDEO_ID = "iG9CE55wbtY"

/**
 * S10's gate items: the ported player manager, as the player screen will drive it, on the phone. Each item loads a video
 * the way the manager's own service-layer load does (extraction, upstream's stream choice, setStreams), shows it, and
 * measures what S10 adds: quality switching, speed, seeking and resume, captions, SponsorBlock, recovery from refused
 * streams (the debug builds' fake stream wall), and the fallback from a SABR session GVS stops after about a minute
 * (S05). The keystore item seals and opens a secret with the Keychain of the signed app (S06's open item).
 */
internal class PlayerGate(
    private val stage: (String) -> Unit,
    private val show: (CALayer) -> Unit,
    private val extraction: suspend (String) -> VideoExtractionResult?,
    /** The gate's verdict for a video no client served: BLOCKED when it was this network's refusal (CI), else FAIL. */
    private val blockedOrFailed: (what: String, id: String, why: String?) -> JsonObject,
) {
    // Set when the gate's video could not be had, so the items that play it on report the same.
    private var unplayable: JsonObject? = null

    private val manager = EnhancedPlayerManager.getInstance()
    private val surface =
        object : VideoSurface {
            override val isValid: Boolean = true

            override fun show(engine: EngineControl?) {
                (engine as? PlaybackEngine)?.layer?.let(this@PlayerGate.show)
            }
        }

    private suspend fun <T> main(block: suspend () -> T): T = withContext(Dispatchers.Main) { block() }

    /** Loads [result] as the manager's own load does, at [startPosition], and shows it. */
    private suspend fun load(
        videoId: String,
        result: VideoExtractionResult,
        startPosition: Long = 0L,
        preferSabr: Boolean = false,
    ) {
        val videos = InnerTubeStreamBridge.convertVideoFormats(result.videoFormats)
        val audios = InnerTubeStreamBridge.convertAudioFormats(result.audioFormats)
        val (video, audio) = ServicePlaybackStreamSelector.selectStreams(videos, audios, VideoQuality.AUTO, "original", "auto")
        val shown = InnerTubeVideoMapper.videoFromResult(videoId, result, Video(id = videoId, title = "", channelName = "", channelId = "", thumbnailUrl = "", duration = 0, viewCount = 0L, uploadDate = ""))
        main {
            // The player screen's part: the manager registers a video's streams for swapping under this video's id.
            GlobalPlayerState.setCurrentVideo(shown)
            manager.initialize()
            manager.attachVideoSurface(surface, forceAttach = true)
            manager.setStreams(
                videoId = videoId,
                videoStream = video,
                audioStream = audio,
                videoStreams = videos,
                audioStreams = audios,
                subtitles = CaptionTrackResolver.resolve(result.playerResponse),
                durationSeconds = InnerTubeVideoMapper.durationSeconds(result),
                dashManifestUrl = result.liveDashUrl,
                hlsUrl = result.liveHlsUrl,
                streamType = InnerTubeVideoMapper.streamType(result),
                startPosition = startPosition,
                sabrInfo = result.sabrInfo,
                itVideoFormats = result.videoFormats,
                itAudioFormats = result.audioFormats,
                preferSabr = preferSabr,
            )
            manager.play()
        }
    }

    private suspend fun position(): Long = main { manager.getCurrentPosition() }

    private suspend fun waitFor(
        timeout: Duration,
        condition: suspend () -> Boolean,
    ): Boolean = withTimeoutOrNull(timeout) {
        while (!condition()) delay(250)
        true
    } == true

    /** Media seconds over wall seconds while playing for [window]. */
    private suspend fun clockRate(window: Duration): Double {
        val before = position()
        val started = TimeSource.Monotonic.markNow()
        delay(window)
        return (position() - before) / 1000.0 / started.elapsedNow().inWholeMilliseconds.times(1.0 / 1000)
    }

    private suspend fun stop() = main { manager.stop() }

    private fun verdict(pass: Boolean) = if (pass) "PASS" else "FAIL"

    private fun noVideo(
        what: String,
        id: String,
    ): JsonObject = blockedOrFailed(what, id, "every client failed")

    suspend fun play(videoId: String): JsonObject {
        stage("s10Play: extraction")
        val result = extraction(videoId) ?: return noVideo("s10Play", videoId).also { unplayable = it }
        stage("s10Play: manager")
        load(videoId, result)
        val started = waitFor(40.seconds) { position() > 1_000 && main { manager.isPlaying() } }
        val firstFrame = waitFor(10.seconds) { main { manager.firstFrameVideoId.value } == videoId }
        val rate = if (started) clockRate(5.seconds) else 0.0
        val state = main { manager.playerState.value }
        return buildJsonObject {
            put("verdict", verdict(started && firstFrame && rate > 0.9))
            put("id", videoId)
            put("client", result.usedClient.clientName)
            put("engine", main { manager.getPlayer()?.engine?.value?.let { it::class.simpleName } })
            put("effectiveQuality", state.effectiveQuality)
            put("qualities", state.availableQualities.joinToString { it.label })
            put("firstFrame", firstFrame)
            put("clockRate", rate)
            put("error", state.error)
        }
    }

    /** A manual pick reopens the stream at the position on the engine its codec needs; Auto goes back to the ladder. */
    suspend fun quality(videoId: String): JsonObject {
        unplayable?.let { return it }
        stage("s10Quality: pick")
        val state = main { manager.playerState.value }
        val target = state.availableQualities.map { it.height }.filter { it in 240..720 && it != state.effectiveQuality }.maxOrNull()
            ?: return buildJsonObject {
                put("verdict", "FAIL")
                put("error", "no other quality offered: ${state.availableQualities.map { it.label }}")
            }
        val before = position()
        main { manager.switchQualityByHeight(target) }
        val switched = waitFor(30.seconds) { main { manager.playerState.value.effectiveQuality } == target && main { manager.isPlaying() } && position() > before }
        val after = position()
        val size = main { manager.getPlayer()?.videoSize }
        stage("s10Quality: auto")
        main { manager.switchQualityByHeight(0) }
        val auto = waitFor(30.seconds) { main { manager.playerState.value.currentQuality } == 0 && main { manager.isPlaying() } }
        return buildJsonObject {
            put("verdict", verdict(switched && auto && after >= before - 2_000))
            put("id", videoId)
            put("picked", target)
            put("positionBefore", before)
            put("positionAfter", after)
            put("pictureAfterPick", size?.let { "${it.width}x${it.height}" })
            put("backToAuto", auto)
        }
    }

    /** 2x, 0.5x and 0.25x keep the clock at that rate (pitch kept); then 1x. */
    suspend fun speed(): JsonObject {
        unplayable?.let { return it }
        val rates = mutableMapOf<Float, Double>()
        for (speed in listOf(2f, 0.5f, 0.25f)) {
            stage("s10Speed: ${speed}x")
            main { manager.setPlaybackSpeed(speed) }
            delay(1_500)
            rates[speed] = clockRate(4.seconds)
        }
        main { manager.setPlaybackSpeed(1f) }
        val pass = rates.all { (speed, rate) -> rate in speed * 0.85..speed * 1.15 }
        return buildJsonObject {
            put("verdict", verdict(pass))
            rates.forEach { (speed, rate) -> put("at${speed}x", rate) }
            put("stateSpeed", main { manager.playerState.value.playbackSpeed })
        }
    }

    /** A seek lands where asked and plays on; a load with a saved position (resume) starts there. */
    suspend fun seek(videoId: String): JsonObject {
        unplayable?.let { return it }
        stage("s10Seek: seek")
        main { manager.seekTo(60_000) }
        val landed = waitFor(20.seconds) { position() in 59_000..75_000 && main { manager.isPlaying() } }
        val afterSeek = position()
        stage("s10Seek: resume")
        stop()
        val result = extraction(videoId) ?: return noVideo("s10Seek", videoId)
        load(videoId, result, startPosition = 30_000)
        val resumed = waitFor(40.seconds) { position() in 29_500..45_000 && main { manager.isPlaying() } }
        return buildJsonObject {
            put("verdict", verdict(landed && resumed))
            put("seekLanded", landed)
            put("positionAfterSeek", afterSeek)
            put("resumedAt30s", resumed)
            put("positionAfterResume", position())
        }
    }

    /** Each caption kind offered (a manual srv3 track, an auto-generated vtt one) shows cues while playing. */
    suspend fun captions(): JsonObject {
        stage("s10Captions: extraction")
        val result = extraction(CAPTIONED_VIDEO_ID) ?: return noVideo("s10Captions", CAPTIONED_VIDEO_ID).also { captionsUnplayable = it }
        stop()
        load(CAPTIONED_VIDEO_ID, result, startPosition = 30_000)
        waitFor(40.seconds) { main { manager.isPlaying() } }
        val options = main { manager.playerState.value.availableSubtitles }
        val picks = listOfNotNull(options.firstOrNull { !it.isAutoGenerated }, options.firstOrNull { it.isAutoGenerated })
        val shown = mutableMapOf<String, String?>()
        for (option in picks) {
            stage("s10Captions: ${option.label}")
            main { manager.selectSubtitle(option.url) }
            waitFor(25.seconds) { main { manager.currentCues.value }.isNotEmpty() }
            shown[(if (option.isAutoGenerated) "auto " else "") + option.label] =
                main { manager.currentCues.value }.firstOrNull()?.text?.toString()?.take(60)
        }
        main { manager.selectSubtitle(null) }
        return buildJsonObject {
            put("verdict", verdict(picks.isNotEmpty() && shown.values.all { !it.isNullOrBlank() }))
            put("id", CAPTIONED_VIDEO_ID)
            put("offered", options.size)
            shown.forEach { (label, cue) -> put("cue $label", cue) }
        }
    }

    /** SponsorBlock on: playback reaching a sponsor segment skips to its end. */
    private var captionsUnplayable: JsonObject? = null

    suspend fun sponsorBlock(): JsonObject {
        captionsUnplayable?.let { return it }
        stage("s10SponsorBlock: segments")
        val segment =
            SponsorBlockRepository().getSegments(CAPTIONED_VIDEO_ID).firstOrNull { it.category == "sponsor" && it.startTime > 5f }
                ?: return buildJsonObject {
                    put("verdict", "FAIL")
                    put("error", "no sponsor segment listed for $CAPTIONED_VIDEO_ID")
                }
        val preferences = PlayerPreferences()
        val wasOn = preferences.sponsorBlockEnabled.first()
        main { manager.toggleSponsorBlock(true) }
        main { manager.reloadSponsorSegments(CAPTIONED_VIDEO_ID) }
        val loaded = waitFor(15.seconds) { main { manager.sponsorSegments.value }.isNotEmpty() }
        val loadedSegments = main { manager.sponsorSegments.value }.size
        stage("s10SponsorBlock: seek before")
        val from = ((segment.startTime - 3) * 1000).toLong()
        val end = (segment.endTime * 1000).toLong()
        main { manager.seekTo(from) }
        val seekedAt = TimeSource.Monotonic.markNow()
        val startedBefore = position() < (segment.startTime * 1000).toLong()
        val passed = waitFor(20.seconds) { position() >= end - 500 }
        val tookMs = seekedAt.elapsedNow().inWholeMilliseconds
        val positionAfter = position()
        main { manager.toggleSponsorBlock(wasOn) }
        // Playing through takes (end - from) of real time; a skip gets there with the segment's length to spare.
        val skipped = loaded && startedBefore && passed && tookMs < end - from - (end - (segment.startTime * 1000).toLong()) / 2
        return buildJsonObject {
            put("verdict", verdict(skipped))
            put("segment", "${segment.startTime}-${segment.endTime}")
            put("loadedSegments", loadedSegments)
            put("seekedTo", from)
            put("startedBefore", startedBefore)
            put("passedEndAfterMs", tookMs)
            put("playThroughMs", end - from)
            put("positionAfter", positionAfter)
        }
    }

    /**
     * The debug builds' fake stream wall refuses the walled clients' media past 60 s of the stream: the player swaps in a
     * replacement (or reloads) and plays on, as upstream does when GVS walls a visitor (#921).
     */
    suspend fun recovery(videoId: String): JsonObject {
        stage("s10Recovery: wall up")
        val wall = Path(appFilesDir(), "fake_stream_wall")
        SystemFileSystem.sink(wall).buffered().use { it.writeString("on") }
        try {
            stop()
            val since = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val result = extraction(videoId) ?: return noVideo("s10Recovery", videoId)
            load(videoId, result, startPosition = 50_000)
            val continued = waitFor(90.seconds) { position() > 80_000 && main { manager.isPlaying() } }
            val log = PlayerDiagnostics.entries().filter { it.timestampMs >= since }.map { it.message }
            val walled = log.any { "403" in it }
            val swapped = log.any { "swapped" in it }
            val reloaded = log.any { "reloading at" in it }
            return buildJsonObject {
                // Playing on proves the recovery only when the wall was met and a swap or a reload answered it.
                put("verdict", verdict(continued && walled && (swapped || reloaded)))
                put("client", result.usedClient.clientName)
                put("walled", walled)
                put("swapped", swapped)
                put("reloaded", reloaded)
                put("quality", main { manager.playerState.value.effectiveQuality })
                put("positionAfter", position())
                put("error", main { manager.playerState.value.error })
                put("log", log.filter { "403" in it || "swap" in it || "replacement" in it || "reloading" in it }.take(6).joinToString(" | "))
            }
        } finally {
            SystemFileSystem.delete(wall, mustExist = false)
        }
    }

    /**
     * A forced SABR session plays at 2x until GVS stops it (about 62 s of media while the PoToken's verdict is pending,
     * S05); the manager closes the proxy session and falls back to direct formats, and playback goes on past it.
     */
    suspend fun sabrFallback(videoId: String): JsonObject {
        stage("s10SabrFallback: forced SABR")
        val forced = InnerTubeVideoStreamExtractor.extract(videoId, forceSabr = true) ?: return noVideo("s10SabrFallback", videoId)
        if (forced.sabrInfo == null) {
            return buildJsonObject {
                put("verdict", "FAIL")
                put("error", "no SABR session from ${forced.usedClient.clientName}")
            }
        }
        stop()
        load(videoId, forced, preferSabr = true)
        main { manager.setPlaybackSpeed(2f) }
        val continued = waitFor(200.seconds) { position() > 90_000 && main { manager.isPlaying() } }
        main { manager.setPlaybackSpeed(1f) }
        val log = PlayerDiagnostics.entries().map { it.message }
        val denied = log.any { "stream denied for $videoId" in it }
        val result =
            buildJsonObject {
                put("verdict", verdict(continued))
                put("client", forced.usedClient.clientName)
                put("cutSeenAndReloaded", denied)
                put("positionAfter", position())
                put("error", main { manager.playerState.value.error })
            }
        stop()
        return result
    }

    /** S06's open item: the Keychain key seals and opens a secret in the signed app (the test runner has no Keychain). */
    fun keystore(): JsonObject {
        val sealed = runCatching { KeystoreSecretBox.seal("flow-gate-secret") }
        val opened = sealed.getOrNull()?.let { KeystoreSecretBox.open(it) }
        // An unsigned build (CI's simulator app) has no Keychain entitlement: -34018, as S06's tests found.
        val noEntitlement = sealed.exceptionOrNull()?.message?.contains("-34018") == true
        return buildJsonObject {
            put("verdict", if (noEntitlement) "BLOCKED" else verdict(opened == "flow-gate-secret"))
            put("sealedPrefix", sealed.getOrNull()?.take(4))
            put("error", sealed.exceptionOrNull()?.message)
        }
    }
}
