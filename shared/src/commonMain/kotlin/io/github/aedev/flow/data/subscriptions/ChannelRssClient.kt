package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.network.ProxyAwareClient
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/**
 * The single place the app fetches a YouTube channel's RSS feed.
 *
 * Both the subscription feed and the background new-upload check go through here, so there is one
 * connection pool, one timeout policy and one parser for the same endpoint.
 */
class ChannelRssClient internal constructor(
    private val httpClient: () -> HttpClient,
    private val sleep: suspend (Long) -> Unit,
) {
    constructor() : this(
        ProxyAwareClient {
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_SECONDS * 1000L
                socketTimeoutMillis = TIMEOUT_SECONDS * 1000L
            }
        }.let { client -> { client.get() } },
        { delay(it) },
    )

    /**
     * A [Result] rather than a nullable feed: the callers distinguish "this channel has nothing
     * new" from "this channel could not be reached", and only the latter is worth surfacing.
     *
     * A sweep over hundreds of channels meets the odd 429 or 5xx; one spaced retry turns most of
     * them into a feed instead of a channel whose uploads vanish until the next refresh.
     */
    suspend fun fetch(channelId: String): Result<ChannelRssFeed> =
        withContext(PerformanceDispatcher.networkIO) {
            val first = attempt(channelId)
            val retryAfterMs = first.retryAfterMs ?: return@withContext first.result
            sleep(retryAfterMs)
            attempt(channelId).result
        }

    private class Attempt(
        val result: Result<ChannelRssFeed>,
        val retryAfterMs: Long? = null,
    )

    private suspend fun attempt(channelId: String): Attempt =
        try {
            val response = httpClient().get(RSS_URL_PREFIX + channelId)
            val code = response.status.value
            when {
                code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR -> {
                    Attempt(
                        result = Result.failure(IOException("HTTP $code for channel $channelId")),
                        retryAfterMs = retryDelayMs(response.headers["Retry-After"]),
                    )
                }

                !response.status.isSuccess() -> {
                    Attempt(Result.failure(IOException("HTTP $code for channel $channelId")))
                }

                else -> {
                    val body = response.bodyAsText()
                    if (body.isEmpty()) {
                        Attempt(Result.failure(IOException("Empty RSS body for channel $channelId")))
                    } else {
                        Attempt(runCatching { ChannelRssParser.parse(body) })
                    }
                }
            }
        } catch (e: CancellationException) {
            // Upstream's call blocks; a suspending one must not turn cancellation into a failed channel.
            throw e
        } catch (e: IOException) {
            Attempt(Result.failure(e), retryAfterMs = DEFAULT_RETRY_DELAY_MS)
        } catch (e: RuntimeException) {
            Attempt(Result.failure(e))
        }

    private fun retryDelayMs(retryAfterHeader: String?): Long =
        retryAfterHeader
            ?.trim()
            ?.toLongOrNull()
            ?.times(1000L)
            ?.coerceIn(DEFAULT_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)
            ?: DEFAULT_RETRY_DELAY_MS

    private companion object {
        const val RSS_URL_PREFIX = "https://www.youtube.com/feeds/videos.xml?channel_id="
        const val TIMEOUT_SECONDS = 30L
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        const val DEFAULT_RETRY_DELAY_MS = 1_500L
        const val MAX_RETRY_DELAY_MS = 10_000L
    }
}
