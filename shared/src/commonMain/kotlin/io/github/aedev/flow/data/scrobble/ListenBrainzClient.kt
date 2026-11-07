package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.io.IOException

/** ListenBrainz's submit API, signed in with the user token from their settings page. */
class ListenBrainzClient(
    private val http: () -> HttpClient,
) {
    suspend fun signIn(token: String): Result<ScrobbleAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body =
                    Json
                        .parseToJsonElement(http().get("$BASE_URL/validate-token") { header(AUTH, "Token $token") }.bodyAsText())
                        .jsonObject
                if (body["valid"]?.jsonPrimitive?.boolean != true) throw IOException(body.message() ?: "Invalid token")
                ScrobbleAccount(userName = body["user_name"]?.jsonPrimitive?.content.orEmpty(), secret = token)
            }
        }

    /** The user's most listened artists over the past year; empty when ListenBrainz has no stats yet. */
    suspend fun topArtists(
        account: ScrobbleAccount,
        limit: Int,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    URLBuilder(BASE_URL)
                        .apply {
                            appendPathSegments("stats", "user", account.userName, "artists")
                            parameters.append("range", "year")
                            parameters.append("count", limit.toString())
                        }.build()
                val response = http().get(url) { header(AUTH, "Token ${account.secret}") }
                if (response.status.value == NO_CONTENT) return@runCatching emptyList()
                if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
                Json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["payload"]
                    ?.jsonObject
                    ?.get("artists")
                    ?.jsonArray
                    ?.mapNotNull {
                        it.jsonObject["artist_name"]
                            ?.jsonPrimitive
                            ?.content
                            ?.takeIf(String::isNotBlank)
                    }.orEmpty()
            }
        }

    suspend fun nowPlaying(
        entry: ScrobbleEntry,
        account: ScrobbleAccount,
    ): SendOutcome = submit(PLAYING_NOW, listOf(entry), account)

    suspend fun scrobble(
        entries: List<ScrobbleEntry>,
        account: ScrobbleAccount,
    ): SendOutcome = submit(if (entries.size == 1) SINGLE else IMPORT, entries.take(MAX_BATCH), account)

    private suspend fun submit(
        type: String,
        entries: List<ScrobbleEntry>,
        account: ScrobbleAccount,
    ): SendOutcome =
        withContext(Dispatchers.IO) {
            val payload = TextContent(listensJson(type, entries).toString(), JSON)
            try {
                val response =
                    http().post("$BASE_URL/submit-listens") {
                        header(AUTH, "Token ${account.secret}")
                        setBody(payload)
                    }
                val code = response.status.value
                when {
                    response.status.isSuccess() -> SendOutcome.Sent
                    code == UNAUTHORIZED -> SendOutcome.SignedOut
                    code == TOO_MANY || code >= SERVER_ERROR -> SendOutcome.Retry
                    else -> SendOutcome.Sent
                }
            } catch (e: IOException) {
                Log.w(TAG, "submit failed: ${e.message}")
                SendOutcome.Retry
            }
        }

    private fun JsonObject.message(): String? = this["message"]?.jsonPrimitive?.content

    companion object {
        const val MAX_BATCH = 100
        private const val BASE_URL = "https://api.listenbrainz.org/1"
        private const val AUTH = "Authorization"
        private const val PLAYING_NOW = "playing_now"
        private const val SINGLE = "single"
        private const val IMPORT = "import"
        private const val NO_CONTENT = 204
        private const val UNAUTHORIZED = 401
        private const val TOO_MANY = 429
        private const val SERVER_ERROR = 500
        private const val TAG = "ListenBrainz"
        private val JSON = ContentType.Application.Json.withCharset(Charsets.UTF_8)
    }
}

/** The submit-listens body; a now-playing listen carries no timestamp. */
internal fun listensJson(
    type: String,
    entries: List<ScrobbleEntry>,
): JsonObject =
    buildJsonObject {
        put("listen_type", type)
        put(
            "payload",
            buildJsonArray {
                entries.forEach { entry ->
                    add(
                        buildJsonObject {
                            if (type != "playing_now") put("listened_at", entry.timestampSec)
                            put(
                                "track_metadata",
                                buildJsonObject {
                                    put("artist_name", entry.artist)
                                    put("track_name", entry.title)
                                    if (entry.album.isNotBlank()) put("release_name", entry.album)
                                    put(
                                        "additional_info",
                                        buildJsonObject {
                                            if (entry.durationSec > 0) put("duration_ms", entry.durationSec * 1000L)
                                            put("submission_client", "Flow")
                                            put("submission_client_version", BuildConfig.VERSION_NAME)
                                            if (entry.fromYouTube) put("music_service", "music.youtube.com")
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            },
        )
    }
