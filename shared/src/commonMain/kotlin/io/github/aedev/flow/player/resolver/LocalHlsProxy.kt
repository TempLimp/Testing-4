package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.network.ProxyAwareClient
import io.github.aedev.flow.player.datasource.GoogleVideoRequestPolicy
import io.github.aedev.flow.player.engine.InvalidResponseCodeException
import io.github.aedev.flow.player.media.localFileEngineKind
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.player.sabr.integration.SabrProxySession
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
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicInt
import kotlin.time.TimeSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.use
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
 *   classified as upstream's data source does. The bundled engine reads the same route as a file
 *   (`.webm` for a WebM rendition), so both engines fetch through one request policy.
 *
 * A loopback server rather than an `AVAssetResourceLoaderDelegate`: AVFoundation lets a resource loader
 * answer HLS playlists and keys but not media segments, and S05's SABR streams (bytes that arrive over
 * a POST, not from a URL) must reach AVPlayer as segments through this same server.
 */
object LocalHlsProxy {
    private const val TAG = "LocalHlsProxy"
    private const val HOST = "127.0.0.1"
    private const val MAX_PRESENTATIONS = 32
    private const val ALIVE_TIMEOUT_MS = 1_000L

    private val httpClient = ProxyAwareClient()
    private val startLock = Mutex()
    private var server: EmbeddedServer<*, *>? = null
    private var port = 0
    private val nextId = AtomicInt(0)
    private val presentations = ConcurrentMap<String, HlsPresentation>()
    private val segmentCache = ConcurrentMap<String, List<SidxSegment>>()
    private val sabrSessions = ConcurrentMap<String, SabrProxySession>()
    private val localFiles = ConcurrentMap<String, String>()
    private val refusals = ConcurrentMap<String, InvalidResponseCodeException>()

    /**
     * What upstream's data-source chain adds around YouTubeHttpDataSource for media requests (PlayerCacheManager's
     * dash and progressive factories): a URL swapped after a refusal, the debug builds' fake wall, and one retry of a
     * refused request once a replacement is recorded (RefusedStreamRetryDataSource). Set by the player.
     */
    interface DataPath {
        fun resolveUrl(url: String): String

        fun refuses(
            url: String,
            position: Long,
        ): Boolean

        suspend fun onRefused(
            position: Long,
            url: String,
        ): Boolean
    }

    var dataPath: DataPath? = null

    /** Told each googlevideo media transfer's size and time, as Media3 tells its bandwidth meter. */
    var transferListener: ((bytes: Long, elapsedMs: Long) -> Unit)? = null

    /** The media cache in front of googlevideo (Media3's CacheDataSource over SimpleCache); null for none. */
    var mediaCache: io.github.aedev.flow.player.cache.ProxyMediaCache? = null

    /** The last googlevideo refusal of a source this proxy published at [uri], as Media3's error cause would carry it. */
    fun refusalFor(uri: String): InvalidResponseCodeException? = PUBLISHED_ID.find(uri)?.groupValues?.get(1)?.let { refusals[it] }

    /** Starts the server once; returns the port it listens on. */
    suspend fun start(): Int =
        startLock.withLock {
            if (server != null) return@withLock port
            val started =
                embeddedServer(CIO, port = 0, host = HOST) {
                    routing {
                        get("/alive") { call.respondText("ok") }
                        get("/p/{id}/master.m3u8") { serveMaster(call) }
                        get("/p/{id}/{rendition}.m3u8") { serveMedia(call) }
                        get("/p/{id}/{rendition}.mp4") { serveBytes(call) }
                        get("/p/{id}/{rendition}.webm") { serveBytes(call) }
                        get("/s/{id}/master.m3u8") { serveSabrMaster(call) }
                        get("/s/{id}/{track}.m3u8") { serveSabrMedia(call) }
                        get("/s/{id}/{track}.mp4") { serveSabrBytes(call) }
                        get("/s/{id}/pipe/{track}.mp4") { serveSabrPipe(call) }
                        get("/s/{id}/pipe/{track}.webm") { serveSabrPipe(call) }
                        get("/f/{id}/{name}") { serveLocalFile(call) }
                    }
                }.start(wait = false)
            port = started.engine.resolvedConnectors().first().port
            server = started
            Log.d(TAG, "Serving HLS on $HOST:$port")
            port
        }

    private val restarts = AtomicInt(0)

    /** How often the server has been started again after it stopped answering. */
    val restartCount: Int get() = restarts.load()

    /**
     * iOS reclaims a suspended app's listening sockets (one playing audio is not suspended, but one paused a while in the
     * background is), so a server that was running may be gone. True when it no longer answered and runs again on a new
     * port: sources published before point at nothing and have to be published again (the player reopens its video).
     */
    suspend fun restartIfGone(): Boolean =
        startLock.withLock {
            val running = server ?: return@withLock false
            if (answers()) return@withLock false
            Log.w(TAG, "The loopback server on $port stopped answering; starting it again")
            PlayerDiagnostics.logWarning(TAG, "loopback server on $port gone (socket reclaimed?); restarting")
            runCatching { running.stop(0, 0) }
            server = null
            restarts.incrementAndFetch()
            true
        }.also { restarted -> if (restarted) start() }

    private suspend fun answers(): Boolean =
        withTimeoutOrNull(ALIVE_TIMEOUT_MS) {
            val alive = "http://$HOST:$port/alive"
            // Loopback: the app proxy's selector sends it direct.
            runCatching { httpClient.get(Url(alive)).prepareGet(alive).execute { it.status == HttpStatusCode.OK } }.getOrDefault(false)
        } == true

    /** Stops the listener but keeps the server object, as iOS reclaiming the socket leaves it. Tests only. */
    internal suspend fun dropListenerForTest() {
        startLock.withLock { server?.stop(0, 0) }
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

    /**
     * Publishes [presentation] for the bundled engine, which reads each rendition as one file through the same
     * byte-range route AVPlayer's segments take: their URLs, in [HlsPresentation.renditions] order.
     */
    suspend fun publishFiles(presentation: HlsPresentation): List<String> {
        val base = publish(presentation).substringBeforeLast('/')
        return presentation.renditions.mapIndexed { index, rendition -> "$base/$index.${if (rendition.mimeType.endsWith("/webm")) "webm" else "mp4"}" }
    }

    /**
     * Publishes a SABR [session] for [engine]: AVPlayer gets an HLS master of its formats, the bundled engine each track
     * as a stream ([SabrProxySession]). Evicting it later ends the session, as upstream's MediaLoader.releaseSabr does.
     */
    suspend fun publishSabr(
        session: SabrProxySession,
        engine: PlaybackEngineKind,
        mediaId: String = "",
    ): MediaSource {
        val port = start()
        val id = nextId.incrementAndFetch().toString()
        sabrSessions[id] = session
        (id.toInt() - MAX_PRESENTATIONS).takeIf { it > 0 }?.let { evict(it.toString()) }
        val base = "http://$HOST:$port/s/$id"
        if (engine == PlaybackEngineKind.AVPLAYER) {
            return MediaSource("$base/master.m3u8", MediaSourceBuilder.MIME_HLS, mediaId, durationMs = session.durationMs)
        }
        val (video, audio) = session.tracks
        return MediaSource(
            uri = "$base/pipe/0.${video.extension}",
            mimeType = video.container,
            mediaId = mediaId,
            engine = PlaybackEngineKind.BUNDLED,
            audioUri = "$base/pipe/1.${audio.extension}",
            durationMs = session.durationMs,
            streamed = true,
        )
    }

    /**
     * A file on the device, served with byte ranges as googlevideo's are, so both engines read it as they read a
     * stream: the engine its video codec needs ([localFileEngineKind]); a muxed file gives the bundled engine both tracks.
     */
    suspend fun localFileSource(
        path: String,
        mediaId: String = "",
    ): MediaSource {
        val port = start()
        val id = nextId.incrementAndFetch().toString()
        localFiles[id] = path
        (id.toInt() - MAX_PRESENTATIONS).takeIf { it > 0 }?.let { evict(it.toString()) }
        val extension = path.substringAfterLast('.', "").lowercase()
        val url = "http://$HOST:$port/f/$id/file.$extension"
        val mimeType = localMimeType(extension)
        val engine = localFileEngineKind(path)
        return MediaSource(
            uri = url,
            mimeType = mimeType,
            mediaId = mediaId,
            engine = engine,
            audioUri = url.takeIf { engine == PlaybackEngineKind.BUNDLED && mimeType.startsWith("video/") },
        )
    }

    private fun localMimeType(extension: String): String =
        when (extension) {
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "m4a", "aac" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "mka" -> "audio/x-matroska"
            "mp3" -> "audio/mpeg"
            else -> "video/mp4"
        }

    private suspend fun serveLocalFile(call: ApplicationCall) {
        val path = call.parameters["id"]?.let { localFiles[it] }?.toPath() ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val size = FileSystem.SYSTEM.metadataOrNull(path)?.size ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val range = call.request.header(HttpHeaders.Range)?.let { BYTE_RANGE.find(it) }
        val first = range?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val last = range?.groupValues?.get(2)?.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
        if (first >= size || first > last) {
            call.response.header(HttpHeaders.ContentRange, "bytes */$size")
            return call.respondText("", status = HttpStatusCode.RequestedRangeNotSatisfiable)
        }
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        if (range != null) call.response.header(HttpHeaders.ContentRange, "bytes $first-$last/$size")
        val status = if (range != null) HttpStatusCode.PartialContent else HttpStatusCode.OK
        call.respondBytesWriter(ContentType.parse(localMimeType(path.name.substringAfterLast('.', "").lowercase())), status, last - first + 1) {
            FileSystem.SYSTEM.openReadOnly(path).use { handle ->
                val buffer = ByteArray(LOCAL_CHUNK)
                var at = first
                while (at <= last) {
                    val read = handle.read(at, buffer, 0, minOf(LOCAL_CHUNK.toLong(), last - at + 1).toInt())
                    if (read <= 0) break
                    writeFully(buffer, 0, read)
                    at += read
                }
            }
        }
    }

    private fun evict(id: String) {
        sabrSessions.remove(id)?.close()
        localFiles.remove(id)
        refusals.remove(id)
        val presentation = presentations.remove(id) ?: return
        presentation.renditions.indices.forEach { segmentCache.remove("$id/$it") }
    }

    private suspend fun serveMaster(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val presentation = presentations[id] ?: return call.respondText("", status = HttpStatusCode.NotFound)
        // The indexes the media playlists need next, read now so the master states each rendition's real rates.
        val measured =
            coroutineScope {
                presentation.renditions
                    .mapIndexed { index, rendition -> async { indexOrNull("$id/$index", rendition)?.let(rendition::measuredBy) ?: rendition } }
                    .awaitAll()
            }
        val video = measured.take(presentation.video.size)
        val shown = HlsPresentation(video, measured.drop(video.size).firstOrNull(), presentation.durationMs)
        call.respondText(
            HlsPlaylists.master(shown) { "${measured.indexOf(it)}.m3u8" },
            ContentType.parse(HLS_MIME),
        )
    }

    private suspend fun serveMedia(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val index = call.parameters["rendition"]?.toIntOrNull()
        val rendition =
            index?.let { presentations[id]?.renditions?.getOrNull(it) }
                ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val segments = indexOrNull("$id/$index", rendition) ?: return call.respondText("", status = HttpStatusCode.BadGateway)
        call.respondText(HlsPlaylists.media(rendition, segments, "$index.mp4"), ContentType.parse(HLS_MIME))
    }

    private suspend fun indexOrNull(
        key: String,
        rendition: HlsRendition,
    ): List<SidxSegment>? =
        try {
            segmentsOf(key, rendition)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Index of itag ${StreamDenialClassifier.itagOf(rendition.url)} unreadable: ${e.message}")
            null
        }

    private suspend fun segmentsOf(
        key: String,
        rendition: HlsRendition,
    ): List<SidxSegment>? {
        segmentCache[key]?.let { return it }
        val bytes =
            fetch(dataPath?.resolveUrl(rendition.url) ?: rendition.url, "bytes=${rendition.indexStart}-${rendition.indexEnd}") { response ->
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
        val id = call.parameters["id"].orEmpty()
        val position = range?.let { BYTE_RANGE.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val path = dataPath
        val contentType = ContentType.parse(rendition.mimeType.ifEmpty { if (rendition.isAudio) "audio/mp4" else "video/mp4" })
        // The cache key is the URL the stream was published with, as Media3's cache sits outside its URL swap.
        val cacheKey = rendition.url
        val total = StreamDenialClassifier.queryParam(rendition.url, "clen")?.toLongOrNull()
        val requestedLast = range?.let { BYTE_RANGE.find(it)?.groupValues?.get(2)?.toLongOrNull() } ?: total?.minus(1)
        val cache = mediaCache
        if (cache != null && requestedLast != null) {
            val cached = cache.read(cacheKey, position, requestedLast)
            if (cached != null) {
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header(HttpHeaders.ContentRange, "bytes $position-$requestedLast/${total ?: "*"}")
                call.respondBytesWriter(contentType, HttpStatusCode.PartialContent, cached.size.toLong()) { writeFully(cached, 0, cached.size) }
                return
            }
        }
        var url = path?.resolveUrl(rendition.url) ?: rendition.url
        // One retry, as RefusedStreamRetryDataSource reopens once a replacement for the refused file is recorded.
        var retried = false
        while (true) {
            val refused =
                if (path?.refuses(url, position) == true) {
                    Log.w("FakeStreamWall", "fake wall refused c=${StreamDenialClassifier.clientOf(url)} at byte $position")
                    HttpStatusCode.Forbidden
                } else {
                    val started = TimeSource.Monotonic.markNow()
                    fetch(url, range) { upstream ->
                        val status = upstream.status
                        if (status.value !in 200..299) return@fetch status
                        refusals.remove(id)
                        upstream.headers[HttpHeaders.ContentRange]?.let { call.response.header(HttpHeaders.ContentRange, it) }
                        call.response.header(HttpHeaders.AcceptRanges, "bytes")
                        // A server that ignores the range sends the file from its start, which is not what [position] names.
                        val writer = cache?.takeIf { status == HttpStatusCode.PartialContent || range == null }?.writer(cacheKey, position)
                        call.respondBytesWriter(
                            contentType = contentType,
                            status = status,
                            contentLength = upstream.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                        ) {
                            val body = upstream.bodyAsChannel()
                            val buffer = ByteArray(COPY_CHUNK)
                            var bytes = 0L
                            try {
                                while (true) {
                                    val read = body.readAvailable(buffer, 0, buffer.size)
                                    if (read < 0) break
                                    if (read == 0) continue
                                    writeFully(buffer, 0, read)
                                    writer?.write(buffer, 0, read)
                                    bytes += read
                                }
                                writer?.commit()
                            } catch (e: Throwable) {
                                writer?.abort()
                                throw e
                            }
                            transferListener?.invoke(bytes, started.elapsedNow().inWholeMilliseconds)
                        }
                        null
                    }
                } ?: return
            if (refused == HttpStatusCode.Forbidden) reportForbidden(url, range)
            if (refused == HttpStatusCode.Forbidden && path != null && !retried && path.onRefused(position, url)) {
                retried = true
                url = path.resolveUrl(rendition.url)
                continue
            }
            Log.w(TAG, "googlevideo answered $refused for itag ${StreamDenialClassifier.itagOf(url)} $range")
            refusals[id] = InvalidResponseCodeException(refused.value, url)
            call.respondText("", status = refused)
            return
        }
    }

    private fun sabrTrack(call: ApplicationCall): Pair<SabrProxySession, SabrProxySession.Track>? {
        val session = call.parameters["id"]?.let { sabrSessions[it] } ?: return null
        val track = call.parameters["track"]?.toIntOrNull()?.let { session.tracks.getOrNull(it) } ?: return null
        return session to track
    }

    private suspend fun serveSabrMaster(call: ApplicationCall) {
        val session = call.parameters["id"]?.let { sabrSessions[it] } ?: return call.respondText("", status = HttpStatusCode.NotFound)
        // Both renditions come from the init segments the session's first response carries.
        val renditions = session.tracks.map { session.rendition(it)?.first ?: return call.respondText("", status = HttpStatusCode.BadGateway) }
        val video = renditions.filterNot { it.isAudio }
        val shown = HlsPresentation(video, renditions.firstOrNull { it.isAudio }, session.durationMs)
        call.respondText(HlsPlaylists.master(shown) { "${renditions.indexOf(it)}.m3u8" }, ContentType.parse(HLS_MIME))
    }

    private suspend fun serveSabrMedia(call: ApplicationCall) {
        val (session, track) = sabrTrack(call) ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val (rendition, segments) = session.rendition(track) ?: return call.respondText("", status = HttpStatusCode.BadGateway)
        call.respondText(HlsPlaylists.media(rendition, segments, "${call.parameters["track"]}.mp4"), ContentType.parse(HLS_MIME))
    }

    // AVPlayer's byte ranges of the file: the init segment, or the segments the sidx places there, as SABR delivers them.
    private suspend fun serveSabrBytes(call: ApplicationCall) {
        val (session, track) = sabrTrack(call) ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val (_, index) = session.rendition(track) ?: return call.respondText("", status = HttpStatusCode.BadGateway)
        val init = session.init(track) ?: return call.respondText("", status = HttpStatusCode.BadGateway)
        val size = index.lastOrNull()?.let { it.offset + it.size } ?: init.size.toLong()
        val range = call.request.header(HttpHeaders.Range)?.let { BYTE_RANGE.find(it) }
        val first = range?.groupValues?.get(1)?.toLong() ?: 0L
        val last = (range?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toLong() ?: (size - 1)).coerceAtMost(size - 1)
        if (first > last) return call.respondText("", status = HttpStatusCode.RequestedRangeNotSatisfiable)

        suspend fun slice(at: Long): ByteArray? {
            if (at < init.size) return init.copyOfRange(at.toInt(), minOf(init.size.toLong(), last + 1).toInt())
            val entry = index.firstOrNull { at >= it.offset && at < it.offset + it.size } ?: return null
            val timeMs = index.takeWhile { it !== entry }.sumOf { it.durationSeconds } * 1000
            val segment = session.segmentFor(track, entry.offset, timeMs.toLong(), (entry.durationSeconds * 1000).toLong()) ?: return null
            val from = (at - segment.startDataRange).toInt()
            return segment.data.copyOfRange(from, minOf(segment.data.size.toLong(), last + 1 - segment.startDataRange).toInt())
        }
        val head = slice(first) ?: return call.respondText("", status = HttpStatusCode.NotFound)
        call.response.header(HttpHeaders.ContentRange, "bytes $first-$last/$size")
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.respondBytesWriter(ContentType.parse(track.container), HttpStatusCode.PartialContent, last - first + 1) {
            var bytes = head
            var at = first
            while (true) {
                writeFully(bytes)
                at += bytes.size
                if (at > last) break
                // A range across segments waits for the next; one that never comes ends the response short.
                bytes = slice(at) ?: break
            }
        }
    }

    // The bundled engine's read of a track: upstream's pipe, the init segment and then the segments in order.
    private suspend fun serveSabrPipe(call: ApplicationCall) {
        val (session, track) = sabrTrack(call) ?: return call.respondText("", status = HttpStatusCode.NotFound)
        val init = session.init(track) ?: return call.respondText("", status = HttpStatusCode.BadGateway)
        call.respondBytesWriter(ContentType.parse(track.container), HttpStatusCode.OK) {
            try {
                writeFully(init)
                flush()
                var segment = session.nextSegment(track, null)
                while (segment != null) {
                    writeFully(segment.data)
                    flush()
                    segment = session.nextSegment(track, segment)
                }
            } finally {
                session.readerGone(track)
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
    private const val LOCAL_CHUNK = 256 * 1024
    private const val COPY_CHUNK = 64 * 1024
    private val PUBLISHED_ID = Regex("""/[pf]/(\d+)/""")
    private val BYTE_RANGE = Regex("""bytes=(\d+)-(\d*)""")
}
