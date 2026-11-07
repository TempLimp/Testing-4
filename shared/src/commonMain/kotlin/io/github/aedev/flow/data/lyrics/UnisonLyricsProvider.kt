package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess

/**
 * Unison: lyrics people submit themselves, so its catalogue is small and uneven, but it sometimes
 * has a song the licensed sources lack. Each entry says whether it is Apple-style TTML, LRC (word
 * stamps included) or plain text, and that picks the parser.
 */
class UnisonLyricsProvider(
    private val client: () -> HttpClient,
) : LyricsProvider {
    override val name = NAME

    @Serializable
    private data class Response(
        val success: Boolean? = null,
        val data: Entry? = null,
    )

    @Serializable
    private data class Entry(
        val lyrics: String? = null,
        val format: String? = null,
        val syncType: String? = null,
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
                require(title.isNotBlank() && artist.isNotBlank()) { "Unison needs a title and an artist" }
                val url =
                    URLBuilder(LYRICS_URL)
                        .apply {
                            parameters.append("song", title)
                            parameters.append("artist", artist)
                            if (!album.isNullOrBlank()) parameters.append("album", album)
                            if (duration > 0) parameters.append("duration", duration.toString())
                        }.build()
                val response =
                    client().get(url) {
                        header("User-Agent", "Flow/${BuildConfig.VERSION_NAME}")
                        header("Accept", "application/json")
                    }
                val body = if (response.status.isSuccess()) response.bodyAsText() else null
                body?.let(::parse)?.ifEmpty { null } ?: error("No Unison lyrics")
            }
        }

    internal companion object {
        const val NAME = "Unison"
        private const val LYRICS_URL = "https://unison.boidu.dev/lyrics"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): List<LyricsEntry> {
            val response = json.decodeFromString<Response>(body)
            val entry = response.data?.takeIf { response.success == true } ?: return emptyList()
            val text = entry.lyrics?.takeIf { it.isNotBlank() } ?: return emptyList()
            return when {
                entry.format.equals("ttml", ignoreCase = true) -> TTMLParser.parseTTMLToLyricsEntries(text)
                entry.syncType.equals("plain", ignoreCase = true) -> listOf(LyricsEntry(time = 0L, text = text.trim()))
                else -> LyricsUtils.parseLyrics(text)
            }
        }
    }
}
