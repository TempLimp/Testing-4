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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import io.github.aedev.flow.ui.theme.AppFont
import io.github.aedev.flow.ui.theme.CONDENSED_FAMILY
import io.github.aedev.flow.ui.theme.CondensedWeights
import io.github.aedev.flow.ui.theme.appFontFamily
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.serialization.json.JsonArray
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFontDescriptor
import platform.UIKit.UIFontDescriptorFamilyAttribute
import platform.UIKit.UIFontDescriptorTraitsAttribute
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIFontWeightHeavy
import platform.UIKit.UIFontWeightMedium
import platform.UIKit.UIFontWeightTrait
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIScreen
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.systemBlueColor
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

// What the gate checks, in order. Every item after the cipher and the PoToken must pass for a PASS.
private val ITEMS = listOf("cipher", "poToken", "fonts", "pip", "music", "video", "h264At1080p", "vp9", "av1", "hdrAv1", "longRun")

// "The World in HDR in 4K (ULTRA HD)" (Jacob + Katie Schwarz): offered as 10-bit AV1 HDR (BT.2020, PQ) from 144p to
// 2160p60 through VISIONOS, the client upstream's ladder asks first; no VP9 profile 2 on any client asked (probed
// 2026-11-01), and no 8K rung to waste the run on.
private const val HDR_VIDEO_ID = "tO01J-M3g0U"

// The long run: VP9 2160p60 for this long, measured in windows of this length.
private const val LONG_RUN_WINDOWS = 20
private const val LONG_RUN_WINDOW_SECONDS = 30

// UIKit's weights for the port's condensed FontWeights (AppFont.kt).
private val UI_FONT_WEIGHTS = mapOf(400 to UIFontWeightRegular, 500 to UIFontWeightMedium, 600 to UIFontWeightSemibold, 700 to UIFontWeightBold, 800 to UIFontWeightHeavy)

/** A line in each of the port's condensed weights, through its own Compose font family, for the gate's screenshot. */
@Composable
private fun CondensedFontSample() {
    val family = appFontFamily(AppFont.CONDENSED, null) { null }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 12.dp, vertical = 6.dp)) {
        CondensedWeights.forEach { weight ->
            BasicText("Condensed ${weight.weight}: Flow plays VP9, AV1 and Opus", style = TextStyle(color = Color.White, fontSize = 18.sp, fontFamily = family, fontWeight = weight))
        }
    }
}

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
    // The gate writes its result before this, whatever a step is doing; CI's wait covers it only when every
    // playback step is BLOCKED there, as on GitHub's walled runners.
    private val DEADLINE = 40.minutes

    private val videoViews = mutableListOf<UIView>()
    private val extractions = mutableMapOf<String, VideoExtractionResult>()
    private var fontSample: UIView? = null
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
        // The long run takes ten minutes untouched: the screen must not lock, and the battery is read as it goes.
        withContext(Dispatchers.Main) {
            UIApplication.sharedApplication.idleTimerDisabled = true
            UIDevice.currentDevice.batteryMonitoringEnabled = true
        }
        val watchdog =
            CoroutineScope(Dispatchers.Default).launch {
                delay(DEADLINE)
                finish(deadlineReached = true)
            }
        step("cipher", 60.seconds) { cipher() }
        step("poToken", 100.seconds) { poToken(videoId) }
        step("fonts", 30.seconds) { fonts() }
        step("pip", 7.minutes) { pictureInPictureByTap(videoId) }
        step("music", 150.seconds) { music() }
        step("video", 150.seconds) { video(videoId) }
        step("h264At1080p", 150.seconds) { h264At1080p(videoId) }
        step("vp9", 360.seconds) { codecLadder(videoId, "vp9") }
        step("av1", 360.seconds) { codecLadder(videoId, "av1") }
        step("hdrAv1", 8.minutes) { codecLadder(HDR_VIDEO_ID, "av1", deep = true) }
        step("longRun", 13.minutes) { longRun(videoId) }
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
                    ITEMS.drop(2).all { verdictOf(it) == "PASS" } -> "PASS"
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
                            listOf("layer", "engine", "codec", "presentationSize", "highestSustained", "sustainedWholeTime", "active", "block", "error")
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
            // Above the font sample along the bottom; the file has everything the label has no room for.
            val room = window.bounds.useContents { size.height } - 60.0 - 210.0
            val height = minOf(label.sizeThatFits(CGSizeMake(width, 10_000.0)).useContents { this.height }, room)
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
        extractions[videoId] ?: InnerTubeVideoStreamExtractor.extract(videoId)?.also { extractions[videoId] = it }

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

    /** Plays [source] until it has advanced [requiredSeconds], and reports what AVPlayer saw. */
    private suspend fun observe(
        source: MediaSource,
        expectVideo: Boolean,
        requiredSeconds: Double = REQUIRED_ADVANCE_SECONDS,
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
            if (advanced >= requiredSeconds && (!expectVideo || frames > 0)) break
        }

        // Only a master with an EXT-X-MEDIA audio group has an audible selection group; an audio-only master has
        // none, so it reports false here and shows its audio through audioTrack and the advancing clock instead.
        val audibleGroup = asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicAudible)
        val audibleSelected = audibleGroup?.let { item.currentMediaSelection.selectedMediaOptionInMediaSelectionGroup(it) } != null
        val advanced = lastTime - (firstTime ?: lastTime)
        val ready = item.status == AVPlayerItemStatusReadyToPlay
        val audioPresent = hasAudio || audibleSelected
        val pass = ready && audioPresent && advanced >= requiredSeconds && (!expectVideo || (hasVideo && frames > 0))
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
        val sample = fontSample
        if (sample != null) sample.superview?.insertSubview(view, belowSubview = sample) else window.addSubview(view)
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
        deep: Boolean = false,
    ): JsonObject {
        stage("$codecKey: player response")
        val result = extraction(videoId) ?: return blockedOrFailed(codecKey, videoId, "every client failed")
        val all = InnerTubeStreamBridge.convertVideoFormats(result.videoFormats)
        val rungs =
            all
                // Upstream keeps 10-bit and HDR on a ladder of their own (AdaptiveLadder.isHighBitDepth).
                .filter { VideoCodecUtils.codecKeyFromStream(it) == codecKey && AdaptiveLadder.isHighBitDepth(it) == deep && PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.BUNDLED }
                .groupBy(VideoCodecUtils::qualityHeightFromStream)
                .map { (_, sameHeight) -> sameHeight.maxBy { it.bitrate } }
                .sortedByDescending(VideoCodecUtils::qualityHeightFromStream)
        if (rungs.isEmpty()) {
            return buildJsonObject {
                put("verdict", "BLOCKED")
                put("layer", currentStage())
                put("block", "no ${if (deep) "10-bit " else ""}$codecKey stream for the bundled engine among ${all.size} offered")
                put("deepFormatsOffered", all.filter(AdaptiveLadder::isHighBitDepth).joinToString { it.codec.orEmpty() })
            }
        }
        val audio =
            if (codecKey == "vp9") {
                opusAudio(result)
            } else {
                AudioStreamSelector.selectPreferredAudioStream(
                    InnerTubeStreamBridge.convertAudioFormats(result.audioFormats),
                    preferredAudioLanguage = "original",
                    compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it, PlaybackEngineKind.BUNDLED) },
                )
            }
        val duration = result.durationSeconds()
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
                    measure(source, fps = VideoCodecUtils.frameRateFromStream(stream), pictureInPicture = codecKey == "vp9" && !deep && measured.isEmpty(), edr = deep)
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
            put("id", videoId)
            put("codec", codecKey)
            put("tenBit", deep)
            put("deepFormatsOffered", all.filter(AdaptiveLadder::isHighBitDepth).joinToString { it.codec.orEmpty() })
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
        edr: Boolean = false,
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
            // How far above SDR white the screen may go, and went, while the picture was up (HDR shows as headroom > 1).
            val edrPotential = if (edr) withContext(Dispatchers.Main) { UIScreen.mainScreen.potentialEDRHeadroom } else null
            var edrCurrentMax = 0.0
            val window = TimeSource.Monotonic.markNow()
            while (window.elapsedNow() < WINDOW_SECONDS.seconds && engine.error() == null) {
                delay(500)
                thermalMax = maxOf(thermalMax, NSProcessInfo.processInfo.thermalState)
                if (edr) edrCurrentMax = maxOf(edrCurrentMax, withContext(Dispatchers.Main) { UIScreen.mainScreen.currentEDRHeadroom })
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
                put("pixelFormat", after.pixelFormat)
                put("colorPrimaries", after.colorPrimaries)
                put("transferFunction", after.transferFunction)
                edrPotential?.let {
                    put("edrHeadroomPotential", it)
                    put("edrHeadroomCurrentMax", edrCurrentMax)
                }
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

    /**
     * The condensed font on a device (PARITY's S02 row): the faces iOS has in Avenir Next Condensed, the face CoreText
     * picks for each weight the port maps, and a line in each drawn through the port's own Compose family, left on
     * screen for the screenshot.
     */
    private suspend fun fonts(): JsonObject =
        withContext(Dispatchers.Main) {
            val faces = UIFont.fontNamesForFamilyName(CONDENSED_FAMILY).map { it.toString() }
            val picked =
                CondensedWeights.associate { weight ->
                    val traits = mapOf<Any?, Any?>(UIFontWeightTrait to UI_FONT_WEIGHTS.getValue(weight.weight))
                    val attributes = mapOf<Any?, Any?>(UIFontDescriptorFamilyAttribute to CONDENSED_FAMILY, UIFontDescriptorTraitsAttribute to traits)
                    weight.weight to UIFont.fontWithDescriptor(UIFontDescriptor.fontDescriptorWithFontAttributes(attributes), size = 18.0).fontName
                }
            showFontSample()
            val distinct = picked.values.all { it.startsWith("AvenirNextCondensed") } && picked.values.toSet().size == CondensedWeights.size
            buildJsonObject {
                put("verdict", if (distinct) "PASS" else "FAIL")
                put("family", CONDENSED_FAMILY)
                put("faces", faces.joinToString())
                picked.forEach { (weight, name) -> put("weight$weight", name) }
            }
        }

    private fun showFontSample() {
        @Suppress("DEPRECATION")
        val window = UIApplication.sharedApplication.keyWindow ?: return
        val root = window.rootViewController ?: return
        val sample = ComposeUIViewController { CondensedFontSample() }
        // A child view controller's view belongs in its parent's view; the videos go under it there (showOnScreen).
        root.addChildViewController(sample)
        val (width, height) = window.bounds.useContents { size.width to size.height }
        sample.view.setFrame(CGRectMake(0.0, height - 200.0, width, 170.0))
        root.view.addSubview(sample.view)
        sample.didMoveToParentViewController(root)
        fontSample = sample.view
    }

    /**
     * Picture in Picture opened the only way iOS allows it, by a tap: the bundled engine plays VP9 at 1080p, a button
     * starts Picture in Picture on its sample-buffer layer, and the gate checks the window came up and the video kept
     * playing in it for 5 s. S11 gives the player its own button.
     */
    private suspend fun pictureInPictureByTap(videoId: String): JsonObject {
        stage("pip: player response")
        val result = extraction(videoId) ?: return blockedOrFailed("pip", videoId, "every client failed")
        val stream =
            InnerTubeStreamBridge
                .convertVideoFormats(result.videoFormats)
                .filter {
                    VideoCodecUtils.codecKeyFromStream(it) == "vp9" && !AdaptiveLadder.isHighBitDepth(it) &&
                        VideoCodecUtils.qualityHeightFromStream(it) <= 1080 && PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.BUNDLED
                }.maxByOrNull(VideoCodecUtils::qualityHeightFromStream)
                ?: return buildJsonObject {
                    put("verdict", "BLOCKED")
                    put("layer", currentStage())
                    put("block", "no VP9 stream for the bundled engine offered")
                }
        val source =
            VideoPlaybackResolver(videoId).resolve(listOf(stream), opusAudio(result), null, null, result.durationSeconds())
                ?: return failed("pip", videoId, "nothing resolved")
        val engine = withContext(Dispatchers.Main) { PlaybackEngines.create(PlaybackEngineKind.BUNDLED).also { showOnScreen(it.layer) } }
        engine.prepare(source)
        engine.play()
        var button: UIView? = null
        try {
            val opening = TimeSource.Monotonic.markNow()
            while (opening.elapsedNow() < 40.seconds && engine.error() == null && !(engine.isPlaying() && engine.currentPositionMs() > 0)) delay(100)
            val controller =
                withContext(Dispatchers.Main) { engine.pictureInPictureController() }
                    ?: return failed("pip", videoId, "no controller; supported=${AVPictureInPictureController.isPictureInPictureSupported()}")
            val tapped = CompletableDeferred<Boolean>()
            button =
                withContext(Dispatchers.Main) {
                    showButton("Start Picture in Picture") {
                        val possible = controller.pictureInPicturePossible
                        controller.startPictureInPicture()
                        tapped.complete(possible)
                    }
                }
            stage("pip: TAP \"Start Picture in Picture\"")
            val possibleAtTap = withTimeoutOrNull(5.minutes) { tapped.await() } ?: return failed("pip", videoId, "nobody tapped within 5 minutes")
            stage("pip: window")
            val asked = TimeSource.Monotonic.markNow()
            while (asked.elapsedNow() < 5.seconds && !withContext(Dispatchers.Main) { controller.pictureInPictureActive }) delay(100)
            val active = withContext(Dispatchers.Main) { controller.pictureInPictureActive }
            val before = engine.stats()
            val positionBefore = engine.currentPositionMs()
            var stayedActive = active
            val inWindow = TimeSource.Monotonic.markNow()
            while (active && inWindow.elapsedNow() < 6.seconds) {
                delay(500)
                stayedActive = stayedActive && withContext(Dispatchers.Main) { controller.pictureInPictureActive }
            }
            val played = (engine.currentPositionMs() - positionBefore) / 1000.0
            val after = engine.stats()
            withContext(Dispatchers.Main) { if (controller.pictureInPictureActive) controller.stopPictureInPicture() }
            delay(1_000)
            return buildJsonObject {
                put("verdict", if (active && stayedActive && played >= 5.0 && engine.error() == null) "PASS" else "FAIL")
                put("engine", PlaybackEngineKind.BUNDLED.name)
                put("videoSize", after.videoSize)
                put("possibleAtTap", possibleAtTap)
                put("active", active)
                put("stayedActive", stayedActive)
                put("secondsPlayedInWindow", played)
                put("framesRenderedInWindow", after.framesRendered - before.framesRendered)
                put("framesDroppedInWindow", after.framesDropped - before.framesDropped)
                put("error", engine.error())
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { button?.removeFromSuperview() }
            engine.close()
        }
    }

    private fun showButton(
        title: String,
        onTap: () -> Unit,
    ): UIView? {
        @Suppress("DEPRECATION")
        val window = UIApplication.sharedApplication.keyWindow ?: return null
        val button = UIButton.buttonWithType(UIButtonTypeSystem, primaryAction = UIAction.actionWithTitle(title, image = null, identifier = null) { _ -> onTap() })
        button.backgroundColor = UIColor.systemBlueColor
        button.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        button.titleLabel?.font = UIFont.boldSystemFontOfSize(22.0)
        button.layer.cornerRadius = 14.0
        val (width, height) = window.bounds.useContents { size.width to size.height }
        button.setFrame(CGRectMake((width - 320.0) / 2, height / 2 - 40.0, 320.0, 80.0))
        window.addSubview(button)
        window.bringSubviewToFront(button)
        return button
    }

    /**
     * AVPlayer held to the 1080p H.264 rung for 30 s, the variant that logged `-12318 Segment exceeds specified
     * bandwidth` in S04 before fcc0d6c stated each rendition's measured peak as BANDWIDTH. A master with that one
     * variant leaves AVPlayer nothing to switch to.
     */
    private suspend fun h264At1080p(videoId: String): JsonObject {
        stage("h264At1080p: player response")
        val result = extraction(videoId) ?: return blockedOrFailed("h264At1080p", videoId, "every client failed")
        val stream =
            InnerTubeStreamBridge
                .convertVideoFormats(result.videoFormats)
                .filter {
                    PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.AVPLAYER && VideoCodecUtils.codecKeyFromStream(it) == "h264" &&
                        VideoCodecUtils.qualityHeightFromStream(it) == 1080
                }.maxByOrNull { it.bitrate }
                ?: return failed("h264At1080p", videoId, "no 1080p H.264 offered")
        val audio =
            AudioStreamSelector.selectPreferredAudioStream(
                InnerTubeStreamBridge.convertAudioFormats(result.audioFormats),
                preferredAudioLanguage = "original",
                compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it) },
            )
        stage("h264At1080p: HLS proxy")
        val source =
            VideoPlaybackResolver(videoId).resolve(listOf(stream), audio, null, null, result.durationSeconds())
                ?: return failed("h264At1080p", videoId, "nothing resolved")
        stage("h264At1080p: AVPlayer")
        val observed = observe(source, expectVideo = true, requiredSeconds = 30.0)
        val at1080p = observed["presentationSize"]?.toString()?.trim('"') == "1920x1080"
        val clean = observed["segmentExceedsBandwidth12318"]?.toString() == "false"
        return buildJsonObject {
            put("id", videoId)
            put("codec", stream.codec)
            put("bitrate", stream.bitrate)
            observed.forEach { (key, value) -> put(key, value) }
            put("verdict", if (observed["verdict"]?.toString()?.trim('"') == "PASS" && at1080p && clean) "PASS" else "FAIL")
        }
    }

    /**
     * VP9 2160p60 with Opus for ten minutes, measured every 30 s: frames shown and dropped, the clock against real
     * time, gaps in the sound, CPU, the hottest thermal state and the battery level. It passes when it played the
     * ten minutes; whether it stayed sustained, and when the phone warmed, is what it is for.
     */
    private suspend fun longRun(videoId: String): JsonObject {
        stage("longRun: player response")
        val result = extraction(videoId) ?: return blockedOrFailed("longRun", videoId, "every client failed")
        val stream =
            InnerTubeStreamBridge
                .convertVideoFormats(result.videoFormats)
                .filter {
                    VideoCodecUtils.codecKeyFromStream(it) == "vp9" && !AdaptiveLadder.isHighBitDepth(it) &&
                        VideoCodecUtils.qualityHeightFromStream(it) == 2160 && PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.BUNDLED
                }.maxByOrNull { it.bitrate }
                ?: return buildJsonObject {
                    put("verdict", "BLOCKED")
                    put("layer", currentStage())
                    put("block", "no VP9 2160p offered")
                }
        val source =
            VideoPlaybackResolver(videoId).resolve(listOf(stream), opusAudio(result), null, null, result.durationSeconds())
                ?: return failed("longRun", videoId, "nothing resolved")
        val (batteryState, batteryStart) =
            withContext(Dispatchers.Main) { UIDevice.currentDevice.batteryState to UIDevice.currentDevice.batteryLevel }
        val engine = withContext(Dispatchers.Main) { PlaybackEngines.create(PlaybackEngineKind.BUNDLED).also { showOnScreen(it.layer) } }
        engine.prepare(source)
        engine.play()
        try {
            val opening = TimeSource.Monotonic.markNow()
            while (opening.elapsedNow() < 40.seconds && engine.error() == null && !(engine.isPlaying() && engine.currentPositionMs() > 0)) delay(100)
            val windows = mutableListOf<JsonObject>()
            var sustainedWholeTime = true
            var firstWarmerAtSeconds: Int? = null
            var totalSeconds = 0.0
            for (index in 1..LONG_RUN_WINDOWS) {
                if (engine.error() != null) break
                stage("longRun: window $index of $LONG_RUN_WINDOWS")
                val before = engine.stats()
                val cpuBefore = cpuSeconds()
                val positionBefore = engine.currentPositionMs()
                var thermalMax = NSProcessInfo.processInfo.thermalState
                val window = TimeSource.Monotonic.markNow()
                while (window.elapsedNow() < LONG_RUN_WINDOW_SECONDS.seconds && engine.error() == null) {
                    delay(1_000)
                    thermalMax = maxOf(thermalMax, NSProcessInfo.processInfo.thermalState)
                }
                val wallSeconds = window.elapsedNow().inWholeMilliseconds / 1000.0
                val after = engine.stats()
                val advanced = (engine.currentPositionMs() - positionBefore) / 1000.0
                val rendered = after.framesRendered - before.framesRendered
                val dropped = after.framesDropped - before.framesDropped
                val underruns = after.audioUnderruns - before.audioUnderruns
                val droppedShare = if (rendered + dropped > 0) dropped.toDouble() / (rendered + dropped) else 0.0
                val realtime = advanced / wallSeconds
                val sustained = droppedShare <= SUSTAINED_DROPPED_SHARE && realtime >= SUSTAINED_REALTIME && underruns == 0 && rendered > 0
                sustainedWholeTime = sustainedWholeTime && sustained
                totalSeconds += advanced
                if (firstWarmerAtSeconds == null && thermalMax != NSProcessInfoThermalState.NSProcessInfoThermalStateNominal) {
                    firstWarmerAtSeconds = index * LONG_RUN_WINDOW_SECONDS
                }
                val battery = withContext(Dispatchers.Main) { UIDevice.currentDevice.batteryLevel }
                windows +=
                    buildJsonObject {
                        put("endsAtSeconds", index * LONG_RUN_WINDOW_SECONDS)
                        put("sustained", sustained)
                        put("framesRendered", rendered)
                        put("framesDropped", dropped)
                        put("droppedPercent", droppedShare * 100)
                        put("realtime", realtime)
                        put("audioUnderruns", underruns)
                        put("cpuPercentOfOneCore", (cpuSeconds() - cpuBefore) / wallSeconds * 100)
                        put("thermalMax", thermalName(thermalMax))
                        put("batteryPercent", battery * 100)
                    }
            }
            val batteryEnd = withContext(Dispatchers.Main) { UIDevice.currentDevice.batteryLevel }
            val playedAll = windows.size == LONG_RUN_WINDOWS && totalSeconds >= LONG_RUN_WINDOWS * LONG_RUN_WINDOW_SECONDS * 0.95
            return buildJsonObject {
                put("verdict", if (playedAll && engine.error() == null) "PASS" else "FAIL")
                put("engine", PlaybackEngineKind.BUNDLED.name)
                put("codec", stream.codec)
                put("videoSize", engine.stats().videoSize)
                put("audioDecoder", engine.stats().audioDecoder)
                put("secondsPlayed", totalSeconds)
                put("sustainedWholeTime", sustainedWholeTime)
                put("firstWarmerThanNominalAtSeconds", firstWarmerAtSeconds)
                // Charging at the start would heat the phone and refill the battery; the run asks for it unplugged.
                put("batteryStateAtStart", batteryState.name.removePrefix("UIDeviceBatteryState").lowercase())
                put("batteryPercentStart", batteryStart * 100)
                put("batteryPercentEnd", batteryEnd * 100)
                put("error", engine.error())
                put("windows", JsonArray(windows))
            }
        } finally {
            engine.close()
        }
    }

    // The audio YouTube pairs with VP9: Opus, chosen among the Opus streams by upstream's own rules.
    private fun opusAudio(result: VideoExtractionResult) =
        InnerTubeStreamBridge.convertAudioFormats(result.audioFormats).let { audios ->
            AudioStreamSelector.selectPreferredAudioStream(
                audios.filter { it.codec == "opus" }.ifEmpty { audios },
                preferredAudioLanguage = "original",
                compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it, PlaybackEngineKind.BUNDLED) },
            )
        }

    private fun VideoExtractionResult.durationSeconds(): Long = playerResponse.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0
}
