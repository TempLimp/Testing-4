package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.core.SabrCpn
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.core.SabrStreamController
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.network.SabrDataSource
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Records the SABR fixture the offline replays use (SabrReplayTest on jvm and iOS, SabrReplayTests in the simulator):
 * one real exchange per engine with GVS for Big Buck Bunny, at its smallest rungs so the fixture stays small: 144p
 * H.264 with AAC 140 for AVPlayer, 144p VP9 with Opus 251 for the bundled engine. Runs only when FLOW_RECORD_SABR
 * names the directory to write (iosApp/iosAppTests/Sabr), from a network YouTube serves.
 *
 * Only what the session needs is kept ([SabrFixture.sanitize]): the request identifier and cancellation policy parts
 * and the playback cookie are dropped, and the streaming URL, PoToken, visitor id and ustreamer config of the real
 * session are not written at all (the URL carries the recording network's address).
 */
class SabrFixtureRecorderJvmTest {
    private class Recording(
        userAgent: String,
        val responses: MutableList<ByteArray>,
    ) : SabrDataSource(userAgent) {
        override suspend fun <T> open(
            url: String,
            body: ByteArray,
            visitorId: String?,
            read: suspend (ByteReadChannel) -> T,
        ): T =
            super.open(url, body, visitorId) { channel ->
                val bytes = channel.toByteArray()
                responses += bytes
                read(ByteReadChannel(bytes))
            }
    }

    @Test
    fun record() {
        val target = System.getenv("FLOW_RECORD_SABR") ?: return
        runBlocking {
            val result = assertNotNull(InnerTubeVideoStreamExtractor.extract(VIDEO_ID, forceSabr = true), "no SABR session")
            val base = assertNotNull(result.sabrInfo)
            val formats = result.playerResponse.streamingData!!.adaptiveFormats.filter { it.xtags.isNullOrEmpty() }
            val dir = File(target).apply { mkdirs() }
            val sessions =
                listOf("h264" to (160 to 140), "vp9" to (278 to 251)).map { (name, itags) ->
                    val video = formats.first { it.itag == itags.first }
                    val audio = formats.first { it.itag == itags.second }
                    val info = base.withFormats(video, audio)
                    val responses = exchange(info)
                    responses.forEachIndexed { index, bytes -> File(dir, "$name-${index + 1}.ump").writeBytes(SabrFixture.sanitize(bytes)) }
                    println("SABR FIXTURE: $name ${responses.size} responses, ${responses.sumOf { it.size }} bytes")
                    buildJsonObject {
                        put("name", name)
                        put("videoId", VIDEO_ID)
                        put("durationMs", info.durationMs)
                        put("videoItag", info.videoItag)
                        put("videoLmt", info.videoLmt)
                        put("videoMimeType", info.videoMimeType)
                        put("videoHeight", info.videoHeight)
                        put("audioItag", info.audioItag)
                        put("audioLmt", info.audioLmt)
                        put("audioMimeType", info.audioMimeType)
                        put("responses", responses.size)
                    }
                }
            File(dir, "manifest.json").writeText(
                buildJsonObject {
                    put("source", "Big Buck Bunny (c) Blender Foundation, www.bigbuckbunny.org, CC BY 3.0; YouTube $VIDEO_ID")
                    put("recorded", kotlin.time.Clock.System.now().toString())
                    put("sessions", buildJsonArray { sessions.forEach { add(it) } })
                }.toString(),
            )
        }
    }

    // The session as upstream's factory builds it, run until both tracks hold 30 s or the server stops sending.
    private suspend fun exchange(info: SabrStreamInfo): List<ByteArray> {
        val responses = mutableListOf<ByteArray>()
        val state =
            SabrSessionState().apply {
                streamingUrl = info.streamingUrl
                videoId = VIDEO_ID
                selectedAudioItag = info.audioItag
                selectedAudioLmt = info.audioLmt
                selectedVideoItag = info.videoItag
                selectedVideoLmt = info.videoLmt
                selectedVideoHeight = info.videoHeight
                stickyResolution = info.targetHeight
                poToken = info.poToken
                visitorId = info.visitorId
                ustreamerConfig = info.ustreamerConfig
                durationMs = info.durationMs
                clientNameId = info.clientNameId
                clientVersion = info.clientVersion
                cpn = SabrCpn.generate()
            }
        val controller = SabrStreamController(Recording(info.clientUserAgent, responses), state)
        controller.startSession()
        repeat(6) {
            val audioEnd = state.audioBufferedRanges.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0
            val videoEnd = state.videoBufferedRanges.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0
            if (minOf(audioEnd, videoEnd) >= 30_000) return responses
            state.playheadPositionMs = (minOf(audioEnd, videoEnd) - 5_000).coerceAtLeast(0)
            controller.requestNextSegments()
        }
        controller.release()
        assertTrue(responses.isNotEmpty())
        return responses
    }

    private fun SabrStreamInfo.withFormats(
        video: PlayerResponse.StreamingData.Format,
        audio: PlayerResponse.StreamingData.Format,
    ) = copy(
        videoItag = video.itag,
        videoLmt = video.lastModified ?: 0,
        videoMimeType = video.mimeType,
        videoHeight = video.height ?: 0,
        targetHeight = video.height ?: 0,
        videoXtags = "",
        audioItag = audio.itag,
        audioLmt = audio.lastModified ?: 0,
        audioMimeType = audio.mimeType,
        audioXtags = "",
        audioTrackId = "",
    )

    private companion object {
        // Big Buck Bunny (Blender Foundation, CC BY 3.0).
        const val VIDEO_ID = "aqz-KE-bpKQ"
    }
}
