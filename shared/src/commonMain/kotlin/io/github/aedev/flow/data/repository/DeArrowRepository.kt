package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.DeArrowContent
import io.github.aedev.flow.utils.LruCache
import io.github.aedev.flow.utils.atomicPutIfAbsent
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.util.collections.ConcurrentMap
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

private val monotonicStart = TimeSource.Monotonic.markNow()

/**
 * DeArrow branding (community titles and thumbnails, https://dearrow.ajay.app/) per video.
 *
 * A video nobody has submitted for answers 404 with a usable body (the random frame time), so 404
 * is cached like any answer. One request per video per [TTL_MS]: concurrent asks share a request
 * and [MAX_PARALLEL] keeps a fast scroll from filling the network pool.
 */
class DeArrowRepository internal constructor(
    // The shared client, read per request so a proxy change applies (upstream's live OkHttpClient).
    private val client: () -> HttpClient,
    private val dispatcher: CoroutineDispatcher,
    private val clockMs: () -> Long,
) {
    constructor(
        client: () -> HttpClient,
        dispatcher: CoroutineDispatcher,
    ) : this(client, dispatcher, { monotonicStart.elapsedNow().inWholeMilliseconds })

    private class Entry(
        val content: DeArrowContent?,
        val atMs: Long,
    )

    private val lock = SynchronizedObject()
    private val answers = LruCache<String, Entry>(CAPACITY)
    private val inFlight = ConcurrentMap<String, CompletableDeferred<DeArrowContent?>>()
    private val permits = Semaphore(MAX_PARALLEL)

    /** What a request already answered, for a card's first frame. */
    fun cached(videoId: String): DeArrowContent? = fresh(videoId)?.content

    suspend fun branding(videoId: String): DeArrowContent? {
        fresh(videoId)?.let { return it.content }
        val mine = CompletableDeferred<DeArrowContent?>()
        inFlight.atomicPutIfAbsent(videoId, mine)?.let { return it.await() }
        try {
            val content = permits.withPermit { fetch(videoId) }
            mine.complete(content)
            return content
        } catch (e: CancellationException) {
            mine.complete(null)
            throw e
        } finally {
            inFlight.remove(videoId, mine)
        }
    }

    private suspend fun fetch(videoId: String): DeArrowContent? =
        withContext(dispatcher) {
            val url = URLBuilder(BRANDING_URL).apply { parameters.append("videoID", videoId) }.build()
            try {
                val response = client().get(url)
                if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) return@withContext null
                parse(response.bodyAsText()).also { store(videoId, it) }
            } catch (e: IOException) {
                null
            }
        }

    private fun fresh(videoId: String): Entry? =
        synchronized(lock) {
            val entry = answers[videoId] ?: return null
            if (clockMs() - entry.atMs < TTL_MS) return entry
            answers.remove(videoId)
            null
        }

    private fun store(
        videoId: String,
        content: DeArrowContent?,
    ) {
        synchronized(lock) { answers.put(videoId, Entry(content, clockMs())) }
    }

    internal companion object {
        const val BRANDING_URL = "https://sponsor.ajay.app/api/branding"
        const val CAPACITY = 1_000
        const val MAX_PARALLEL = 4
        const val TTL_MS = 60 * 60 * 1000L

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): DeArrowContent? = runCatching { json.decodeFromString<DeArrowContent>(body) }.getOrNull()
    }
}
