package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.abs

/**
 * LyricsProvider implementation wrapping the LrcLib API.
 * LrcLib provides standard LRC synced lyrics (line-level only, no word sync).
 */
class LrcLibLyricsProvider(
    private val client: () -> HttpClient,
) : LyricsProvider {
    override val name = "LrcLib"

    // Upstream's client.newBuilder() with these timeouts, set on each request here.
    private fun HttpRequestBuilder.lrcLibTimeouts() =
        timeout {
            connectTimeoutMillis = 3_000L
            socketTimeoutMillis = 4_000L
            requestTimeoutMillis = 5_000L
        }

    @Serializable
    private data class LrcLibResponse(
        val id: Long? = null,
        val plainLyrics: String?,
        val syncedLyrics: String?,
        val instrumental: Boolean?,
        val duration: Double? = null,
    )

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<List<LyricsEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val result =
                    tryFetch(artist, title, duration)
                        ?: trySearch(artist, title, duration)
                        ?: tryFetch("", title, duration)
                        ?: trySearch("", "$artist $title", duration)

                if (result != null) {
                    val syncedLrc = result.syncedLyrics
                    val plainLrc = result.plainLyrics

                    if (syncedLrc != null) {
                        val entries = LyricsUtils.parseLyrics(syncedLrc)
                        if (entries.isNotEmpty()) {
                            return@withContext Result.success(entries)
                        }
                    }

                    if (plainLrc != null) {
                        // Plain text: single entry with no sync
                        val entries = listOf(LyricsEntry(time = 0L, text = plainLrc))
                        return@withContext Result.success(entries)
                    }

                    Result.failure(Exception("No lyrics content in response"))
                } else {
                    Result.failure(Exception("No lyrics found on LrcLib"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun tryFetch(
        artist: String,
        title: String,
        duration: Int?,
    ): LrcLibResponse? {
        try {
            if (artist.isEmpty()) return null

            val encodedArtist = artist.encodeURLParameter(spaceToPlus = true)
            val encodedTitle = title.encodeURLParameter(spaceToPlus = true)

            val url =
                if (duration != null && duration > 0) {
                    "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle&duration=$duration"
                } else {
                    "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"
                }

            val response = client().get(url) { lrcLibTimeouts() }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                return LyricsJson.decodeFromString(LrcLibResponse.serializer(), body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("LrcLibProvider", "Fetch failed", e)
        }
        return null
    }

    private suspend fun trySearch(
        artist: String,
        title: String,
        duration: Int?,
    ): LrcLibResponse? {
        try {
            val q = if (artist.isNotEmpty()) "$artist $title" else title
            val encodedQ = q.encodeURLParameter(spaceToPlus = true)
            val url = "https://lrclib.net/api/search?q=$encodedQ"

            val response = client().get(url) { lrcLibTimeouts() }
            if (!response.status.isSuccess()) return null
            val body = response.bodyAsText()
            val results = LyricsJson.decodeFromString(ListSerializer(LrcLibResponse.serializer()), body)

            if (results.isNotEmpty()) {
                if (duration != null && duration > 0) {
                    val closestSynced =
                        results
                            .filter { it.syncedLyrics != null }
                            .minByOrNull { abs((it.duration ?: 0.0) - duration) }

                    if (closestSynced != null && abs((closestSynced.duration ?: 0.0) - duration) < 5) {
                        return closestSynced
                    }
                }

                return results.firstOrNull { it.syncedLyrics != null } ?: results[0]
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("LrcLibProvider", "Search failed", e)
        }
        return null
    }
}
