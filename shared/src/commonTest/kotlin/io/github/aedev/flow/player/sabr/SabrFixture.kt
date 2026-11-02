package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.proto.ProtobufReader
import io.github.aedev.flow.player.sabr.ump.UmpFrameDecoder
import io.github.aedev.flow.player.sabr.ump.UmpPartType
import io.github.aedev.flow.player.sabr.ump.UmpVarInt
import io.github.aedev.flow.testing.SABR_FIXTURE_DIR
import io.github.aedev.flow.utils.protobuf.ProtobufWriter
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.toByteArray
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The SABR fixture (iosApp/iosAppTests/Sabr, written by SabrFixtureRecorderJvmTest): real UMP responses GVS sent for Big
 * Buck Bunny (Blender Foundation, CC BY 3.0), one session per engine, and what each session asked for.
 */
internal object SabrFixture {
    @Serializable
    data class Session(
        val name: String,
        val videoId: String,
        val durationMs: Long,
        val videoItag: Int,
        val videoLmt: Long,
        val videoMimeType: String,
        val videoHeight: Int,
        val audioItag: Int,
        val audioLmt: Long,
        val audioMimeType: String,
        val responses: Int,
    )

    @Serializable
    private data class Manifest(
        val sessions: List<Session>,
    )

    fun session(name: String): Session =
        Json { ignoreUnknownKeys = true }.decodeFromString<Manifest>(read("manifest.json").decodeToString()).sessions.single { it.name == name }

    fun response(
        session: Session,
        number: Int,
    ): ByteArray = read("${session.name}-$number.ump")

    /** The session as upstream's resolver would hand it over, pointed at [server]; nothing in it is checked there. */
    fun info(
        session: Session,
        server: String,
    ) = SabrStreamInfo(
        streamingUrl = "$server/videoplayback?sabr=1",
        audioItag = session.audioItag,
        audioLmt = session.audioLmt,
        videoItag = session.videoItag,
        videoLmt = session.videoLmt,
        durationMs = session.durationMs,
        poToken = "fixture",
        visitorId = "fixture",
        ustreamerConfig = "fixture".encodeToByteArray(),
        audioMimeType = session.audioMimeType,
        videoMimeType = session.videoMimeType,
        targetHeight = session.videoHeight,
        videoHeight = session.videoHeight,
    )

    private fun read(name: String): ByteArray = SystemFileSystem.source(Path(SABR_FIXTURE_DIR, name)).buffered().use { it.readByteArray() }

    // The parts a session plays from; the rest (request identifiers, cancellation policy, SABR contexts) are opaque server state.
    private val KEPT =
        setOf(
            UmpPartType.MEDIA_HEADER,
            UmpPartType.MEDIA,
            UmpPartType.MEDIA_END,
            UmpPartType.NEXT_REQUEST_POLICY,
            UmpPartType.FORMAT_INITIALIZATION_METADATA,
            UmpPartType.PLAYBACK_START_POLICY,
            UmpPartType.STREAM_PROTECTION_STATUS,
            UmpPartType.END_OF_TRACK,
        )

    /** [ump] with only the parts in [KEPT], and the playback cookie out of the request policy. */
    fun sanitize(ump: ByteArray): ByteArray {
        val decoder = UmpFrameDecoder()
        decoder.feed(ump)
        val out = Buffer()
        while (decoder.hasNext()) {
            val frame = decoder.next()
            check(frame.type != UmpPartType.SABR_REDIRECT && frame.type != UmpPartType.RELOAD_PLAYER_RESPONSE) {
                "the recording was redirected or reloaded (${UmpPartType.nameOf(frame.type)}); record again"
            }
            if (frame.type !in KEPT) continue
            val payload =
                if (frame.type == UmpPartType.NEXT_REQUEST_POLICY) {
                    ProtobufWriter.encode {
                        ProtobufReader(frame.payload).forEachField { field ->
                            if (field.fieldNumber == 7) return@forEachField
                            if (field.wireType == ProtobufReader.WIRE_LENGTH_DELIMITED) writeBytes(field.fieldNumber, field.bytesValue) else writeVarint(field.fieldNumber, field.varintValue)
                        }
                    }
                } else {
                    frame.payload
                }
            out.write(UmpVarInt.encode(frame.type.toLong()))
            out.write(UmpVarInt.encode(payload.size.toLong()))
            out.write(payload)
        }
        return out.readByteArray()
    }
}

/**
 * GVS for one recorded session: the n-th POST is answered with the n-th recorded response, and every later one with no
 * media (only a request policy), as GVS answers a client that already holds what it may have.
 */
internal class SabrReplayServer(
    private val session: SabrFixture.Session,
) {
    /** The request bodies, in order. */
    val requests: List<ByteArray> get() = synchronized(lock) { received.toList() }
    private val received = mutableListOf<ByteArray>()
    private val lock = SynchronizedObject()
    private var server: EmbeddedServer<*, *>? = null

    /** Starts listening on loopback; returns the base URL. */
    suspend fun start(): String {
        val started =
            embeddedServer(CIO, port = 0, host = "127.0.0.1") {
                routing {
                    post("/videoplayback") {
                        val body = call.receiveChannel().toByteArray()
                        val number =
                            synchronized(lock) {
                                received += body
                                received.size
                            }
                        val response = if (number <= session.responses) SabrFixture.response(session, number) else policyOnly()
                        call.respondBytes(response, ContentType("application", "vnd.yt-ump"))
                    }
                }
            }.start(wait = false)
        server = started
        return "http://127.0.0.1:${started.engine.resolvedConnectors().first().port}"
    }

    fun stop() {
        server?.stop(0, 0)
    }

    private fun policyOnly(): ByteArray {
        val policy = ProtobufWriter.encode { writeInt64(4, 1_000) }
        return UmpVarInt.encode(UmpPartType.NEXT_REQUEST_POLICY.toLong()) + UmpVarInt.encode(policy.size.toLong()) + policy
    }
}
