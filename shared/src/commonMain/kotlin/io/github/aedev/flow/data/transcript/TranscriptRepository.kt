package io.github.aedev.flow.data.transcript

import io.github.aedev.flow.utils.Log
import io.github.aedev.flow.utils.LruCache
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

private const val TAG = "TranscriptRepository"
private const val CACHE_SIZE = 4

/**
 * Reads a caption track as a transcript.
 *
 * The track URL comes from what the player already resolved for subtitles, so opening a transcript
 * costs one request for a few KB of text and nothing at all the second time.
 */
class TranscriptRepository
    constructor(
        // The shared client, read per request so a proxy change applies (upstream's live OkHttpClient).
        private val httpClient: () -> HttpClient,
    ) {
        private val cache = LruCache<String, List<TranscriptCue>>(CACHE_SIZE)

        suspend fun cues(trackUrl: String): List<TranscriptCue> =
            withContext(Dispatchers.IO) {
                if (trackUrl.isBlank()) return@withContext emptyList()
                cache.get(trackUrl)?.let { return@withContext it }
                val requestUrl = transcriptFormatUrl(trackUrl)
                val body =
                    try {
                        val response = httpClient().get(requestUrl)
                        if (!response.status.isSuccess()) {
                            Log.w(TAG, "Caption track returned HTTP ${response.status.value}")
                            return@withContext emptyList()
                        }
                        response.bodyAsText()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Caption track fetch failed: ${e.message}")
                        return@withContext emptyList()
                    }
                VideoTranscript.parse(body).also { cues ->
                    if (cues.isNotEmpty()) cache.put(trackUrl, cues)
                }
            }
    }

/**
 * The same caption track asked for as json3.
 *
 * The `fmt` already on the URL is replaced rather than appended: YouTube honours the first one it
 * sees, so a second parameter would hand back the old format under the new one's name.
 */
internal fun transcriptFormatUrl(trackUrl: String): String {
    val separator = trackUrl.indexOf('?')
    if (separator < 0) return "$trackUrl?fmt=json3"
    val path = trackUrl.substring(0, separator)
    val query =
        trackUrl
            .substring(separator + 1)
            .split('&')
            .filter { it.isNotEmpty() && !it.startsWith("fmt=") }
    return (listOf(path) + listOf((query + "fmt=json3").joinToString("&"))).joinToString("?")
}
