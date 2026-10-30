package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.network.ProxyAwareClient
import io.github.aedev.flow.player.datasource.GoogleVideoRequestPolicy
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.utils.Log
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * The loopback HTTP server AVPlayer plays YouTube's adaptive streams through. AVPlayer reads HLS and
 * no DASH, so where upstream builds ExoPlayer a DASH manifest over each googlevideo URL, this serves
 * the same stream as HLS:
 *
 * - `/p/{id}/master.m3u8`: the presentation's master playlist, written without a network call;
 * - `/p/{id}/{n}.m3u8`: rendition `n`'s media playlist, from its sidx, fetched once when AVPlayer
 *   first asks for it;
 * - `/p/{id}/{n}.mp4`: rendition `n`'s bytes, each byte range forwarded to googlevideo with the user
 *   agent and headers the minting client needs ([GoogleVideoRequestPolicy]), its 403s logged and
 *   classified as upstream's data source does.
 *
 * A loopback server rather than an `AVAssetResourceLoaderDelegate`: AVFoundation lets a resource loader
 * answer HLS playlists and keys but not media segments, and S05's SABR streams (bytes that arrive over
 * a POST, not from a URL) must reach AVPlayer as segments through this same server.
 */
object LocalHlsProxy {
    private const val TAG = "LocalHlsProxy"
    private const val HOST = "127.0.0.1"
    private const val MAX_PRESENTATIONS = 32

    private val httpClient = ProxyAwareClient()
    private val startLock = Mutex()
    private var server: EmbeddedServer<*, *>? = null
    private var port = 0
    private val nextId = AtomicInt(0)
    private val presentations = ConcurrentMap<String, HlsPresentation>()
    private val segmentCache = ConcurrentMap<String, List<SidxSegment>>()

    /** Starts the server once; returns the port it listens on. */
    suspend fun start(): Int =
        startLock.withLock {
            if (server != null) return@withLock port
            val started =
                embeddedServer(CIO, port = 0, host = HOST) {
                    routing {
                        get("/p/{id}/master.m3u8") { serveMaster(call) }
                        get("/p/{id}/{rendition}.m3u8") { serveMedia(call) }
                        get("/p/{id}/{rendition}.mp4") { serveBytes(call) }
                    }
                }.start(wait = false)
            port = started.engine.resolvedConnectors().first().port
            server = started
            Log.d(TAG, "Serving HLS on $HOST:$port")
            port
        }

    /** Publishes [presentation] and returns its master playlist's URL. */
    suspend fun publish(presentation: HlsPresentation): String {
        val port = start()
        val id = nextId.incrementAndFetch().toString()
        presentations[id] = presentation
        // Only the latest few videos are ever playing or preloaded; older ones are dropped.
        (id.toInt() - MAX_PRESENTATIONS).takeIf { it > 0 }?.let { evict(it.toString()) }
        return "http://$HOST:$port/p/$id/master.m3u8"
    }

    private fun evict(id: String) {
        val presentation = presentations.remove(id) ?: return
        presentation.renditions.indices.forEach { segmentCache.remove("$id/$it") }
    }

    private suspend fun serveMaster(call: ApplicationCall) {
        val presentation = call.parameters["id"]?.let { presentations[it] } ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val renditions = presentation.renditions
        call.respondText(
            HlsPlaylists.master(presentation) { "${renditions.indexOf(it)}.m3u8" },
            ContentType.parse(HLS_MIME),
        )
    }

    private suspend fun serveMedia(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val index = call.parameters["rendition"]?.toIntOrNull()
        val rendition =
            index?.let { presentations[id]?.renditions?.getOrNull(it) }
                ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val segments =
            try {
                segmentsOf("$id/$index", rendition)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Index of itag ${StreamDenialClassifier.itagOf(rendition.url)} unreadable: ${e.message}")
                null
            } ?: return call.respondText("", status = HttpStatusCode.BadGateway)
        call.respondText(HlsPlaylists.media(rendition, segments, "$index.mp4"), ContentType.parse(HLS_MIME))
    }

    private suspend fun segmentsOf(
        key: String,
        rendition: HlsRendition,
    ): List<SidxSegment>? {
        segmentCache[key]?.let { return it }
        val bytes =
            fetch(rendition.url, "bytes=${rendition.indexStart}-${rendition.indexEnd}") { response ->
                if (response.status.value !in 200..299) null else response.readRawBytes()
            } ?: return null
        // A server that ignores the range sends the whole file; the index is then at its own offset.
        val box =
            if (bytes.size.toLong() > rendition.indexEnd - rendition.indexStart + 1) {
                bytes.copyOfRange(rendition.indexStart.toInt(), (rendition.indexEnd + 1).toInt())
            } else {
                bytes
            }
        return SidxParser.parse(box, rendition.indexStart)?.also { segmentCache[key] = it }
    }

    private suspend fun serveBytes(call: ApplicationCall) {
        val presentation = call.parameters["id"]?.let { presentations[it] }
        val rendition =
            call.parameters["rendition"]?.toIntOrNull()?.let { presentation?.renditions?.getOrNull(it) }
                ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val range = call.request.header(HttpHeaders.Range)
        fetch(rendition.url, range) { upstream ->
            val status = upstream.status
            if (status == HttpStatusCode.Forbidden) reportForbidden(rendition.url, range)
            if (status.value !in 200..299) {
                Log.w(TAG, "googlevideo answered $status for itag ${StreamDenialClassifier.itagOf(rendition.url)} $range")
                call.respondText("", status = status)
                return@fetch
            }
            upstream.headers[HttpHeaders.ContentRange]?.let { call.response.header(HttpHeaders.ContentRange, it) }
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.respondBytesWriter(
                contentType = ContentType.parse(if (rendition.isAudio) "audio/mp4" else "video/mp4"),
                status = status,
                contentLength = upstream.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
            ) {
                upstream.bodyAsChannel().copyTo(this)
            }
        }
    }

    private suspend fun <T> fetch(
        url: String,
        range: String?,
        block: suspend (HttpResponse) -> T,
    ): T {
        val client = StreamDenialClassifier.clientOf(url)
        return httpClient.get(Url(url)).prepareGet(url) {
            header(HttpHeaders.UserAgent, GoogleVideoRequestPolicy.userAgent(client, YouTubeClient.USER_AGENT_WEB))
            GoogleVideoRequestPolicy.headers(client).forEach { (name, value) -> header(name, value) }
            range?.let { header(HttpHeaders.Range, it) }
        }.execute { block(it) }
    }

    // Upstream's YouTubeHttpDataSource.logForbidden; acting on the denial is the player's error handler (S10).
    private fun reportForbidden(
        url: String,
        range: String?,
    ) {
        val expiry = StreamDenialClassifier.describeExpiry(url)
        val kind = StreamDenialClassifier.classify(url)
        val client = StreamDenialClassifier.clientOf(url)
        val itag = StreamDenialClassifier.itagOf(url)
        val pot = StreamDenialClassifier.hasPoToken(url)
        Log.w(TAG, "HTTP 403 c=$client itag=$itag pot=$pot range=$range $expiry denial=$kind")
        PlayerDiagnostics.logWarning(TAG, "403 c=$client itag=$itag pot=$pot range=$range $expiry denial=$kind")
    }

    private const val HLS_MIME = "application/vnd.apple.mpegurl"
}
