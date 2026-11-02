package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.network.platformHttpClient
import io.github.aedev.flow.player.resolver.PlaybackEngineKind
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceFactory
import io.github.aedev.flow.player.sabr.integration.SabrMediaSourceResult
import io.github.aedev.flow.player.sabr.proto.ProtobufReader
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
 * The SABR chain offline, on every target: a recorded exchange with GVS (SabrFixture) replayed from a local server
 * through upstream's session and LocalHlsProxy, as each engine reads it. The H.264 session must become HLS whose
 * segments are the recorded fMP4; the VP9 session two streams that carry every recorded byte of both tracks in order.
 * Both must ask for their formats as the init-segment fix has it: no selected formats until the server initialized
 * them, both after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SabrReplayTest {
    private val http = platformHttpClient { install(HttpTimeout) { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS } }

    @Test
    fun anH264SessionReplaysAsHls() =
        replay("h264") { result, server ->
            val source = result.mediaSource
            assertEquals(PlaybackEngineKind.AVPLAYER, source.engine)
            val master = http.get(source.uri).bodyAsText()
            assertTrue("#EXT-X-STREAM-INF" in master && "#EXT-X-MEDIA:TYPE=AUDIO" in master && "avc1.4d400c,mp4a.40.2" in master, master)
            val base = source.uri.substringBeforeLast('/')
            // Every segment of both tracks that starts in the first 25 s: past the first response, so a second request goes.
            listOf("0", "1").forEach { track ->
                val media = http.get("$base/$track.m3u8").bodyAsText()
                assertTrue("#EXT-X-MAP:URI=\"$track.mp4\",BYTERANGE=" in media && "#EXT-X-ENDLIST" in media, media.take(600))
                var startsAt = 0.0
                Regex("""#EXTINF:([\d.]+),\n#EXT-X-BYTERANGE:(\d+)@(\d+)\n""").findAll(media).takeWhile { startsAt < 25.0 }.forEach { segment ->
                    val (duration, length, offset) = segment.destructured
                    val bytes = http.get("$base/$track.mp4") { header("Range", "bytes=$offset-${offset.toLong() + length.toLong() - 1}") }.readRawBytes()
                    assertEquals(length.toInt(), bytes.size, "track $track segment at $offset")
                    assertEquals("moof", bytes.copyOfRange(4, 8).decodeToString(), "track $track segment at $offset")
                    startsAt += duration.toDouble()
                }
            }
            assertSelectionFollowsInitialization(server, 160, 140)
        }

    @Test
    fun aVp9SessionReplaysAsTwoStreams() =
        replay("vp9") { result, server ->
            val source = result.mediaSource
            assertEquals(PlaybackEngineKind.BUNDLED, source.engine)
            assertTrue(source.streamed)
            val session = SabrFixture.session("vp9")
            // What the recording holds of each track, init segment included: the streams must carry exactly that, in order.
            val recorded = (1..session.responses).map { SabrFixture.response(session, it) }
            val (video, audio) =
                coroutineScope {
                    val video = async { read(source.uri, recordedBytes(recorded, session.videoItag)) }
                    val audio = async { read(assertNotNull(source.audioUri), recordedBytes(recorded, session.audioItag)) }
                    video.await() to audio.await()
                }
            assertTrue(video.copyOfRange(0, 4).contentEquals(EBML) && audio.copyOfRange(0, 4).contentEquals(EBML))
            assertSelectionFollowsInitialization(server, 278, 251)
        }

    private fun assertSelectionFollowsInitialization(
        server: SabrReplayServer,
        vararg itags: Int,
    ) {
        val requests = server.requests
        assertTrue(requests.size >= 2, "only ${requests.size} request(s) reached the server")
        assertEquals(null, ProtobufReader(requests[0]).readAllFields()[2], "the first request named selected formats")
        val selected = ProtobufReader(requests[1]).readAllFields()[2].orEmpty().map { ProtobufReader(it.asBytes()).readAllFields()[1]!!.first().asInt() }
        assertEquals(itags.toList().sorted(), selected.sorted())
    }

    // The bytes of [itag]'s segments in [responses], the init segment's included: what its stream must carry.
    private fun recordedBytes(
        responses: List<ByteArray>,
        itag: Int,
    ): Int {
        var total = 0
        responses.forEach { response ->
            val decoder = io.github.aedev.flow.player.sabr.ump.UmpFrameDecoder()
            decoder.feed(response)
            val headers = mutableMapOf<Int, Int>()
            while (decoder.hasNext()) {
                val frame = decoder.next()
                when (frame.type) {
                    io.github.aedev.flow.player.sabr.ump.UmpPartType.MEDIA_HEADER ->
                        io.github.aedev.flow.player.sabr.proto.MediaHeader.decode(frame.payload).let { headers[it.headerId] = it.itag }
                    io.github.aedev.flow.player.sabr.ump.UmpPartType.MEDIA ->
                        if (headers[frame.payload[0].toInt() and 0xFF] == itag) total += frame.payload.size - 1
                }
            }
        }
        return total
    }

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

    private fun replay(
        name: String,
        block: suspend (SabrMediaSourceResult, SabrReplayServer) -> Unit,
    ) = runBlocking {
        val session = SabrFixture.session(name)
        val server = SabrReplayServer(session)
        val base = server.start()
        // The orchestrator collects on the main dispatcher, which a test process does not run.
        Dispatchers.setMain(Dispatchers.Default)
        val result = assertNotNull(SabrMediaSourceFactory.create(SabrFixture.info(session, base), session.videoId, session.durationMs))
        try {
            withTimeout(60.seconds) { block(result, server) }
        } finally {
            result.session.close()
            server.stop()
            Dispatchers.resetMain()
        }
    }

    private companion object {
        val EBML = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    }
}
