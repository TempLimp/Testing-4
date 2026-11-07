package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * lrc.red: Apple Music word-synced TTML archived by ISRC. Its search names the recording behind
 * each hit, and the hit is checked against the request with [LyricsMatching] before its document
 * is fetched. `lyrics-api.binimum.org`, which LyricsPlus used to call first, now redirects here.
 */
class LrcRedLyricsProvider(
    private val client: () -> HttpClient,
) : LyricsProvider {
    override val name = NAME

    @Serializable
    internal data class Hit(
        @SerialName("track_name") val trackName: String? = null,
        @SerialName("artist_name") val artistName: String? = null,
        val duration: Int? = null,
        @SerialName("timing_type") val timingType: String? = null,
        val lyricsUrl: String? = null,
    )

    @Serializable
    private data class SearchResponse(
        val results: List<Hit> = emptyList(),
    )

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<List<LyricsEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = LyricsMatching.Song(title, artist, duration)
                val shaped =
                    buildList {
                        add("track" to title)
                        if (artist.isNotBlank()) add("artist" to artist)
                        if (!album.isNullOrBlank()) add("album" to album)
                        if (duration > 0) add("duration" to duration.toString())
                    }
                val hit =
                    pick(search(shaped), request)
                        ?: pick(search(listOf("q" to if (artist.isBlank()) title else "$artist - $title")), request)
                        ?: error("No lrc.red match")
                val document = get(Url(hit.lyricsUrl!!)) ?: error("lrc.red document unavailable")
                TTMLParser.parseTTMLToLyricsEntries(document).ifEmpty { error("lrc.red document has no lines") }
            }
        }

    private suspend fun search(params: List<Pair<String, String>>): List<Hit> {
        val url =
            URLBuilder(SEARCH_URL)
                .apply { params.forEach { (key, value) -> parameters.append(key, value) } }
                .build()
        return get(url)?.let(::parseSearch).orEmpty()
    }

    private suspend fun get(url: Url): String? {
        val response = client().get(url) { header("User-Agent", "Flow/${BuildConfig.VERSION_NAME}") }
        return if (response.status.isSuccess()) response.bodyAsText().takeIf { it.isNotBlank() } else null
    }

    internal companion object {
        const val NAME = "LrcRed"
        private const val SEARCH_URL = "https://lrc.red/api/v1"
        private val json = Json { ignoreUnknownKeys = true }

        fun parseSearch(body: String): List<Hit> = json.decodeFromString<SearchResponse>(body).results

        /** The same recording, word-timed files first; documents are only fetched over https. */
        fun pick(
            hits: List<Hit>,
            request: LyricsMatching.Song,
        ): Hit? =
            LyricsMatching.best(
                hits.filter { hit -> hit.lyricsUrl?.let(::parseUrl)?.protocol == URLProtocol.HTTPS },
                request,
                describe = { LyricsMatching.Song(it.trackName.orEmpty(), it.artistName.orEmpty(), it.duration ?: 0) },
                preferred = { it.timingType.equals("word", ignoreCase = true) },
            )
    }
}
