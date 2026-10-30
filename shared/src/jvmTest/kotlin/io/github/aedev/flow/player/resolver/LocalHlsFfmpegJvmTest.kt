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
 * A second, independent reader of the HLS [LocalHlsProxy] serves: ffmpeg decodes ten seconds of a live
 * video's master playlist (video rendition plus audio group) through the proxy. Skipped where ffmpeg
 * is not installed, and when YouTube resolves nothing (LivePlaybackChainTest reports that case).
 */
class LocalHlsFfmpegJvmTest {
    @Test
    fun ffmpegDecodesTheProxiedHls() =
        runBlocking {
            val ffmpeg =
                System.getenv("PATH").split(File.pathSeparator).flatMap { listOf(File(it, "ffmpeg"), File(it, "ffmpeg.exe")) }.firstOrNull { it.canExecute() }
                    ?: return@runBlocking println("ffmpeg not installed; skipped")
            val result = InnerTubeVideoStreamExtractor.extract(VIDEO_ID) ?: return@runBlocking println("LIVE PLAYBACK: BLOCKED ffmpeg check")
            val video =
                InnerTubeStreamBridge
                    .convertVideoFormats(result.videoFormats)
                    .filter { PlatformCodecSupport.canPlay(it) && VideoCodecUtils.qualityHeightFromStream(it) <= 360 }
                    .maxBy { VideoCodecUtils.qualityHeightFromStream(it) }
            val audio =
                AudioStreamSelector.selectPreferredAudioStream(
                    InnerTubeStreamBridge.convertAudioFormats(result.audioFormats),
                    "original",
                    compatibilityFilter = { PlatformCodecSupport.canMergeAudio(it) },
                )
            val source = VideoPlaybackResolver(VIDEO_ID).resolve(listOf(video), audio, null, null, 635)
            assertNotNull(source)
            val process =
                ProcessBuilder(ffmpeg.path, "-v", "error", "-i", source.uri, "-t", "10", "-f", "null", "-")
                    .redirectErrorStream(true)
                    .start()
            val finished = process.waitFor(120, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            println("ffmpeg on ${source.uri}: exit=${if (finished) process.exitValue() else "timeout"} $output")
            assertEquals(0, if (finished) process.exitValue() else -1, output)
        }

    private companion object {
        const val VIDEO_ID = "aqz-KE-bpKQ"
    }
}
