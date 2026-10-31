@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.quality.AdaptiveLadder
import io.github.aedev.flow.player.quality.AutoQualityPolicy
import io.github.aedev.flow.player.resolver.MediaSource
import io.github.aedev.flow.player.resolver.PlatformCodecSupport
import io.github.aedev.flow.player.resolver.VideoPlaybackResolver
import io.github.aedev.flow.player.resolver.canMergeAudio
import io.github.aedev.flow.player.resolver.canPlay
import io.github.aedev.flow.player.stream.AudioStreamSelector
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.VideoCodecUtils
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

/**
 * The S04 go/no-go gate, run inside the app because AVPlayer and WKWebView need an app process. It plays a
 * real video and a real YouTube Music song through the ported chain (client ladder, stream bridge, loopback
 * HLS) in AVPlayer, checks the cipher in JavaScriptCore and a PoToken mint in WKWebView, shows each verdict
 * over the video, and writes the verdict and every observation to `Documents/playback-gate.json`.
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

// Reasons in which the client never answered, was not asked, or answered without a stream to make playable.
private val NO_STREAM_OFFERED = Regex("timeout or null response|not asked|no visitorData|PoToken unavailable|demoted|no adaptive formats|live but no hls")
private object PlaybackGate {
    // The gate writes its result before this, whatever a step is doing; CI waits a little longer.
    private val DEADLINE = 8.minutes

    private val layers = mutableListOf<AVPlayerLayer>()
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
                    listOf("cipher", "poToken", "music", "video").filter { it !in results }.forEach { missing ->
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
                val items = listOf("cipher", "poToken", "music", "video").map(::verdictOf)
                when {
                    "FAIL" in items || null in items -> "FAIL"
                    listOf("music", "video").all { verdictOf(it) == "PASS" } -> "PASS"
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
                    listOf("cipher", "poToken", "music", "video").forEach { name ->
                        val item = results[name] as? JsonObject ?: return@forEach
                        val details =
                            listOf("layer", "client", "codec", "presentationSize", "secondsAdvanced", "block", "error")
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
        stage("music: AVPlayer")
        val observed = observe(source, expectVideo = false)
        return buildJsonObject {
            put("id", songId)
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
        val result =
            InnerTubeVideoStreamExtractor.extract(videoId)
                ?: return blockedOrFailed("video", videoId, "every client failed")
        val all = InnerTubeStreamBridge.convertVideoFormats(result.videoFormats)
        val videos = all.filter { PlatformCodecSupport.canPlay(it) }
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
            observed.forEach { (key, value) -> put(key, value) }
        }
    }

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
        val asset = AVURLAsset(uRL = NSURL.URLWithString(source.uri)!!, options = null)
        val item = AVPlayerItem(asset = asset)
        val output = AVPlayerItemVideoOutput(pixelBufferAttributes = null)
        item.addOutput(output)
        val player = AVPlayer(playerItem = item)
        if (expectVideo) withContext(Dispatchers.Main) { showOnScreen(player) }
        player.play()

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

        val audibleGroup = asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicAudible)
        val audibleSelected = audibleGroup?.let { item.currentMediaSelection.selectedMediaOptionInMediaSelectionGroup(it) } != null
        val advanced = lastTime - (firstTime ?: lastTime)
        val ready = item.status == AVPlayerItemStatusReadyToPlay
        val audioPresent = hasAudio || audibleSelected
        val pass = ready && audioPresent && advanced >= REQUIRED_ADVANCE_SECONDS && (!expectVideo || (hasVideo && frames > 0))
        val access = item.accessLog()?.events?.lastOrNull() as? AVPlayerItemAccessLogEvent
        val errors = item.errorLog()?.events?.mapNotNull { it as? AVPlayerItemErrorLogEvent }.orEmpty()
        if (!expectVideo) player.pause()
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
        }
    }

    // The video on screen under the verdicts, so CI's screenshot and the person holding the phone see it.
    private fun showOnScreen(player: AVPlayer) {
        @Suppress("DEPRECATION")
        val window = UIApplication.sharedApplication.keyWindow ?: return
        val layer = AVPlayerLayer.playerLayerWithPlayer(player)
        val view = UIView(frame = window.bounds)
        layer.frame = window.bounds
        view.layer.addSublayer(layer)
        window.addSubview(view)
        overlay?.let { window.bringSubviewToFront(it) }
        layers += layer
    }
}
