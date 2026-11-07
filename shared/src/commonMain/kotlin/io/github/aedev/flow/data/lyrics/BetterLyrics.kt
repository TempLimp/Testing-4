// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Service for fetching lyrics from the BetterLyrics API (lyrics-api.boidu.dev).
 * Returns structured LyricsEntry objects with word-level timestamps from TTML.
 */
class BetterLyrics(
    private val client: () -> HttpClient,
) {
    @Serializable
    private data class TTMLResponse(
        @SerialName("ttml") val ttml: String? = null,
    )

    /**
     * Fetch lyrics by title, artist, and duration.
     * Uses TTMLParser.parseTTMLToLines() directly to preserve word-level data.
     *
     * @param title Song title
     * @param artist Artist name
     * @param duration Duration in seconds
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<List<LyricsEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val encodedTitle = title.encodeURLParameter(spaceToPlus = true)
                val encodedArtist = artist.encodeURLParameter(spaceToPlus = true)

                val url =
                    buildString {
                        append("$BASE_URL/getLyrics")
                        append("?s=$encodedTitle")
                        append("&a=$encodedArtist")
                        if (!album.isNullOrBlank()) {
                            append("&al=${album.encodeURLParameter(spaceToPlus = true)}")
                        }
                        if (duration > 0) {
                            append("&d=$duration")
                        }
                    }

                Log.d(TAG, "Fetching: $url")

                val response =
                    client().get(url) {
                        header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
                        )
                        header("Accept", "application/json")
                    }

                if (!response.status.isSuccess()) {
                    // Songs nobody has looked up yet need an API key (401). That is a miss for
                    // this song, not a refused client, so it must not bench the provider.
                    val uncached = response.status.value == 401 && response.bodyAsText().contains("Uncached", ignoreCase = true)
                    val reason = if (uncached) "no cached lyrics for this song" else "API returned ${response.status.value}"
                    return@withContext Result.failure(Exception("BetterLyrics $reason"))
                }

                val body = response.bodyAsText()
                if (body.isBlank()) {
                    return@withContext Result.failure(Exception("Empty response from BetterLyrics"))
                }

                val ttml =
                    try {
                        val ttmlResponse = LyricsJson.decodeFromString(TTMLResponse.serializer(), body)
                        ttmlResponse.ttml
                    } catch (e: Exception) {
                        Log.d(TAG, "JSON parse failed, trying raw TTML")
                        if (body.contains("<tt") || body.contains("<p ")) body else null
                    }

                if (ttml.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("No TTML in BetterLyrics response"))
                }

                val ttmlLines =
                    try {
                        TTMLParser.parseTTMLToLyricsEntries(ttml)
                    } catch (e: Exception) {
                        Log.e(TAG, "TTML parse failed", e)
                        emptyList()
                    }

                if (ttmlLines.isNotEmpty()) {
                    Log.d(TAG, "Parsed ${ttmlLines.size} lines, ${ttmlLines.count { it.words != null }} with word sync")
                    return@withContext Result.success(ttmlLines)
                }

                Result.failure(Exception("Failed to parse BetterLyrics TTML"))
            } catch (e: Exception) {
                Log.e(TAG, "BetterLyrics fetch failed", e)
                Result.failure(e)
            }
        }

    private companion object {
        const val TAG = "BetterLyrics"
        const val BASE_URL = "https://lyrics-api.boidu.dev"
    }
}
