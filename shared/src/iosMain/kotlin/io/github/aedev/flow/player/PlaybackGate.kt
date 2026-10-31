@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.engine.AvPlayerEngine
import io.github.aedev.flow.player.engine.PlaybackEngine
import io.github.aedev.flow.player.engine.PlaybackEngines
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.quality.AdaptiveLadder
import io.github.aedev.flow.player.quality.AutoQualityPolicy
import io.github.aedev.flow.player.resolver.MediaSource
import io.github.aedev.flow.player.resolver.PlatformCodecSupport
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.resolver.VideoPlaybackResolver
import io.github.aedev.flow.player.resolver.canMergeAudio
import io.github.aedev.flow.player.resolver.engineFor
import io.github.aedev.flow.player.stream.AudioStreamSelector
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.VideoExtractionResult
import io.github.aedev.flow.utils.Log
import io.github.aedev.flow.utils.MusicPlayerUtils
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.github.aedev.flow.utils.potoken.PoTokenAttestationPolicy
import io.github.aedev.flow.utils.potoken.VisitorIdentityStore
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import io.github.aedev.flow.utils.writeText
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.AVFoundation.AVMediaCharacteristicAudible
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemAccessLogEvent
import platform.AVFoundation.AVPlayerItemErrorLogEvent
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerItemTrack
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.accessLog
import platform.AVFoundation.asset
import platform.AVFoundation.addOutput
import platform.AVFoundation.currentMediaSelection
import platform.AVFoundation.currentTime
import platform.AVFoundation.errorLog
import platform.AVFoundation.mediaType
import platform.AVFoundation.pause
import platform.AVFoundation.presentationSize
import platform.AVFoundation.tracks
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.play
import platform.AVFoundation.selectedMediaOptionInMediaSelectionGroup
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRelease
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.QuartzCore.CACurrentMediaTime
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightRegular
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.UIKit.UIDevice
import platform.VideoToolbox.VTIsHardwareDecodeSupported
import platform.AVKit.AVPictureInPictureController
import platform.Foundation.NSProcessInfoThermalState
import platform.Foundation.lowPowerModeEnabled
import platform.Foundation.thermalState
import platform.QuartzCore.CALayer
import platform.posix.RUSAGE_SELF
import platform.posix.getrusage
import platform.posix.rusage
import platform.posix.uname
import platform.posix.utsname
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val TAG = "PlaybackGate"
private const val GATE_ARGUMENT = "-flowPlaybackGate"

// Big Buck Bunny (Blender Foundation, CC BY 3.0): H.264 to 1080p60, VP9 and AV1 to 2160p60.
private const val DEFAULT_VIDEO_ID = "aqz-KE-bpKQ"

// Queen, Bohemian Rhapsody, the song when the YouTube Music search does not answer.
private const val FALLBACK_SONG_ID = "lZUgQPTCTlo"
private const val REQUIRED_ADVANCE_SECONDS = 5.0

// What the gate checks, in order; music, video, vp9 and av1 must all pass for a PASS.
private val ITEMS = listOf("cipher", "poToken", "music", "video", "vp9", "av1")

// The bundled engine's measurement window, after its clock has started.
private const val WINDOW_SECONDS = 20

// The engine drops a frame decoded later than this past the clock, so a shown frame is at most this far from the audio.
private const val LATE_FRAME_BOUND_MS = 40.0

// A seek lands on the first frame at or after its target, so within one frame after it; audio alone within one
// 20 ms Opus packet either side.
private fun seekLandingBoundMs(fps: Int): Long = if (fps > 0) 1000L / fps + 1 else 21L

// A rung is sustained when this few frames were dropped and the clock kept real time, with no gap in the sound.
private const val SUSTAINED_DROPPED_SHARE = 0.01
private const val SUSTAINED_REALTIME = 0.98

/**
 * The S04 go/no-go gate, run inside the app because AVPlayer and WKWebView need an app process. It plays a
 * real video and a real YouTube Music song through the ported chain (client ladder, stream bridge, loopback
 * HLS) in AVPlayer, checks the cipher in JavaScriptCore and a PoToken mint in WKWebView, shows each verdict
 * over the video, and writes the verdict and every observation to `Documents/playback-gate.json`.
 *
 * S04b adds the bundled engine: the song as upstream picks its format (Opus), and the video's VP9 rungs (with
 * Opus) and AV1 rungs (with upstream's audio choice) from the top down, each measured over [WINDOW_SECONDS]: frames
 * shown and dropped, how far behind the clock a shown frame was, gaps in the sound, CPU and thermal state, a
 * seek's landing, and Picture in Picture once. 2160p and 1440p are always tried; below them it stops at the first
 * rung the phone sustains. The S04 video stays on AVPlayer with H.264, as before.
 *
 * Swift calls it only in Debug builds, where it runs with the launch argument `-flowPlaybackGate` (CI), and
 * in the `PLAYBACK_GATE` build CI makes for a phone, where [always] runs it on every launch.
 */
fun startPlaybackGateIfRequested(always: Boolean) {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    if (!always && GATE_ARGUMENT !in arguments) return
    val videoId = arguments.getOrNull(arguments.indexOf("-gateVideo") + 1).takeIf { "-gateVideo" in arguments } ?: DEFAULT_VIDEO_ID
    val device = "${UIDevice.currentDevice.model} iOS ${UIDevice.currentDevice.systemVersion}"
    // Off the main thread, as the player's own extraction runs: only the video layer goes back to it.
    MainScope().launch(Dispatchers.Default) { PlaybackGate.run(videoId, device) }
}

/**
 * Why the ladder found nothing to play, when that is this network's answer and not a fault in a layer the port
 * owns: every client refused the viewer (a bot wall, or a status such as LOGIN_REQUIRED or UNPLAYABLE), never
 * answered, was not asked, or is WEB, whose answer is SABR-only (its formats carry neither a URL nor a cipher;
 * measured 2026-10-31: 0 of 22 and 0 of 40) and plays from S05. Null when any client answered with formats the
 * port failed to make playable (a ciphered client such as MWEB, TV_TIZEN's solve, an n-transform, an
 * exception): those are faults. [report] is the ladder's own "ALL clients failed" line.
 */
internal fun networkRefusal(
    id: String,
    report: String?,
): String? {
    val reasons = report?.substringAfter("ALL clients failed $id: ", missingDelimiterValue = "")?.takeIf { it.isNotEmpty() }?.split(" | ") ?: return null
    val outcomes =
        reasons.map { reason ->
            when {
                "BOT_WALL" in reason -> "bot wall"
                "status=" in reason -> "status " + reason.substringAfter("status=").substringBefore(',')
                reason.startsWith("WEB: SABR resolved but no format survived") -> "web client SABR-only (S05)"
                NO_STREAM_OFFERED.containsMatchIn(reason) -> "no stream offered"
                else -> return null
            }
        }
    return outcomes.groupingBy { it }.eachCount().entries.joinToString { "${it.key} x${it.value}" }
}

// The process's CPU time, user and system, over all threads: divided by wall time, the share of one core used.
private fun cpuSeconds(): Double =
    memScoped {
        val usage = alloc<rusage>()
        getrusage(RUSAGE_SELF, usage.ptr)
        usage.ru_utime.tv_sec + usage.ru_utime.tv_usec / 1e6 + usage.ru_stime.tv_sec + usage.ru_stime.tv_usec / 1e6
    }

// "nominal", "fair", "serious" or "critical", as ProcessInfo.ThermalState names them.
private fun thermalName(state: NSProcessInfoThermalState): String = state.name.removePrefix("NSProcessInfoThermalState").lowercase()

private fun machine(): String =
    memScoped {
        val name = alloc<utsname>()
        uname(name.ptr)
        name.machine.toKString()
    }

// Reasons in which the client never answered, was not asked, or answered without a stream to make playable.
private val NO_STREAM_OFFERED = Regex("timeout or null response|not asked|no visitorData|PoToken unavailable|demoted|no adaptive formats|live but no hls")
private object PlaybackGate {
    // The gate writes its result before this, whatever a step is doing; CI waits a little longer.
    private val DEADLINE = 22.minutes

    private val videoViews = mutableListOf<UIView>()
    private var extraction: VideoExtractionResult? = null
    private val lock = SynchronizedObject()
    private val results = linkedMapOf<String, JsonElement>()
    private var running = "start"
    private var finished = false

    suspend fun run(
        videoId: String,
        device: String,
    ) {
        Log.w(TAG, "Playback gate started for $videoId")
        // Sound even with the phone's silent switch on, so the gate is heard as well as seen.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        // FlowApplication's start-up wiring (S13): the visitor identity persists, and a visitor walled on every
        // client at /player is re-rolled once.
        val identityStore = VisitorIdentityStore()
        YouTube.onVisitorDataChanged = identityStore::save
        WebPoTokenSession.bindIdentityStore(identityStore)
        identityStore.restore()?.let { YouTube.visitorData = it }
        record("device", JsonPrimitive(device))
        // The model identifier (iPhone15,4 and so on) sets what 2160p can be asked of the phone.
        record(
            "hardware",
            buildJsonObject {
                put("model", machine())
                put("cores", NSProcessInfo.processInfo.activeProcessorCount.toLong())
                put("lowPowerMode", NSProcessInfo.processInfo.lowPowerModeEnabled)
                put("thermalState", thermalName(NSProcessInfo.processInfo.thermalState))
            },
        )
        record("codecs", codecEvidence())
        val watchdog =
            CoroutineScope(Dispatchers.Default).launch {
                delay(DEADLINE)
                finish(deadlineReached = true)
            }
        step("cipher", 60.seconds) { cipher() }
        step("poToken", 100.seconds) { poToken(videoId) }
        step("music", 150.seconds) { music() }
        step("video", 150.seconds) { video(videoId) }
        step("vp9", 360.seconds) { codecLadder(videoId, "vp9") }
        step("av1", 360.seconds) { codecLadder(videoId, "av1") }
        watchdog.cancel()
        finish(deadlineReached = false)
    }

    /** Names the layer the gate is in, in the result file, so a crash or a hang there is attributed to it. */
    private fun stage(name: String) {
        synchronized(lock) { running = name }
        write("RUNNING")
    }

    private fun currentStage() = synchronized(lock) { running }

    private fun record(
        key: String,
        value: JsonElement,
    ) = synchronized(lock) { results[key] = value }

    private suspend fun step(
        name: String,
        timeout: Duration,
        block: suspend () -> JsonObject,
    ) {
        stage(name)
        val result =
            withTimeoutOrNull(timeout) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    buildJsonObject {
                        put("verdict", "FAIL")
                        put("layer", currentStage())
                        put("error", "${e::class.simpleName}: ${e.message}")
                    }
                }
            } ?: buildJsonObject {
                put("verdict", "FAIL")
                put("layer", currentStage())
                put("error", "timed out after $timeout")
            }
        record(name, result)
        write("RUNNING")
    }

    private fun finish(deadlineReached: Boolean) {
        val verdict =
            synchronized(lock) {
                if (finished) return
                finished = true
                if (deadlineReached) {
                    ITEMS.filter { it !in results }.forEach { missing ->
                        results[missing] =
                            buildJsonObject {
                                put("verdict", "FAIL")
                                put("layer", running)
                                put("error", "the gate's $DEADLINE deadline passed in $running")
                            }
                    }
                }
                // A fault in a layer the port owns fails the gate; a network that offered nothing playable blocks it.
                fun verdictOf(name: String) = (results[name] as? JsonObject)?.get("verdict")?.toString()?.trim('"')
                val items = ITEMS.map(::verdictOf)
                when {
                    "FAIL" in items || null in items -> "FAIL"
                    listOf("music", "video", "vp9", "av1").all { verdictOf(it) == "PASS" } -> "PASS"
                    else -> "BLOCKED"
                }
            }
        write(verdict)
        Log.w(TAG, "FLOW PLAYBACK GATE: $verdict")
    }

    // Written to a temporary file and moved, so CI never reads half a file.
    private fun write(verdict: String) {
        val json =
            synchronized(lock) {
                buildJsonObject {
                    results.forEach { (key, value) -> put(key, value) }
                    put("verdict", verdict)
                    put("running", running)
                    put("diagnostics", buildJsonArray { PlayerDiagnostics.entries().takeLast(80).forEach { add(JsonPrimitive(it.toString())) } })
                }.toString()
            }
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        val partial = Path(documents, "playback-gate.json.partial")
        partial.writeText(json)
        SystemFileSystem.atomicMove(partial, Path(documents, "playback-gate.json"))
        showVerdicts(verdict)
    }

    private var overlay: UILabel? = null

    // Each item's verdict over the video, for someone watching the phone rather than reading CI.
    private fun showVerdicts(verdict: String) {
        val text =
            synchronized(lock) {
                buildString {
                    appendLine("Flow playback gate: $verdict")
                    if (verdict == "RUNNING") appendLine("now: $running")
                    ITEMS.forEach { name ->
                        val item = results[name] as? JsonObject ?: return@forEach
                        val details =
                            listOf("layer", "client", "engine", "codec", "presentationSize", "secondsAdvanced", "highestSustained", "block", "error")
                                .mapNotNull { key -> item[key]?.toString()?.trim('"')?.takeIf { it != "null" }?.let { "$key=${it.take(80)}" } }
                        appendLine("$name: ${item["verdict"]?.toString()?.trim('"')} ${details.joinToString(" ")}")
                    }
                    append("Result: Files > On My iPhone > Flow > playback-gate.json")
                }
            }
        dispatch_async(dispatch_get_main_queue()) {
            @Suppress("DEPRECATION")
            val window = UIApplication.sharedApplication.keyWindow ?: return@dispatch_async
            val label =
                overlay ?: UILabel().also { label ->
                    label.numberOfLines = 0
                    label.font = UIFont.monospacedSystemFontOfSize(12.0, UIFontWeightRegular)
                    label.textColor = UIColor.whiteColor
                    label.backgroundColor = UIColor.blackColor.colorWithAlphaComponent(0.7)
                    window.addSubview(label)
                    overlay = label
                }
            label.text = text
            val width = window.bounds.useContents { size.width } - 24.0
            val height = label.sizeThatFits(CGSizeMake(width, 10_000.0)).useContents { this.height }
            label.setFrame(CGRectMake(12.0, 60.0, width, height))
            window.bringSubviewToFront(label)
        }
    }
    // What AVFoundation and VideoToolbox say they can open here: the evidence behind the VP9 decision.
    private fun codecEvidence() =
        buildJsonObject {
            put("hardwareH264", VTIsHardwareDecodeSupported(0x61766331u))
            put("hardwareHevc", VTIsHardwareDecodeSupported(0x68766331u))
            put("hardwareVp9", VTIsHardwareDecodeSupported(0x76703039u))
            put("hardwareAv1", VTIsHardwareDecodeSupported(0x61763031u))
            put("platformCodecSupportAv1", PlatformCodecSupport.av1)
            listOf(
                "video/mp4; codecs=\"avc1.640028\"",
                "video/mp4; codecs=\"av01.0.08M.08\"",
                "video/mp4; codecs=\"vp09.00.40.08\"",
                "video/webm; codecs=\"vp9\"",
                "audio/mp4; codecs=\"mp4a.40.2\"",
                "audio/webm; codecs=\"opus\"",
            ).forEach { put("playable $it", AVURLAsset.isPlayableExtendedMIMEType(it)) }
        }

    private suspend fun cipher(): JsonObject {
        stage("cipher: player script download")
        val sts = CipherDeobfuscator.ensureSignatureTimestamp()
        stage("cipher: JavaScriptCore")
        val sampleN = "KdrqFlzJXl9EcCwlmEy"
        val transformed = CipherDeobfuscator.transformNParamInUrl("https://rr1---sn.googlevideo.com/videoplayback?itag=18&n=$sampleN")
        val n = Regex("[?&]n=([^&]+)").find(transformed)?.groupValues?.get(1)
        val signature =
            CipherDeobfuscator.deobfuscateSignature(
                "AOq0QJ8wRgIhAKhdt2X3VbN3nk_dS3K1yh0E2D9bY6dQjR0j8XyAq4uiAiEA2n4K3p2s0mZq4f5p0wq5yY8hF0d4yQ6kq1xT6bU0aGw=",
            )
        val ok = sts != null && n != null && n != sampleN && signature != null
        return buildJsonObject {
            put("verdict", if (ok) "PASS" else "FAIL")
            put("engine", "JavaScriptCore")
            put("playerHash", CipherDeobfuscator.playerHash())
            put("signatureTimestamp", sts)
            put("nTransformed", n != null && n != sampleN)
            put("signatureSolved", signature != null)
            put("unparseablePlayerHash", CipherDeobfuscator.unparseablePlayerHash)
        }
    }

    private suspend fun poToken(videoId: String): JsonObject {
        stage("poToken: visitor data")
        val visitorData = WebPoTokenSession.sessionVisitorData()
        stage("poToken: WKWebView")
        val result = withTimeoutOrNull(90.seconds) { WebPoTokenSession.mint(videoId) }
        return buildJsonObject {
            put("verdict", if (result != null) "PASS" else "FAIL")
            put("engine", "WKWebView")
            put("visitorData", visitorData != null)
            put("playerPotLength", result?.playerRequestPoToken?.length)
            put("streamingPotLength", result?.streamingDataPoToken?.length)
            put("streamingPotLowTrust", result?.streamingDataPoToken?.let(PoTokenAttestationPolicy::isLowTrust))
        }
    }

    private suspend fun music(): JsonObject {
        stage("music: YouTube Music search")
        val songId =
            YouTube
                .search("Queen Bohemian Rhapsody", YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                ?.firstOrNull()
                ?.id ?: FALLBACK_SONG_ID
        stage("music: player response")
        val playback =
            MusicPlayerUtils.playerResponseForPlayback(songId).getOrElse { error ->
                return blockedOrFailed("music", songId, error.message)
            }
        stage("music: HLS proxy")
        val stream = InnerTubeStreamBridge.convertAudioFormats(listOf(playback.format)).single()
        val source =
            VideoPlaybackResolver(songId).resolve(
                videoStreams = emptyList(),
                audioStream = stream,
                dashManifestUrl = null,
                hlsUrl = null,
                durationSeconds = playback.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0,
            ) ?: return failed("music", songId, "nothing resolved")
        val observed =
            if (source.engine == PlaybackEngineKind.BUNDLED) {
                stage("music: bundled engine")
                measure(source, fps = 0, pictureInPicture = false)
            } else {
                stage("music: AVPlayer")
                observe(source, expectVideo = false)
            }
        return buildJsonObject {
            put("id", songId)
            put("engine", source.engine.name)
            put("title", playback.videoDetails?.title)
            put("client", playback.usedClient.clientName)
            put("itag", playback.format.itag)
            put("mimeType", playback.format.mimeType)
            put("codec", VideoCodecUtils.codecStringFromMimeType(playback.format.mimeType))
            observed.forEach { (key, value) -> put(key, value) }
        }
    }

    private suspend fun video(videoId: String): JsonObject {
        stage("video: player response")
        val result = extraction(videoId) ?: return blockedOrFailed("video", videoId, "every client failed")
        val all = InnerTubeStreamBridge.convertVideoFormats(result.videoFormats)
        // S04's check, kept as it was: AVPlayer's own formats (H.264 to 1080p on a phone without hardware AV1).
        val videos = all.filter { PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.AVPLAYER }
        val audio =
            AudioStreamSelector.selectPreferredAudioStream(
                InnerTubeStreamBridge.convertAudioFormats(result.audioFormats),
                preferredAudioLanguage = "original",
                compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it) },
            )
        // QualityManager.selectSmartInitialQuality before any bandwidth is measured, then Auto's ladder.
        val anchor =
            AutoQualityPolicy.initialPick(videos, PlayerConfig.INITIAL_BANDWIDTH_ESTIMATE, null)
                ?: return failed("video", videoId, "no stream AVPlayer can decode among ${all.map { it.codec }}")
        val ladder = AdaptiveLadder.rungs(anchor, videos)
        stage("video: HLS proxy")
        val source =
            VideoPlaybackResolver(videoId).resolve(
                videoStreams = ladder.ifEmpty { listOf(anchor) },
                audioStream = audio,
                dashManifestUrl = null,
                hlsUrl = null,
                durationSeconds = result.playerResponse.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0,
                asAdaptiveLadder = ladder.isNotEmpty(),
            ) ?: return failed("video", videoId, "nothing resolved")
        stage("video: AVPlayer")
        val observed = observe(source, expectVideo = true)
        return buildJsonObject {
            put("id", videoId)
            put("client", result.usedClient.clientName)
            put("offered", all.joinToString { "${VideoCodecUtils.qualityHeightFromStream(it)}p ${VideoCodecUtils.codecKeyFromStream(it)}" })
            put("rungs", ladder.ifEmpty { listOf(anchor) }.joinToString { "${VideoCodecUtils.qualityHeightFromStream(it)}p ${it.codec}" })
            put("codec", anchor.codec)
            put("audioItag", audio?.itag)
            put("engine", source.engine.name)
            observed.forEach { (key, value) -> put(key, value) }
        }
    }

    private suspend fun extraction(videoId: String): VideoExtractionResult? =
        extraction ?: InnerTubeVideoStreamExtractor.extract(videoId)?.also { extraction = it }

    private fun failed(
        what: String,
        id: String,
        why: String?,
    ) = buildJsonObject {
        put("verdict", "FAIL")
        put("layer", currentStage())
        put("id", id)
        put("error", "$what: $why")
    }

    // BLOCKED when YouTube refused the viewer, as the ladder reports it, or when nothing this network was offered is playable before S05.
    private fun blockedOrFailed(
        what: String,
        id: String,
        why: String?,
    ): JsonObject {
        val block = InnerTubeVideoStreamExtractor.blockOf(id)?.name ?: networkRefusal(id) ?: return failed(what, id, why)
        return buildJsonObject {
            put("verdict", "BLOCKED")
            put("layer", currentStage())
            put("id", id)
            put("block", block)
            put("error", "$what: $why")
        }
    }

    private fun networkRefusal(id: String): String? =
        networkRefusal(id, PlayerDiagnostics.entries().lastOrNull { "ALL clients failed $id: " in it.message }?.message)

    /** Plays [source] until it has advanced [REQUIRED_ADVANCE_SECONDS], and reports what AVPlayer saw. */
    private suspend fun observe(
        source: MediaSource,
        expectVideo: Boolean,
    ): JsonObject {
        val engine = AvPlayerEngine()
        engine.prepare(source)
        val item = engine.item!!
        val asset = item.asset
        val output = AVPlayerItemVideoOutput(pixelBufferAttributes = null)
        item.addOutput(output)
        if (expectVideo) withContext(Dispatchers.Main) { showOnScreen(engine.layer) }
        engine.play()

        val started = TimeSource.Monotonic.markNow()
        var firstTime: Double? = null
        var lastTime = 0.0
        var frames = 0
        var frameSize: String? = null
        var hasVideo = false
        var hasAudio = false
        while (started.elapsedNow() < 90.seconds) {
            delay(250)
            if (item.status == AVPlayerItemStatusFailed) break
            if (item.status != AVPlayerItemStatusReadyToPlay) continue
            item.tracks.forEach { track ->
                when ((track as? AVPlayerItemTrack)?.assetTrack?.mediaType) {
                    AVMediaTypeVideo -> hasVideo = true
                    AVMediaTypeAudio -> hasAudio = true
                }
            }
            val time = CMTimeGetSeconds(item.currentTime())
            if (firstTime == null && time > 0) firstTime = time
            lastTime = time
            val itemTime = output.itemTimeForHostTime(CACurrentMediaTime())
            if (output.hasNewPixelBufferForItemTime(itemTime)) {
                output.copyPixelBufferForItemTime(itemTime, null)?.let { buffer ->
                    frames++
                    frameSize = "${CVPixelBufferGetWidth(buffer)}x${CVPixelBufferGetHeight(buffer)}"
                    CVPixelBufferRelease(buffer)
                }
            }
            val advanced = lastTime - (firstTime ?: lastTime)
            if (advanced >= REQUIRED_ADVANCE_SECONDS && (!expectVideo || frames > 0)) break
        }

        // Only a master with an EXT-X-MEDIA audio group has an audible selection group; an audio-only master has
        // none, so it reports false here and shows its audio through audioTrack and the advancing clock instead.
        val audibleGroup = asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicAudible)
        val audibleSelected = audibleGroup?.let { item.currentMediaSelection.selectedMediaOptionInMediaSelectionGroup(it) } != null
        val advanced = lastTime - (firstTime ?: lastTime)
        val ready = item.status == AVPlayerItemStatusReadyToPlay
        val audioPresent = hasAudio || audibleSelected
        val pass = ready && audioPresent && advanced >= REQUIRED_ADVANCE_SECONDS && (!expectVideo || (hasVideo && frames > 0))
        val access = item.accessLog()?.events?.lastOrNull() as? AVPlayerItemAccessLogEvent
        val errors = item.errorLog()?.events?.mapNotNull { it as? AVPlayerItemErrorLogEvent }.orEmpty()
        // Stopped before the next check: the bundled engine's measurements must not share the phone with it.
        engine.close()
        return buildJsonObject {
            put("verdict", if (pass) "PASS" else "FAIL")
            put("uri", source.uri)
            put("status", if (ready) "readyToPlay" else if (item.status == AVPlayerItemStatusFailed) "failed" else "unknown")
            put("error", item.error?.localizedDescription)
            put("videoTrack", hasVideo)
            put("audioTrack", hasAudio)
            put("audibleOptionSelected", audibleSelected)
            put("secondsAdvanced", advanced)
            put("decodedFrames", frames)
            put("frameSize", frameSize)
            put("presentationSize", item.presentationSize.useContents { "${width.toInt()}x${height.toInt()}" })
            put("indicatedBitrate", access?.indicatedBitrate)
            put("observedBitrate", access?.observedBitrate)
            put("errorLog", buildJsonArray { errors.takeLast(5).forEach { add(JsonPrimitive("${it.errorStatusCode} ${it.errorComment} ${it.URI}")) } })
            // S04's run logged -12318 once, before fcc0d6c stated each rendition's measured peak rate as BANDWIDTH.
            put("segmentExceedsBandwidth12318", errors.any { it.errorStatusCode == -12318L })
        }
    }

    // The video on screen under the verdicts, so CI's screenshot and the person holding the phone see it.
    private fun showOnScreen(layer: CALayer) {
        @Suppress("DEPRECATION")
        val window = UIApplication.sharedApplication.keyWindow ?: return
        videoViews.forEach { it.removeFromSuperview() }
        videoViews.clear()
        val view = UIView(frame = window.bounds)
        layer.frame = window.bounds
        view.layer.addSublayer(layer)
        window.addSubview(view)
        overlay?.let { window.bringSubviewToFront(it) }
        videoViews += view
    }

    /**
     * The video's [codecKey] rungs in the bundled engine, from the top: 2160p and 1440p always, then down to the first
     * rung the phone sustains. VP9 plays beside Opus, YouTube's own pairing; AV1 beside upstream's own audio choice.
     */
    private suspend fun codecLadder(
        videoId: String,
        codecKey: String,
    ): JsonObject {
        stage("$codecKey: player response")
        val result = extraction(videoId) ?: return blockedOrFailed(codecKey, videoId, "every client failed")
        val all = InnerTubeStreamBridge.convertVideoFormats(result.videoFormats)
        val rungs =
            all
                .filter { VideoCodecUtils.codecKeyFromStream(it) == codecKey && !AdaptiveLadder.isHighBitDepth(it) && PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.BUNDLED }
                .groupBy(VideoCodecUtils::qualityHeightFromStream)
                .map { (_, sameHeight) -> sameHeight.maxBy { it.bitrate } }
                .sortedByDescending(VideoCodecUtils::qualityHeightFromStream)
        if (rungs.isEmpty()) {
            return buildJsonObject {
                put("verdict", "BLOCKED")
                put("layer", currentStage())
                put("block", "no $codecKey stream for the bundled engine among ${all.size} offered")
            }
        }
        val audios = InnerTubeStreamBridge.convertAudioFormats(result.audioFormats)
        val audio =
            AudioStreamSelector.selectPreferredAudioStream(
                if (codecKey == "vp9") audios.filter { it.codec == "opus" }.ifEmpty { audios } else audios,
                preferredAudioLanguage = "original",
                compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it, PlaybackEngineKind.BUNDLED) },
            )
        val duration = result.playerResponse.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0
        val measured = mutableListOf<JsonObject>()
        var highestSustained: Int? = null
        for (stream in rungs) {
            val height = VideoCodecUtils.qualityHeightFromStream(stream)
            if ((height < 1440 && highestSustained != null) || measured.size >= 5) break
            stage("$codecKey: ${height}p")
            val source = VideoPlaybackResolver(videoId).resolve(listOf(stream), audio, null, null, duration)
            val rung =
                if (source == null || source.engine != PlaybackEngineKind.BUNDLED) {
                    failed(codecKey, videoId, "${height}p resolved to ${source?.engine ?: "nothing"}")
                } else {
                    measure(source, fps = VideoCodecUtils.frameRateFromStream(stream), pictureInPicture = codecKey == "vp9" && measured.isEmpty())
                }
            measured +=
                buildJsonObject {
                    put("height", height)
                    put("codec", stream.codec)
                    put("fps", VideoCodecUtils.frameRateFromStream(stream))
                    put("bitrate", stream.bitrate)
                    rung.forEach { (key, value) -> put(key, value) }
                }
            if (highestSustained == null && rung["sustained"]?.toString() == "true") highestSustained = height
        }
        val allPlayed = measured.all { it["verdict"]?.toString()?.trim('"') == "PASS" }
        return buildJsonObject {
            put("verdict", if (allPlayed && highestSustained != null) "PASS" else "FAIL")
            put("client", result.usedClient.clientName)
            put("engine", PlaybackEngineKind.BUNDLED.name)
            put("codec", codecKey)
            put("highestSustained", highestSustained?.let { "${it}p" })
            put("audioItag", audio?.itag)
            put("audioCodec", audio?.codec)
            put("offered", rungs.joinToString { "${VideoCodecUtils.qualityHeightFromStream(it)}p${VideoCodecUtils.frameRateFromStream(it)} ${it.codec}" })
            put("rungs", JsonArray(measured))
        }
    }

    /**
     * Plays [source] in the bundled engine for [WINDOW_SECONDS] once its clock runs, then seeks, and reports what it
     * measured. [fps] is the video's frame rate, 0 for audio alone.
     */
    private suspend fun measure(
        source: MediaSource,
        fps: Int,
        pictureInPicture: Boolean,
    ): JsonObject {
        // Made on the main thread, where its layers belong.
        val engine =
            withContext(Dispatchers.Main) {
                PlaybackEngines.create(PlaybackEngineKind.BUNDLED).also { if (fps > 0) showOnScreen(it.layer) }
            }
        engine.prepare(source)
        engine.play()
        try {
            val opening = TimeSource.Monotonic.markNow()
            while (opening.elapsedNow() < 40.seconds && engine.error() == null && !(engine.isPlaying() && engine.currentPositionMs() > 0)) delay(100)
            val startupMs = opening.elapsedNow().inWholeMilliseconds
            val before = engine.stats()
            val cpuBefore = cpuSeconds()
            val positionBefore = engine.currentPositionMs()
            val thermalStart = NSProcessInfo.processInfo.thermalState
            var thermalMax = thermalStart
            val window = TimeSource.Monotonic.markNow()
            while (window.elapsedNow() < WINDOW_SECONDS.seconds && engine.error() == null) {
                delay(500)
                thermalMax = maxOf(thermalMax, NSProcessInfo.processInfo.thermalState)
            }
            val wallSeconds = window.elapsedNow().inWholeMilliseconds / 1000.0
            val cpuPercent = (cpuSeconds() - cpuBefore) / wallSeconds * 100
            val advancedSeconds = (engine.currentPositionMs() - positionBefore) / 1000.0
            val after = engine.stats()
            val rendered = after.framesRendered - before.framesRendered
            val dropped = after.framesDropped - before.framesDropped
            val underruns = after.audioUnderruns - before.audioUnderruns
            val droppedShare = if (rendered + dropped > 0) dropped.toDouble() / (rendered + dropped) else 0.0
            val realtime = advancedSeconds / wallSeconds
            val seek = seekCheck(engine, fps)
            val pip = if (pictureInPicture) pictureInPictureCheck(engine) else null
            val played = engine.error() == null && advancedSeconds >= REQUIRED_ADVANCE_SECONDS && after.audioRendering && (fps == 0 || rendered > 0)
            val inSync = after.maxVideoLateMs <= LATE_FRAME_BOUND_MS
            val sustained = played && droppedShare <= SUSTAINED_DROPPED_SHARE && realtime >= SUSTAINED_REALTIME && underruns == 0
            return buildJsonObject {
                put("verdict", if (played && inSync && seek["landed"]?.toString() == "true") "PASS" else "FAIL")
                put("uri", source.uri)
                put("sustained", sustained)
                put("error", engine.error())
                put("videoDecoder", after.videoDecoder)
                put("audioDecoder", after.audioDecoder)
                put("videoSize", after.videoSize)
                put("startupMs", startupMs)
                put("windowSeconds", wallSeconds)
                put("secondsAdvanced", advancedSeconds)
                put("realtime", realtime)
                put("framesRendered", rendered)
                put("framesDropped", dropped)
                put("droppedPercent", droppedShare * 100)
                put("framesPerSecondShown", rendered / wallSeconds)
                put("maxVideoLateMs", after.maxVideoLateMs)
                put("lateFrameBoundMs", LATE_FRAME_BOUND_MS)
                put("audioRendering", after.audioRendering)
                put("audioUnderruns", underruns)
                put("cpuPercentOfOneCore", cpuPercent)
                put("thermalStart", thermalName(thermalStart))
                put("thermalMax", thermalName(thermalMax))
                put("thermalEnd", thermalName(NSProcessInfo.processInfo.thermalState))
                put("seek", seek)
                pip?.let { put("pictureInPicture", it) }
            }
        } finally {
            engine.close()
        }
    }

    /** Seeks 30 s on (or to 10 s before the end) and reports where the first frame after it landed. */
    private suspend fun seekCheck(
        engine: PlaybackEngine,
        fps: Int,
    ): JsonObject {
        val target = minOf(engine.currentPositionMs() + 30_000, engine.durationMs() - 10_000).coerceAtLeast(0)
        val started = TimeSource.Monotonic.markNow()
        engine.seekTo(target)
        while (started.elapsedNow() < 30.seconds && engine.error() == null && !(engine.stats().seekLandedMs >= 0 && engine.currentPositionMs() > target)) delay(50)
        val landed = engine.stats().seekLandedMs
        val bound = seekLandingBoundMs(fps)
        val landing = landed - target
        return buildJsonObject {
            put("targetMs", target)
            put("landedMs", landed)
            put("landingMs", landing)
            put("boundMs", bound)
            put("resumedAfterMs", started.elapsedNow().inWholeMilliseconds)
            put("landed", landed >= 0 && landing <= bound && landing >= -bound)
        }
    }

    // Starting Picture in Picture without a tap may be refused; the gate records what iOS allowed.
    private suspend fun pictureInPictureCheck(engine: PlaybackEngine): JsonObject =
        withContext(Dispatchers.Main) {
            val controller =
                engine.pictureInPictureController()
                    ?: return@withContext buildJsonObject { put("supported", AVPictureInPictureController.isPictureInPictureSupported()) }
            val asked = TimeSource.Monotonic.markNow()
            while (!controller.pictureInPicturePossible && asked.elapsedNow() < 5.seconds) delay(100)
            val possible = controller.pictureInPicturePossible
            if (possible) controller.startPictureInPicture()
            delay(3_000)
            val active = controller.pictureInPictureActive
            if (active) controller.stopPictureInPicture()
            delay(1_000)
            buildJsonObject {
                put("supported", AVPictureInPictureController.isPictureInPictureSupported())
                put("possible", possible)
                put("started", active)
            }
        }
}
