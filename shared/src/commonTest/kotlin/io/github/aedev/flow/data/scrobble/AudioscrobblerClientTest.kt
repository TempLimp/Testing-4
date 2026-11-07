package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.testing.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class AudioscrobblerClientTest {
    private val keys = AudioscrobblerKeys("key", "secret")
    private val sent = mutableListOf<Map<String, String>>()

    // Upstream's OkHttp interceptor: records the form, or the query of a GET, and answers [reply].
    private fun client(reply: String): AudioscrobblerClient {
        val http =
            HttpClient(
                MockEngine { request ->
                    val form = request.body as? FormDataContent
                    sent += form?.formData?.entries()?.associate { (name, values) -> name to values.single() }
                        ?: request.url.parameters.names().associateWith { request.url.parameters[it].orEmpty() }
                    respond(reply, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        return AudioscrobblerClient("https://example.invalid/2.0/") { http }
    }

    @Test
    fun theSignatureFollowsTheDocumentedRecipe() {
        val params =
            mapOf("username" to "u", "method" to "auth.getMobileSession", "password" to "p", "api_key" to "xxx", "format" to "json")

        assertThat(audioscrobblerSignature(params, "s")).isEqualTo("f02a24fa543d12eeed6c58ed55b6c83a")
    }

    @Test
    fun signInReturnsTheSessionAndSignsTheRequest() =
        runBlocking {
            val account =
                client(
                    """{"session":{"name":"Listener","key":"SK","subscriber":0}}""",
                ).signIn("listener", "pw", keys).getOrThrow()

            assertThat(account).isEqualTo(ScrobbleAccount("Listener", "SK"))
            val form = sent.single()
            assertThat(form["method"]).isEqualTo("auth.getMobileSession")
            assertThat(form["api_sig"]).isEqualTo(audioscrobblerSignature(form - "api_sig" - "format", "secret"))
        }

    @Test
    fun aRejectedSignInCarriesTheServicesMessage() =
        runBlocking {
            val result = client("""{"error":4,"message":"Invalid username or password"}""").signIn("u", "bad", keys)

            assertThat(result.exceptionOrNull()?.message).isEqualTo("Invalid username or password")
        }

    @Test
    fun aBatchSendsIndexedFieldsAndAnExpiredSessionSignsOut() =
        runBlocking {
            val entries = listOf(ScrobbleEntry("A", "One", timestampSec = 10), ScrobbleEntry("B", "Two", "Record", 180, 20))
            val outcome = client("""{"error":9,"message":"Invalid session key"}""").scrobble(entries, ScrobbleAccount("u", "SK"), keys)

            assertThat(outcome).isEqualTo(SendOutcome.SignedOut)
            val form = sent.single()
            assertThat(form["track[1]"]).isEqualTo("Two")
            assertThat(form["album[1]"]).isEqualTo("Record")
            assertThat(form).doesNotContainKey("album[0]")
            assertThat(form["timestamp[0]"]).isEqualTo("10")
        }

    @Test
    fun outagesRetryAndOtherErrorsDropTheBatch() {
        assertThat(outcomeOf(null)).isEqualTo(SendOutcome.Sent)
        assertThat(outcomeOf(11)).isEqualTo(SendOutcome.Retry)
        assertThat(outcomeOf(29)).isEqualTo(SendOutcome.Retry)
        assertThat(outcomeOf(6)).isEqualTo(SendOutcome.Sent)
    }

    @Test
    fun aLikeBecomesTrackLoveAndAnUnlikeTrackUnlove() =
        runBlocking {
            val client = client("{}")

            assertThat(client.setLoved(LoveEntry("A", "Song", loved = true), ScrobbleAccount("u", "SK"), keys)).isEqualTo(SendOutcome.Sent)
            client.setLoved(LoveEntry("A", "Song", loved = false), ScrobbleAccount("u", "SK"), keys)

            assertThat(sent.map { it["method"] }).containsExactly("track.love", "track.unlove").inOrder()
            assertThat(sent.first()["sk"]).isEqualTo("SK")
        }

    @Test
    fun topArtistsAreReadByNameInPlayOrder() =
        runBlocking {
            val reply = """{"topartists":{"artist":[{"name":"First","playcount":"90"},{"name":"Second","playcount":"40"}]}}"""

            val artists = client(reply).topArtists("listener", keys, limit = 20).getOrThrow()

            assertThat(artists).containsExactly("First", "Second").inOrder()
            assertThat(sent.single()["method"]).isEqualTo("user.getTopArtists")
            assertThat(sent.single()["period"]).isEqualTo("12month")
        }

    @Test
    fun similarTracksAreReadAsArtistAndTitlePairs() =
        runBlocking {
            val reply = """{"similartracks":{"track":[{"name":"Near","artist":{"name":"Other"}},{"name":"","artist":{"name":"Skip"}}]}}"""

            val similar = client(reply).similarTracks("Artist", "Song", keys, limit = 15).getOrThrow()

            assertThat(similar).containsExactly("Other" to "Near")
            assertThat(sent.single()["method"]).isEqualTo("track.getSimilar")
            assertThat(sent.single()["autocorrect"]).isEqualTo("1")
        }
}
