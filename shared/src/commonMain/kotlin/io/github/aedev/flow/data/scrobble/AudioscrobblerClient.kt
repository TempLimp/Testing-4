package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.parameters
import kotlinx.io.IOException
import okio.ByteString.Companion.encodeUtf8

/** An app's identity on a Last.fm-compatible service. */
data class AudioscrobblerKeys(
    val apiKey: String,
    val secret: String,
) {
    val isUsable: Boolean get() = apiKey.isNotBlank() && secret.isNotBlank()
}

/**
 * The Audioscrobbler 2.0 API that Last.fm and Libre.fm both speak: a mobile session from a user
 * name and password, now playing, and scrobbles in batches of up to [MAX_BATCH].
 */
class AudioscrobblerClient(
    private val baseUrl: String,
    private val http: () -> HttpClient,
) {
    suspend fun signIn(
        userName: String,
        password: String,
        keys: AudioscrobblerKeys,
    ): Result<ScrobbleAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = post(mapOf("method" to "auth.getMobileSession", "username" to userName, "password" to password), keys)
                body.errorCode()?.let { throw IOException(body.errorMessage() ?: "Error $it") }
                val session = body["session"]?.jsonObject ?: throw IOException("No session")
                ScrobbleAccount(
                    userName = session["name"]?.jsonPrimitive?.content ?: userName,
                    secret = session["key"]?.jsonPrimitive?.content ?: throw IOException("No session key"),
                )
            }
        }

    suspend fun nowPlaying(
        entry: ScrobbleEntry,
        account: ScrobbleAccount,
        keys: AudioscrobblerKeys,
    ): SendOutcome {
        val params =
            buildMap {
                put("method", "track.updateNowPlaying")
                put("artist", entry.artist)
                put("track", entry.title)
                if (entry.album.isNotBlank()) put("album", entry.album)
                if (entry.durationSec > 0) put("duration", entry.durationSec.toString())
                put("sk", account.secret)
            }
        return send(params, keys)
    }

    suspend fun scrobble(
        entries: List<ScrobbleEntry>,
        account: ScrobbleAccount,
        keys: AudioscrobblerKeys,
    ): SendOutcome {
        val params =
            buildMap {
                put("method", "track.scrobble")
                put("sk", account.secret)
                entries.take(MAX_BATCH).forEachIndexed { index, entry ->
                    put("artist[$index]", entry.artist)
                    put("track[$index]", entry.title)
                    put("timestamp[$index]", entry.timestampSec.toString())
                    if (entry.album.isNotBlank()) put("album[$index]", entry.album)
                    if (entry.durationSec > 0) put("duration[$index]", entry.durationSec.toString())
                }
            }
        return send(params, keys)
    }

    suspend fun setLoved(
        love: LoveEntry,
        account: ScrobbleAccount,
        keys: AudioscrobblerKeys,
    ): SendOutcome =
        send(
            mapOf(
                "method" to if (love.loved) "track.love" else "track.unlove",
                "artist" to love.artist,
                "track" to love.title,
                "sk" to account.secret,
            ),
            keys,
        )

    /** The user's most played artists over the past year, most played first. */
    suspend fun topArtists(
        userName: String,
        keys: AudioscrobblerKeys,
        limit: Int,
    ): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    URLBuilder(baseUrl)
                        .apply {
                            parameters.append("method", "user.getTopArtists")
                            parameters.append("user", userName)
                            parameters.append("period", "12month")
                            parameters.append("limit", limit.toString())
                            parameters.append("api_key", keys.apiKey)
                            parameters.append("format", "json")
                        }.build()
                val body = read(http().get(url))
                body.errorCode()?.let { throw IOException(body.errorMessage() ?: "Error $it") }
                body["topartists"]
                    ?.jsonObject
                    ?.get("artist")
                    ?.jsonArray
                    ?.mapNotNull {
                        it.jsonObject["name"]
                            ?.jsonPrimitive
                            ?.content
                            ?.takeIf(String::isNotBlank)
                    }.orEmpty()
            }
        }

    /** Songs Last.fm listeners play alongside [artist]'s [title], closest first. */
    suspend fun similarTracks(
        artist: String,
        title: String,
        keys: AudioscrobblerKeys,
        limit: Int,
    ): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    URLBuilder(baseUrl)
                        .apply {
                            parameters.append("method", "track.getSimilar")
                            parameters.append("artist", artist)
                            parameters.append("track", title)
                            parameters.append("autocorrect", "1")
                            parameters.append("limit", limit.toString())
                            parameters.append("api_key", keys.apiKey)
                            parameters.append("format", "json")
                        }.build()
                val body = read(http().get(url))
                body.errorCode()?.let { throw IOException(body.errorMessage() ?: "Error $it") }
                body["similartracks"]
                    ?.jsonObject
                    ?.get("track")
                    ?.jsonArray
                    ?.mapNotNull { element ->
                        val track = element.jsonObject
                        val name = track["name"]?.jsonPrimitive?.content
                        val by =
                            track["artist"]
                                ?.jsonObject
                                ?.get("name")
                                ?.jsonPrimitive
                                ?.content
                        if (name.isNullOrBlank() || by.isNullOrBlank()) null else by to name
                    }.orEmpty()
            }
        }

    private suspend fun send(
        params: Map<String, String>,
        keys: AudioscrobblerKeys,
    ): SendOutcome =
        withContext(Dispatchers.IO) {
            try {
                outcomeOf(post(params, keys).errorCode())
            } catch (e: IOException) {
                Log.w(TAG, "${params["method"]} failed: ${e.message}")
                SendOutcome.Retry
            }
        }

    private suspend fun post(
        params: Map<String, String>,
        keys: AudioscrobblerKeys,
    ): JsonObject {
        val signed = params + ("api_key" to keys.apiKey)
        val form =
            parameters {
                signed.forEach { (name, value) -> append(name, value) }
                append("api_sig", audioscrobblerSignature(signed, keys.secret))
                append("format", "json")
            }
        return read(http().submitForm(baseUrl, form))
    }

    private suspend fun read(response: HttpResponse): JsonObject {
        val text = response.bodyAsText()
        if (text.isBlank()) throw IOException("HTTP ${response.status.value}")
        return runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse { throw IOException("HTTP ${response.status.value}") }
    }

    private fun JsonObject.errorCode(): Int? = this["error"]?.jsonPrimitive?.int

    private fun JsonObject.errorMessage(): String? = this["message"]?.jsonPrimitive?.content

    companion object {
        const val MAX_BATCH = 50
        const val LASTFM_URL = "https://ws.audioscrobbler.com/2.0/"
        const val LIBREFM_URL = "https://libre.fm/2.0/"
        private const val TAG = "Audioscrobbler"

        /** Libre.fm registers no apps and takes any key; these only have to be 32 characters. */
        val LibreFmKeys = AudioscrobblerKeys(apiKey = "f10wf10wf10wf10wf10wf10wf10wf10w", secret = "f10wf10wf10wf10wf10wf10wf10wf10w")
    }
}

/** The documented api_sig: every parameter but format and callback, sorted, joined, then the secret, MD5'd. */
internal fun audioscrobblerSignature(
    params: Map<String, String>,
    secret: String,
): String {
    val base =
        params
            .filterKeys { it != "format" && it != "callback" }
            .entries
            .sortedBy { it.key }
            .joinToString("") { it.key + it.value } + secret
    return base.encodeUtf8().md5().hex()
}

/** Session and key errors sign the account out; outages and rate limits retry; anything else drops the batch. */
internal fun outcomeOf(errorCode: Int?): SendOutcome =
    when (errorCode) {
        null -> SendOutcome.Sent
        4, 9, 10, 26 -> SendOutcome.SignedOut
        8, 11, 16, 29 -> SendOutcome.Retry
        else -> SendOutcome.Sent
    }
