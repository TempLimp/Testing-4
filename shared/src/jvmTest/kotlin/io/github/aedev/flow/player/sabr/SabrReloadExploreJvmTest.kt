package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.network.platformHttpClient
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceFactory
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test

/**
 * How long GVS keeps a live SABR session going (S05's phone run saw it stop 49.9 s in): reads a 1080p H.264 session's
 * segments through LocalHlsProxy as AVPlayer does, video and audio at once, and prints each answer and the session's
 * diagnostics. FLOW_EXPLORE_SABR=fast reads as fast as the session sends; =paced keeps 30 s ahead of the wall clock, as
 * a player's buffer does. FLOW_EXPLORE_UNTIL caps the media time read (ms, default 120000). Does nothing without
 * FLOW_EXPLORE_SABR; needs a network YouTube serves and a Chromium for the PoToken.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SabrReloadExploreJvmTest {
    private val http = platformHttpClient { install(HttpTimeout) { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS } }

    @Test
    fun explore() =
        runBlocking {
            val mode = System.getenv("FLOW_EXPLORE_SABR") ?: return@runBlocking
            val untilMs = System.getenv("FLOW_EXPLORE_UNTIL")?.toLong() ?: 120_000L
            val info = InnerTubeVideoStreamExtractor.resolveSabrDownload(VIDEO_ID, targetHeight = 1080, preferredCodec = "h264", client = YouTubeClient.MWEB)
            if (info == null) {
                println("EXPLORE: no SABR session: ${PlayerDiagnostics.entries().takeLast(8).joinToString(" | ") { it.message }}")
                return@runBlocking
            }
            // The orchestrator collects on the main dispatcher, which a test process does not run.
            Dispatchers.setMain(Dispatchers.Default)
            val result = SabrMediaSourceFactory.create(info, VIDEO_ID, info.durationMs)!!
            val started = System.currentTimeMillis()
            try {
                val base = result.mediaSource.uri.substringBeforeLast('/')
                coroutineScope {
                    listOf("0", "1").map { track ->
                        async {
                            val media = http.get("$base/$track.m3u8").bodyAsText()
                            var atMs = 0.0
                            for (segment in Regex("""#EXTINF:([\d.]+),\n#EXT-X-BYTERANGE:(\d+)@(\d+)\n(\S+)""").findAll(media)) {
                                val (seconds, length, offset, file) = segment.destructured
                                if (atMs > untilMs) break
                                if (mode == "paced") while (atMs > System.currentTimeMillis() - started + 30_000) delay(200)
                                val response = http.get("$base/$file") { header("Range", "bytes=$offset-${offset.toLong() + length.toLong() - 1}") }
                                val bytes = response.readRawBytes()
                                println("EXPLORE: track $track at ${atMs / 1000} s: ${response.status.value}, ${bytes.size} B, wall ${(System.currentTimeMillis() - started) / 1000.0} s")
                                if (response.status.value >= 300) break
                                atMs += seconds.toDouble() * 1000
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                result.session.close()
                Dispatchers.resetMain()
                PlayerDiagnostics.entries().forEach { println("EXPLORE DIAG: ${it.message}") }
            }
        }

    private companion object {
        // Big Buck Bunny (Blender Foundation, CC BY 3.0).
        const val VIDEO_ID = "aqz-KE-bpKQ"
    }
}
