package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.innertube.blockReason
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.network.platformHttpClient
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceFactory
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceResult
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A live SABR session end to end short of the engines, the SABR counterpart of LivePlaybackChainTest: upstream's forced
 * SABR extraction (MWEB, then WEB, with a BotGuard PoToken), the session upstream's factory builds, and what
 * LocalHlsProxy serves from it to each engine: AVPlayer an HLS master, a media playlist from the sidx in the init
 * segment and segments that are fMP4; the bundled engine each track as a stream that starts like its container and
 * keeps coming. A network YouTube walls, or a target with no BotGuard host (the iOS test executable has no web view),
 * prints `LIVE SABR: BLOCKED` and passes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SabrPlaybackChainTest {
    // No limit on a whole request: the bundled engine's streams last as long as the session runs.
    private val http = platformHttpClient { install(HttpTimeout) { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS } }

    @Test
    fun anH264SabrSessionBecomesHlsAVPlayerCanOpen() =
        live("avplayer") {
            // Upstream's quality-targeted session (its reloads and downloads), at 1080p H.264: the rung AVPlayer decodes.
            val info =
                InnerTubeVideoStreamExtractor.resolveSabrDownload(VIDEO_ID, targetHeight = 1080, preferredCodec = "h264", client = YouTubeClient.MWEB)
                    ?: return@live blocked("avplayer", "no SABR session: ${refusals()}")
            withSession(info) { result ->
                val source = result.mediaSource
                assertEquals(PlaybackEngineKind.AVPLAYER, source.engine)
                val master = http.get(source.uri).bodyAsText()
                assertTrue("#EXT-X-STREAM-INF" in master && "#EXT-X-MEDIA:TYPE=AUDIO" in master, master)
                val base = source.uri.substringBeforeLast('/')
                val media = http.get("$base/0.m3u8").bodyAsText()
                assertTrue("#EXT-X-MAP:URI=" in media && "#EXT-X-ENDLIST" in media, media.take(600))
                val segments = Regex("""#EXT-X-BYTERANGE:(\d+)@(\d+)\n(\S+)""").findAll(media).toList()
                assertTrue(segments.size > 50, "only ${segments.size} segments in ${media.take(600)}")
                // The first four segments: about 20 seconds, which the session's first responses carry.
                segments.take(4).forEach { segment ->
                    val (length, offset, file) = segment.destructured
                    val bytes = http.get("$base/$file") { header("Range", "bytes=$offset-${offset.toLong() + length.toLong() - 1}") }.readRawBytes()
                    assertEquals(length.toLong(), bytes.size.toLong(), "segment at $offset")
                    assertEquals("moof", bytes.copyOfRange(4, 8).decodeToString(), "segment at $offset")
                }
                val audio = http.get("$base/1.m3u8").bodyAsText()
                assertTrue("#EXT-X-BYTERANGE:" in audio, audio.take(600))
                println("LIVE SABR: avplayer OK: video itag ${info.videoItag} ${info.videoMimeType}, audio itag ${info.audioItag}, ${segments.size} segments, 4 served")
            }
        }

    @Test
    fun aForcedSabrSessionStreamsBothTracksToTheBundledEngine() =
        live("bundled") {
            // Upstream's forced SABR (forceSabr): the tallest rung, VP9 or AV1 with Opus, which the bundled engine plays.
            val result =
                InnerTubeVideoStreamExtractor.extract(VIDEO_ID, forceSabr = true)
                    ?: return@live blocked("bundled", "no SABR session: ${refusals()}")
            val info = assertNotNull(result.sabrInfo, "forced SABR resolved without a session")
            withSession(info) { session ->
                val source = session.mediaSource
                assertEquals(PlaybackEngineKind.BUNDLED, source.engine)
                assertTrue(source.streamed)
                // Both at once, as the engine reads them: the session paces each track by the other.
                val (video, audio) =
                    coroutineScope {
                        val video = async { read(source.uri, VIDEO_BYTES) }
                        val audio = async { read(assertNotNull(source.audioUri), AUDIO_BYTES) }
                        video.await() to audio.await()
                    }
                assertContainer(source.uri, video)
                assertContainer(source.audioUri!!, audio)
                println(
                    "LIVE SABR: bundled OK via ${result.usedClient.clientName}: video itag ${info.videoItag} ${info.videoMimeType} ${info.videoHeight}p, " +
                        "audio itag ${info.audioItag} ${info.audioMimeType}, read ${video.size} + ${audio.size} bytes",
                )
            }
        }

    private suspend fun withSession(
        info: SabrStreamInfo,
        block: suspend (SabrMediaSourceResult) -> Unit,
    ) {
        // The orchestrator collects on the main dispatcher, which a test process does not run.
        Dispatchers.setMain(Dispatchers.Default)
        val result = assertNotNull(SabrMediaSourceFactory.create(info, VIDEO_ID, info.durationMs), "no engine for $info")
        try {
            block(result)
        } finally {
            result.session.close()
            Dispatchers.resetMain()
        }
    }

    // The first [count] bytes of a stream the proxy is still being fed, as the bundled engine reads them.
    private suspend fun read(
        uri: String,
        count: Int,
    ): ByteArray =
        http.prepareGet(uri).execute { response ->
            val channel = response.bodyAsChannel()
            val bytes = ByteArray(count)
            var filled = 0
            while (filled < count) {
                val read = channel.readAvailable(bytes, filled, count - filled)
                if (read < 0) break
                filled += read
            }
            assertEquals(count, filled, "$uri ended after $filled bytes")
            bytes
        }

    private fun assertContainer(
        uri: String,
        bytes: ByteArray,
    ) {
        val webm = bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
        val mp4 = bytes.copyOfRange(4, 8).decodeToString() == "ftyp"
        assertTrue(if (uri.endsWith(".webm")) webm else mp4, "$uri does not start like its container")
    }

    private fun blocked(
        what: String,
        reason: String,
    ) = println("LIVE SABR: BLOCKED $what: $reason")

    private fun refusals(): String =
        PlayerDiagnostics
            .entries()
            .filter { "SABR" in it.message || "skip " in it.message || "PoToken" in it.message }
            .takeLast(8)
            .joinToString(" | ") { it.message }

    private fun live(
        what: String,
        block: suspend () -> Unit,
    ) = runBlocking {
        try {
            withTimeout(180.seconds) { block() }
        } catch (error: Throwable) {
            val reason = blockReason(error) ?: throw error
            blocked(what, reason)
        }
    }

    private companion object {
        // Big Buck Bunny (Blender Foundation, CC BY 3.0).
        const val VIDEO_ID = "aqz-KE-bpKQ"

        // About ten seconds of 2160p VP9, and more audio than one 2 MiB read: both only arrive as the session runs on.
        const val VIDEO_BYTES = 24 shl 20
        const val AUDIO_BYTES = 640 shl 10
    }
}
