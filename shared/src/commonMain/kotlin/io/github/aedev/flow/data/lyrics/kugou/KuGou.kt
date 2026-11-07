// ==================================================================================================
// This implementation was based on metrolist's (https://github.com/MetrolistGroup/Metrolist)
// ==================================================================================================

package io.github.aedev.flow.data.lyrics.kugou

import io.github.aedev.flow.data.lyrics.kugou.models.DownloadLyricsResponse
import io.github.aedev.flow.data.lyrics.kugou.models.Keyword
import io.github.aedev.flow.data.lyrics.kugou.models.SearchLyricsResponse
import io.github.aedev.flow.data.lyrics.kugou.models.SearchSongResponse
import io.github.aedev.flow.utils.Log
import io.github.aedev.flow.data.lyrics.LyricsJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.math.min

class KuGou(
    private val client: () -> HttpClient,
) {
    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String> =
        runCatching {
            val keyword = generateKeyword(title, artist, album)
            val candidate =
                getLyricsCandidate(keyword, duration)
                    ?: throw IllegalStateException("No lyrics candidate")
            val downloaded = downloadLyrics(candidate.id, candidate.accesskey)
            val decoded = AndroidBase64.decode(downloaded.content).decodeToString()
            decoded.normalize()
        }

    private suspend fun getLyricsCandidate(
        keyword: Keyword,
        duration: Int,
    ): SearchLyricsResponse.Candidate? {
        val songs = searchSongs(keyword)
        for (song in songs.data.info) {
            if (duration <= 0 || abs(song.duration - duration) <= DURATION_TOLERANCE) {
                val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull()
                if (candidate != null) return candidate
            }
        }
        return searchLyricsByKeyword(keyword, duration).candidates.firstOrNull()
    }

    private suspend fun searchSongs(keyword: Keyword): SearchSongResponse {
        val searchQuery =
            buildString {
                append(keyword.title)
                append(" - ")
                append(keyword.artist)
                if (!keyword.album.isNullOrBlank()) {
                    append(" ")
                    append(keyword.album)
                }
            }
        val url =
            buildString {
                append("https://mobileservice.kugou.com/api/v3/search/song")
                append("?version=9108&plat=0&pagesize=$PAGE_SIZE&showtype=0")
                append("&keyword=${searchQuery.encodeURLParameter(spaceToPlus = true)}")
            }
        return executeGet(url, SearchSongResponse.serializer()) ?: SearchSongResponse()
    }

    private suspend fun searchLyricsByKeyword(
        keyword: Keyword,
        duration: Int,
    ): SearchLyricsResponse {
        val searchQuery =
            buildString {
                append(keyword.title)
                append(" - ")
                append(keyword.artist)
                if (!keyword.album.isNullOrBlank()) {
                    append(" ")
                    append(keyword.album)
                }
            }
        val url =
            buildString {
                append("https://lyrics.kugou.com/search")
                append("?ver=1&man=yes&client=pc")
                if (duration > 0) append("&duration=${duration * 1000}")
                append("&keyword=${searchQuery.encodeURLParameter(spaceToPlus = true)}")
            }
        return executeGet(url, SearchLyricsResponse.serializer()) ?: SearchLyricsResponse()
    }

    private suspend fun searchLyricsByHash(hash: String): SearchLyricsResponse {
        val url = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$hash"
        return executeGet(url, SearchLyricsResponse.serializer()) ?: SearchLyricsResponse()
    }

    private suspend fun downloadLyrics(
        id: Long,
        accessKey: String,
    ): DownloadLyricsResponse {
        val url = "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=$id&accesskey=$accessKey"
        return executeGet(url, DownloadLyricsResponse.serializer()) ?: DownloadLyricsResponse()
    }

    private suspend fun <T> executeGet(
        url: String,
        deserializer: DeserializationStrategy<T>,
    ): T? {
        return try {
            val response = client().get(url)
            if (!response.status.isSuccess()) {
                Log.w(TAG, "HTTP ${response.status.value} for $url")
                return null
            }
            val body = response.bodyAsText()
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                Log.w(TAG, "Non-JSON response (${trimmed.take(80)}...) for $url")
                return null
            }
            LyricsJson.decodeFromString(deserializer, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}")
            null
        }
    }

    private fun normalizeTitle(title: String) =
        title
            .replace("\\(.*\\)".toRegex(), "")
            .replace("（.*）".toRegex(), "")
            .replace("「.*」".toRegex(), "")
            .replace("『.*』".toRegex(), "")
            .replace("<.*>".toRegex(), "")
            .replace("《.*》".toRegex(), "")
            .replace("〈.*〉".toRegex(), "")
            .replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(artist: String) =
        artist
            .replace(", ", "、")
            .replace(" & ", "、")
            .replace(".", "")
            .replace("和", "、")
            .replace("\\(.*\\)".toRegex(), "")
            .replace("（.*）".toRegex(), "")

    fun generateKeyword(
        title: String,
        artist: String,
        album: String? = null,
    ) = Keyword(normalizeTitle(title), normalizeArtist(artist), album)

    private fun String.normalize(): String =
        lines()
            .filter { line -> line.matches(ACCEPTED_REGEX) }
            .let { lines ->
                var headCutLine = 0
                for (i in min(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                    if (lines[i].matches(BANNED_REGEX)) {
                        headCutLine = i + 1
                        break
                    }
                }
                val filteredLines = lines.drop(headCutLine)

                var tailCutLine = 0
                for (i in min(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                    if (lines[lines.lastIndex - i].matches(BANNED_REGEX)) {
                        tailCutLine = i + 1
                        break
                    }
                }
                filteredLines.dropLast(tailCutLine).joinToString("\n")
            }

    private companion object {
        // android.util.Base64.DEFAULT: skips line breaks and other characters outside the alphabet, padding optional.
        val AndroidBase64 = Base64.Mime.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
        const val TAG = "KuGou"
        const val PAGE_SIZE = 8
        const val HEAD_CUT_LIMIT = 30
        const val DURATION_TOLERANCE = 8

        @Suppress("RegExpRedundantEscape")
        val ACCEPTED_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*".toRegex()
        val BANNED_REGEX = ".+].+[:：].+".toRegex()
    }
}
