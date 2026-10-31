package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.player.stream.AudioStreamSelector
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A second, independent reader of what [LocalHlsProxy] serves: ffmpeg decodes ten seconds of a live video's
 * master playlist (video rendition plus audio group) through the proxy, as AVPlayer reads it, and ten seconds
 * of a VP9 stream and its Opus audio as the files the bundled engine reads. Skipped where ffmpeg is not
 * installed, and when YouTube resolves nothing (LivePlaybackChainTest reports that case).
 */
class LocalHlsFfmpegJvmTest {
    @Test
    fun ffmpegDecodesTheProxiedHls() =
        runBlocking {
            val ffmpeg = ffmpeg() ?: return@runBlocking println("ffmpeg not installed; skipped")
            val result = InnerTubeVideoStreamExtractor.extract(VIDEO_ID) ?: return@runBlocking println("LIVE PLAYBACK: BLOCKED ffmpeg check")
            val video =
                InnerTubeStreamBridge
                    .convertVideoFormats(result.videoFormats)
                    .filter { PlatformCodecSupport.engineFor(it) == PlaybackEngineKind.AVPLAYER && VideoCodecUtils.qualityHeightFromStream(it) <= 360 }
                    .maxBy { VideoCodecUtils.qualityHeightFromStream(it) }
            val audio =
                AudioStreamSelector.selectPreferredAudioStream(
                    InnerTubeStreamBridge.convertAudioFormats(result.audioFormats),
                    "original",
                    compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it) },
                )
            val source = VideoPlaybackResolver(VIDEO_ID).resolve(listOf(video), audio, null, null, 635)
            assertNotNull(source)
            decode(ffmpeg, source.uri)
        }

    @Test
    fun ffmpegDecodesTheBundledEngineFiles() =
        runBlocking {
            val ffmpeg = ffmpeg() ?: return@runBlocking println("ffmpeg not installed; skipped")
            val result = InnerTubeVideoStreamExtractor.extract(VIDEO_ID) ?: return@runBlocking println("LIVE PLAYBACK: BLOCKED ffmpeg bundled check")
            val video =
                InnerTubeStreamBridge
                    .convertVideoFormats(result.videoFormats)
                    .filter { VideoCodecUtils.codecKeyFromStream(it) == "vp9" && VideoCodecUtils.qualityHeightFromStream(it) <= 360 }
                    .maxByOrNull { VideoCodecUtils.qualityHeightFromStream(it) }
                    ?: return@runBlocking println("LIVE PLAYBACK: BLOCKED ffmpeg bundled check, no VP9 offered")
            val audio =
                AudioStreamSelector.selectPreferredAudioStream(
                    InnerTubeStreamBridge.convertAudioFormats(result.audioFormats),
                    "original",
                    compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it, PlaybackEngineKind.BUNDLED) },
                )
            val source = VideoPlaybackResolver(VIDEO_ID).resolve(listOf(video), audio, null, null, 635)
            assertNotNull(source)
            assertEquals(PlaybackEngineKind.BUNDLED, source.engine)
            decode(ffmpeg, source.uri)
            decode(ffmpeg, assertNotNull(source.audioUri))
        }

    private fun ffmpeg(): File? =
        System.getenv("PATH").split(File.pathSeparator).flatMap { listOf(File(it, "ffmpeg"), File(it, "ffmpeg.exe")) }.firstOrNull { it.canExecute() }

    private fun decode(
        ffmpeg: File,
        uri: String,
    ) {
        val process =
            ProcessBuilder(ffmpeg.path, "-v", "error", "-i", uri, "-t", "10", "-f", "null", "-")
                .redirectErrorStream(true)
                .start()
        val finished = process.waitFor(120, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        println("ffmpeg on $uri: exit=${if (finished) process.exitValue() else "timeout"} $output")
        assertEquals(0, if (finished) process.exitValue() else -1, output)
    }

    private companion object {
        const val VIDEO_ID = "aqz-KE-bpKQ"
    }
}
