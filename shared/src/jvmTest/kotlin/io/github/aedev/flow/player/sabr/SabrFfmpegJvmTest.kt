package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceFactory
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An independent decoder of live SABR media as LocalHlsProxy serves it: ffmpeg reads ten seconds of an H.264 session as
 * AVPlayer does (the HLS master, its video rendition and audio group as byte ranges), and ten seconds of a forced VP9 +
 * Opus session as the bundled engine does (both tracks' streams at once, in order, never seeking); and the same for the
 * recorded sessions replayed offline. Skipped where ffmpeg is not installed, the live ones where YouTube resolves no
 * session (SabrPlaybackChainTest reports why).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SabrFfmpegJvmTest {
    @Test
    fun ffmpegDecodesAnH264SabrSessionAsAVPlayerReadsIt() =
        runBlocking {
            val ffmpeg = ffmpeg() ?: return@runBlocking println("ffmpeg not installed; skipped")
            val info =
                InnerTubeVideoStreamExtractor.resolveSabrDownload(VIDEO_ID, targetHeight = 1080, preferredCodec = "h264", client = YouTubeClient.MWEB)
                    ?: return@runBlocking println("LIVE SABR: BLOCKED ffmpeg avplayer check")
            decodeSession(ffmpeg, info, PlaybackEngineKind.AVPLAYER) { listOf("-i", it.uri) }
        }

    @Test
    fun ffmpegDecodesAForcedSabrSessionAsTheBundledEngineReadsIt() =
        runBlocking {
            val ffmpeg = ffmpeg() ?: return@runBlocking println("ffmpeg not installed; skipped")
            val info = InnerTubeVideoStreamExtractor.extract(VIDEO_ID, forceSabr = true)?.sabrInfo ?: return@runBlocking println("LIVE SABR: BLOCKED ffmpeg bundled check")
            decodeSession(ffmpeg, info, PlaybackEngineKind.BUNDLED) { listOf("-i", it.uri, "-i", assertNotNull(it.audioUri), "-map", "0:v", "-map", "1:a") }
        }

    // The recorded sessions (SabrFixture), replayed from a local server: the same check, offline.
    @Test
    fun ffmpegDecodesTheReplayedH264SessionAsAVPlayerReadsIt() = replayed("h264", PlaybackEngineKind.AVPLAYER) { listOf("-i", it.uri) }

    @Test
    fun ffmpegDecodesTheReplayedVp9SessionAsTheBundledEngineReadsIt() =
        replayed("vp9", PlaybackEngineKind.BUNDLED) { listOf("-i", it.uri, "-i", assertNotNull(it.audioUri), "-map", "0:v", "-map", "1:a") }

    private fun replayed(
        name: String,
        engine: PlaybackEngineKind,
        inputs: (io.github.aedev.flow.player.resolver.MediaSource) -> List<String>,
    ) = runBlocking {
        val ffmpeg = ffmpeg() ?: return@runBlocking println("ffmpeg not installed; skipped")
        val session = SabrFixture.session(name)
        val server = SabrReplayServer(session)
        try {
            decodeSession(ffmpeg, SabrFixture.info(session, server.start()), engine, inputs)
        } finally {
            server.stop()
        }
    }

    private suspend fun decodeSession(
        ffmpeg: File,
        info: SabrStreamInfo,
        engine: PlaybackEngineKind,
        inputs: (io.github.aedev.flow.player.resolver.MediaSource) -> List<String>,
    ) {
        Dispatchers.setMain(Dispatchers.Default)
        val result = assertNotNull(SabrMediaSourceFactory.create(info, VIDEO_ID, info.durationMs))
        try {
            assertEquals(engine, result.mediaSource.engine)
            val process =
                ProcessBuilder(listOf(ffmpeg.path, "-v", "error", "-nostats") + inputs(result.mediaSource) + listOf("-t", "10", "-f", "null", "-", "-progress", "pipe:1"))
                    .redirectErrorStream(true)
                    .start()
            val finished = process.waitFor(150, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().readText()
            val frames = Regex("""frame=(\d+)""").findAll(output).lastOrNull()?.groupValues?.get(1)?.toInt() ?: 0
            val outTimeUs = Regex("""out_time_us=(\d+)""").findAll(output).lastOrNull()?.groupValues?.get(1)?.toLong() ?: 0
            val errors = output.lines().filterNot { '=' in it }.filter { it.isNotBlank() }
            println(
                "SABR: ffmpeg $engine itag ${info.videoItag} ${info.videoMimeType} + ${info.audioItag} ${info.audioMimeType}: " +
                    "exit=${if (finished) process.exitValue() else "timeout"} frames=$frames decoded to ${outTimeUs / 1000} ms ${errors.joinToString(" | ")}",
            )
            assertTrue(finished && process.exitValue() == 0, output.takeLast(2000))
            assertTrue(errors.isEmpty(), errors.joinToString("\n"))
            // Ten seconds at 24 to 60 fps, through to the -t limit.
            assertTrue(frames >= 240 && outTimeUs >= 9_900_000, "frames=$frames out_time_us=$outTimeUs")
        } finally {
            result.session.close()
            Dispatchers.resetMain()
        }
    }

    private fun ffmpeg(): File? =
        System.getenv("PATH").split(File.pathSeparator).flatMap { listOf(File(it, "ffmpeg"), File(it, "ffmpeg.exe")) }.firstOrNull { it.canExecute() }

    private companion object {
        // Big Buck Bunny (Blender Foundation, CC BY 3.0).
        const val VIDEO_ID = "aqz-KE-bpKQ"
    }
}
