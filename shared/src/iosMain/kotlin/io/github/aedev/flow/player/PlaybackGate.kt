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
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import io.github.aedev.flow.utils.writeText
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonObject
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
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.VideoToolbox.VTIsHardwareDecodeSupported
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
 * The S04 go/no-go gate, run inside the app because AVPlayer and WKWebView need an app process. Swift
 * calls this in Debug builds only; with the launch argument `-flowPlaybackGate` it plays a real video
 * and a real YouTube Music song through the ported chain (client ladder, stream bridge, loopback HLS)
 * in AVPlayer, checks the cipher in JavaScriptCore and a PoToken mint in WKWebView, and writes the
 * verdict and every observation to `Documents/playback-gate.json` for CI.
 */
fun startPlaybackGateIfRequested() {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    if (GATE_ARGUMENT !in arguments) return
    val videoId = arguments.getOrNull(arguments.indexOf("-gateVideo") + 1).takeIf { "-gateVideo" in arguments } ?: DEFAULT_VIDEO_ID
    MainScope().launch { PlaybackGate.run(videoId) }
}

private object PlaybackGate {
    private val layers = mutableListOf<AVPlayerLayer>()

    suspend fun run(videoId: String) {
        Log.w(TAG, "Playback gate started for $videoId")
        val report =
            buildJsonObject {
                put("device", "${UIDevice.currentDevice.model} iOS ${UIDevice.currentDevice.systemVersion}")
                put("codecs", codecEvidence())
                put("cipher", step { cipher() })
                put("poToken", step { poToken(videoId) })
                val music = step { music() }
                put("music", music)
                val video = step { video(videoId) }
                put("video", video)
                val verdicts = listOf(music, video).map { it["verdict"].toString().trim('"') }
                put(
                    "verdict",
                    when {
                        verdicts.all { it == "PASS" } -> "PASS"
                        "FAIL" in verdicts -> "FAIL"
                        else -> "BLOCKED"
                    },
                )
                put("diagnostics", buildJsonArray { PlayerDiagnostics.entries().takeLast(80).forEach { add(kotlinx.serialization.json.JsonPrimitive(it.toString())) } })
            }
        val json = report.toString()
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        Path(documents, "playback-gate.json").writeText(json)
        Log.w(TAG, "FLOW PLAYBACK GATE: $json")
    }

    private suspend fun step(block: suspend () -> JsonObject): JsonObject =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            buildJsonObject {
                put("verdict", "FAIL")
                put("error", "${e::class.simpleName}: ${e.message}")
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
        val sts = CipherDeobfuscator.ensureSignatureTimestamp()
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
        val visitorData = WebPoTokenSession.sessionVisitorData()
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
        val songId =
            YouTube
                .search("Queen Bohemian Rhapsody", YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                ?.firstOrNull()
                ?.id ?: FALLBACK_SONG_ID
        val playback =
            MusicPlayerUtils.playerResponseForPlayback(songId).getOrElse { error ->
                return blockedOrFailed("music", songId, error.message)
            }
        val stream = InnerTubeStreamBridge.convertAudioFormats(listOf(playback.format)).single()
        val source =
            VideoPlaybackResolver(songId).resolve(
                videoStreams = emptyList(),
                audioStream = stream,
                dashManifestUrl = null,
                hlsUrl = null,
                durationSeconds = playback.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0,
            ) ?: return failed("music", songId, "nothing resolved")
        val observed = observe(source, expectVideo = false)
        return buildJsonObject {
            put("id", songId)
            put("title", playback.videoDetails?.title)
            put("client", playback.usedClient.clientName)
            put("itag", playback.format.itag)
            put("mimeType", playback.format.mimeType)
            observed.forEach { (key, value) -> put(key, value) }
        }
    }

    private suspend fun video(videoId: String): JsonObject {
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
        val source =
            VideoPlaybackResolver(videoId).resolve(
                videoStreams = ladder.ifEmpty { listOf(anchor) },
                audioStream = audio,
                dashManifestUrl = null,
                hlsUrl = null,
                durationSeconds = result.playerResponse.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0,
                asAdaptiveLadder = ladder.isNotEmpty(),
            ) ?: return failed("video", videoId, "nothing resolved")
        val observed = observe(source, expectVideo = true)
        return buildJsonObject {
            put("id", videoId)
            put("client", result.usedClient.clientName)
            put("offered", all.joinToString { "${VideoCodecUtils.qualityHeightFromStream(it)}p ${VideoCodecUtils.codecKeyFromStream(it)}" })
            put("rungs", ladder.ifEmpty { listOf(anchor) }.joinToString { "${VideoCodecUtils.qualityHeightFromStream(it)}p ${it.codec}" })
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
        put("id", id)
        put("error", "$what: $why")
    }

    // BLOCKED only when YouTube refused the viewer (bot wall, sign-in, region), as the ladder reports it.
    private fun blockedOrFailed(
        what: String,
        id: String,
        why: String?,
    ): JsonObject {
        val block = InnerTubeVideoStreamExtractor.blockOf(id) ?: return failed(what, id, why)
        return buildJsonObject {
            put("verdict", "BLOCKED")
            put("id", id)
            put("block", block.name)
            put("error", "$what: $why")
        }
    }

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
        if (expectVideo) showOnScreen(player)
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
            put("errorLog", buildJsonArray { errors.takeLast(5).forEach { add(kotlinx.serialization.json.JsonPrimitive("${it.errorStatusCode} ${it.errorComment} ${it.URI}")) } })
        }
    }

    // The video on screen, so CI's screenshot shows the frames AVPlayer decoded.
    private fun showOnScreen(player: AVPlayer) {
        @Suppress("DEPRECATION")
        val window = UIApplication.sharedApplication.keyWindow ?: return
        val layer = AVPlayerLayer.playerLayerWithPlayer(player)
        layer.frame = window.bounds
        window.layer.addSublayer(layer)
        layers += layer
    }
}
