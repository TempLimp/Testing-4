package io.github.aedev.flow.player.media

import io.github.aedev.flow.network.ProxyAwareClient
import io.github.aedev.flow.player.engine.InvalidResponseCodeException
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.player.renderer.subtitle.DelayedSubtitleDecoder
import io.github.aedev.flow.player.renderer.subtitle.Srv3SubtitleParser
import io.github.aedev.flow.player.renderer.subtitle.SubripParser
import io.github.aedev.flow.player.renderer.subtitle.Subtitle
import io.github.aedev.flow.player.renderer.subtitle.SubtitleParser
import io.github.aedev.flow.player.renderer.subtitle.WebvttParser
import io.github.aedev.flow.player.stream.CaptionFormat
import io.github.aedev.flow.player.stream.ResolvedCaption
import io.github.aedev.flow.player.subtitle.SubtitleDelay
import io.github.aedev.flow.utils.Log
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.time.Clock

/**
 * Fetches and decodes the caption track on screen, where upstream merges every caption into the media source as a
 * sidecar track for Media3 to load when its text renderer selects it. The retries are upstream's
 * SubtitleLoadErrorHandlingPolicy, and a fetch that has given up is reported as upstream's reporter reports it.
 */
class CaptionLoader(
    private val delayOffset: SubtitleDelay,
    private val client: ProxyAwareClient = ProxyAwareClient(),
) {
    /** Thrown for a caption file in a format the player has no parser for (SSA and TTML, from device files). */
    class UnsupportedCaptionFormat(
        format: CaptionFormat,
    ) : Exception("no parser for ${format.mimeType}")

    /**
     * The caption [caption] decoded and shifted by the user's offset, or null once its fetch has given up, after
     * [onGaveUp] heard why.
     */
    suspend fun load(
        caption: ResolvedCaption,
        label: String,
        onGaveUp: () -> Unit,
    ): Subtitle? {
        val maxAttempts = if (caption.isTranslated) TRANSLATED_MAX_ATTEMPTS else MAX_ATTEMPTS
        var errorCount = 0
        while (true) {
            try {
                return DelayedSubtitleDecoder(parserFor(caption.format), delayOffset).decode(fetch(caption.url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorCount++
                val retryDelay = retryDelayMsFor(e, errorCount, caption.isTranslated)
                if (retryDelay == null || errorCount >= maxAttempts) {
                    Log.w(TAG, "Subtitle '$label' gave up after retries: ${SubtitleLoadDiagnostics.describe(caption.url, e)}")
                    onGaveUp()
                    return null
                }
                Log.d(TAG, "Subtitle '$label' load failed (status=${SubtitleLoadDiagnostics.statusOf(e)}), retrying")
                delay(retryDelay)
            }
        }
    }

    private fun parserFor(format: CaptionFormat): SubtitleParser =
        when (format) {
            CaptionFormat.SRV3 -> Srv3SubtitleParser()
            CaptionFormat.VTT -> WebvttParser()
            CaptionFormat.SRT -> SubripParser()
            else -> throw UnsupportedCaptionFormat(format)
        }

    private suspend fun fetch(url: String): ByteArray {
        if (url.startsWith("file://") || url.startsWith("/")) {
            val path = Path(url.removePrefix("file://"))
            return SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        }
        val response = client.get(Url(url)).get(url)
        if (response.status.value !in 200..299) throw InvalidResponseCodeException(response.status.value, url)
        return response.readRawBytes()
    }

    /**
     * Upstream's SubtitleLoadErrorHandlingPolicy over Media3's default policy: an HTTP 429 or 5xx backs off, any other
     * status gives up at once; a caption that cannot be parsed gives up; other failures retry after (n - 1) s, at most 5 s.
     */
    internal fun retryDelayMsFor(
        error: Throwable,
        errorCount: Int,
        isTranslated: Boolean,
    ): Long? {
        if (error is UnsupportedCaptionFormat) return null
        val status = (error as? InvalidResponseCodeException)?.responseCode
            ?: return ((errorCount - 1) * 1_000L).coerceAtMost(5_000L)
        val isTransient = status == HTTP_TOO_MANY_REQUESTS || status >= HTTP_SERVER_ERROR
        if (!isTransient) return null
        val exponent = (errorCount - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        val initial = if (isTranslated) TRANSLATED_INITIAL_BACKOFF_MS else INITIAL_BACKOFF_MS
        val ceiling = if (isTranslated) TRANSLATED_MAX_BACKOFF_MS else MAX_BACKOFF_MS
        return (initial shl exponent).coerceAtMost(ceiling)
    }

    private companion object {
        const val TAG = "MediaLoader"
        const val MAX_ATTEMPTS = 6
        const val INITIAL_BACKOFF_MS = 500L
        const val MAX_BACKOFF_MS = 8_000L

        // A tlang fetch is not rate-limited, it is refused by Google's abuse interstitial: no
        // Retry-After, and it hardens against the IP as attempts continue. Fewer, slower tries
        // give the block time to lapse while the caller falls back to the source track.
        const val TRANSLATED_MAX_ATTEMPTS = 3
        const val TRANSLATED_INITIAL_BACKOFF_MS = 2_000L
        const val TRANSLATED_MAX_BACKOFF_MS = 20_000L
        const val MAX_BACKOFF_EXPONENT = 4
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
    }
}

private val FMT_VALUE = Regex("[^A-Za-z0-9]")

/**
 * The facts that tell a failed caption fetch's causes apart (which client minted it, which format
 * was asked for, whether it was a translation, whether it had expired), and nothing that could replay
 * the request: no path, signature, key, session or video id.
 */
internal object SubtitleLoadDiagnostics {
    fun describe(
        url: String?,
        error: Throwable,
        nowSeconds: Long = Clock.System.now().toEpochMilliseconds() / 1000,
    ): String {
        val remaining = StreamDenialClassifier.expiresInSeconds(url, nowSeconds)
        return listOfNotNull(
            "host=${hostOf(url)}",
            "c=${StreamDenialClassifier.clientOf(url) ?: "none"}",
            "fmt=${fmtValues(url).joinToString(",").ifEmpty { "none" }}",
            "tlang=${presence(url, "tlang")}",
            "pot=${presence(url, "pot")}",
            if (remaining == null) "expire=absent" else "expiresIn=${remaining}s",
            "status=${statusOf(error) ?: "none"}",
            "error=${error::class.simpleName}",
            error.cause?.let { "cause=${it::class.simpleName}" },
        ).joinToString(" ")
    }

    fun statusOf(error: Throwable): Int? = (error as? InvalidResponseCodeException)?.responseCode

    private fun hostOf(url: String?): String {
        if (url.isNullOrBlank()) return "none"
        val scheme = url.substringBefore("://", missingDelimiterValue = "")
        if (!scheme.equals("http", true) && !scheme.equals("https", true)) return "local"
        return url
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringAfter('@')
            .ifEmpty { "none" }
    }

    private fun presence(
        url: String?,
        name: String,
    ): String = if (StreamDenialClassifier.queryParam(url, name).isNullOrEmpty()) "no" else "yes"

    /** Every `fmt`, in order: the endpoint honours the first, so a duplicate is itself the finding. */
    private fun fmtValues(url: String?): List<String> {
        val query = url?.substringAfter('?', missingDelimiterValue = "")?.substringBefore('#').orEmpty()
        return query
            .split('&')
            .filter { it.startsWith("fmt=") }
            .map { it.removePrefix("fmt=").replace(FMT_VALUE, "").take(8) }
    }
}
