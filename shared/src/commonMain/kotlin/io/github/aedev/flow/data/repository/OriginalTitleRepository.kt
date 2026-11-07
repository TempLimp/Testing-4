package io.github.aedev.flow.data.repository

import io.github.aedev.flow.utils.LruCache
import io.github.aedev.flow.utils.atomicPutIfAbsent
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Titles in the video's own language. Every list response YouTube sends is translated into the
 * request language, and no request setting turns that off; oEmbed is the one cheap response that
 * is not (about 500 bytes against 200 KB for `/player`). Live-probed 2026-10-16 (#1230).
 *
 * One request per video per process: answers and refusals are cached, concurrent asks share one
 * request, and [MAX_PARALLEL] keeps a fast scroll from filling the network pool.
 */
class OriginalTitleRepository
    constructor(
        // The shared client, read per request so a proxy change applies (upstream's live OkHttpClient).
        private val client: () -> HttpClient,
        private val dispatcher: CoroutineDispatcher,
    ) {
        private val answers = BoundedCache(CAPACITY)
        private val inFlight = ConcurrentMap<String, CompletableDeferred<String?>>()
        private val permits = Semaphore(MAX_PARALLEL)
        private val answered =
            MutableSharedFlow<String>(extraBufferCapacity = ANSWER_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** What a request already answered, for a card's first frame. */
        fun cached(videoId: String): String? = answers[videoId]?.ifEmpty { null }

        /** [cached], updated when a lookup started elsewhere answers; never requests anything itself. */
        fun cachedUpdates(videoId: String): Flow<String?> =
            answered
                .filter { it == videoId }
                .map { cached(videoId) }
                .onStart { emit(cached(videoId)) }

        suspend fun originalTitle(videoId: String): String? {
            answers[videoId]?.let { return it.ifEmpty { null } }
            val mine = CompletableDeferred<String?>()
            inFlight.atomicPutIfAbsent(videoId, mine)?.let { return it.await() }
            try {
                val title = permits.withPermit { fetch(videoId) }
                mine.complete(title)
                return title
            } catch (e: CancellationException) {
                mine.complete(null)
                throw e
            } finally {
                inFlight.remove(videoId, mine)
            }
        }

        private suspend fun fetch(videoId: String): String? =
            withContext(dispatcher) {
                val url =
                    URLBuilder(OEMBED_URL)
                        .apply {
                            parameters.append("url", "https://www.youtube.com/watch?v=$videoId")
                            parameters.append("format", "json")
                        }.build()
                try {
                    val response = client().get(url)
                    when {
                        // Embedding turned off, or the video is gone: both are final answers.
                        response.status.value == 401 || response.status.value == 404 -> {
                            store(videoId, "")
                            null
                        }

                        !response.status.isSuccess() -> {
                            null
                        }

                        else -> {
                            parseTitle(response.bodyAsText()).also { store(videoId, it.orEmpty()) }
                        }
                    }
                } catch (e: IOException) {
                    null
                }
            }

        private fun store(
            videoId: String,
            title: String,
        ) {
            answers[videoId] = title
            answered.tryEmit(videoId)
        }

        private class BoundedCache(
            capacity: Int,
        ) {
            private val map = LruCache<String, String>(capacity)

            operator fun get(key: String): String? = map[key]

            operator fun set(
                key: String,
                value: String,
            ) {
                map.put(key, value)
            }
        }

        internal companion object {
            const val OEMBED_URL = "https://www.youtube.com/oembed"
            const val CAPACITY = 1_000
            const val MAX_PARALLEL = 4
            private const val ANSWER_BUFFER = 64

            private val json = Json { ignoreUnknownKeys = true }

            @Serializable
            private data class OEmbed(
                val title: String? = null,
            )

            fun parseTitle(body: String): String? =
                runCatching { json.decodeFromString<OEmbed>(body).title }
                    .getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
        }
    }
