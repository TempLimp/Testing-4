package io.github.aedev.flow.player.sabr.network

import io.github.aedev.flow.network.ProxyAwareClient
import io.github.aedev.flow.utils.Log
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlin.concurrent.Volatile

/**
 * HTTP transport for SABR protocol.
 * Sends POST requests with protobuf bodies and provides streaming access
 * to the UMP response. Does NOT implement Media3's DataSource interface —
 * this is a raw transport layer used by SabrStreamController.
 *
 * Upstream's OkHttp call becomes a Ktor request on the app's proxy-aware client; Ktor reads a body only
 * inside the request's scope, so [open] hands the UMP stream to a reader instead of returning it.
 */
// Open so a test can stand in for the network, as the controller takes its transport through the constructor.
open class SabrDataSource(
    private val userAgent: String,
) {
    companion object {
        private const val TAG = "SabrDataSource"

        // One pool for every session, as LocalHlsProxy's: upstream's per-source OkHttpClient carried these timeouts.
        private val client =
            ProxyAwareClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                    // OkHttp sets no limit on a whole call; a response carrying 20 s of 2160p can outlast CIO's 15 s default.
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            }
    }

    @Volatile
    private var currentCall: Job? = null

    /**
     * Opens a streaming POST request to the SABR endpoint and hands its UMP response to [read].
     *
     * @param url The GVS SABR streaming URL (contains sabr=1 parameter)
     * @param body Serialized VideoPlaybackAbrRequest protobuf
     * @throws IOException on a failed or cancelled request
     */
    open suspend fun <T> open(
        url: String,
        body: ByteArray,
        visitorId: String? = null,
        read: suspend (ByteReadChannel) -> T,
    ): T {
        close()

        Log.d(TAG, "SABR POST: ${url.take(100)}... bodySize=${body.size}")

        val call = Job(currentCoroutineContext()[Job])
        currentCall = call
        try {
            return withContext(call) {
                client.get(Url(url)).preparePost(url) {
                    // WEB SABR sends a binary body without a Content-Type header.
                    setBody(
                        object : OutgoingContent.ByteArrayContent() {
                            override val contentLength = body.size.toLong()

                            override fun bytes() = body
                        },
                    )
                    header(HttpHeaders.UserAgent, userAgent)
                    header(HttpHeaders.Origin, "https://www.youtube.com")
                    header(HttpHeaders.Referrer, "https://www.youtube.com/")
                    header("Sec-Fetch-Dest", "empty")
                    header("Sec-Fetch-Mode", "cors")
                    header("Sec-Fetch-Site", "cross-site")
                    header(HttpHeaders.Accept, "*/*")
                    // Sent on every SABR POST so GVS can link the request to the attested visitor
                    // session — the working desktop implementation does the same.
                    if (!visitorId.isNullOrEmpty()) header("X-Goog-Visitor-Id", visitorId)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        val errorBody =
                            try {
                                response.bodyAsText().take(500)
                            } catch (e: IOException) {
                                ""
                            }
                        throw IOException("SABR request failed: HTTP ${response.status.value} - $errorBody")
                    }

                    val contentType = response.headers[HttpHeaders.ContentType] ?: ""
                    if (!contentType.contains("application/vnd.yt-ump") && !contentType.contains("application/x-protobuf")) {
                        Log.w(TAG, "Unexpected content-type: $contentType (expected application/vnd.yt-ump)")
                    }

                    Log.d(TAG, "SABR stream opened: content-type=$contentType")
                    read(response.bodyAsChannel())
                }
            }
        } catch (e: CancellationException) {
            // [cancel] ends only this call, as cancelling an OkHttp call fails its read with an IOException.
            currentCoroutineContext().ensureActive()
            throw IOException("SABR request cancelled", e)
        } finally {
            call.complete()
        }
    }

    /** The only teardown safe while another coroutine reads the stream: it cancels the call, whose reader then fails. */
    fun cancel() {
        currentCall?.cancel()
    }

    /** Ends the call [open] made; its response is closed with it. */
    fun close() {
        cancel()
        currentCall = null
    }

    // The HTTP pool is shared by every session (ProxyAwareClient), so there is no client of its own to shut down.
    fun release() {
        cancel()
    }
}
