package io.github.aedev.flow.data.repository

import io.github.aedev.flow.testing.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test

// Upstream answers through an OkHttp interceptor; the port's client is Ktor, so a MockEngine answers instead.
class DeArrowRepositoryTest {
    private val requests = AtomicInt(0)
    private var nowMs = 1_000L
    private var askedVideoId: String? = null

    private fun repository(
        gate: CompletableDeferred<Unit>? = null,
        answer: () -> Pair<Int, String>,
    ): DeArrowRepository {
        val client =
            HttpClient(
                MockEngine { request ->
                    requests.incrementAndFetch()
                    askedVideoId = request.url.parameters["videoID"]
                    gate?.let { withTimeoutOrNull(5_000) { it.await() } }
                    val (code, body) = answer()
                    if (code < 0) throw IOException("offline")
                    respond(body, HttpStatusCode.fromValue(code), headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        return DeArrowRepository({ client }, Dispatchers.IO) { nowMs }
    }

    @Test
    fun a404ForNoSubmissionsIsAnAnswerWithTheRandomTime() =
        runBlocking {
            val deArrow = repository { 404 to """{"titles":[],"thumbnails":[],"casualVotes":[],"randomTime":0.4,"videoDuration":250.5}""" }

            val content = deArrow.branding("abc123def45")

            assertThat(content?.randomTime).isEqualTo(0.4)
            assertThat(content?.videoDuration).isEqualTo(250.5)
            assertThat(askedVideoId).isEqualTo("abc123def45")
        }

    @Test
    fun a404IsCachedLikeAnyAnswer() =
        runBlocking {
            val deArrow = repository { 404 to """{"titles":[],"thumbnails":[],"randomTime":0.4,"videoDuration":null}""" }

            repeat(3) { deArrow.branding("abc123def45") }

            assertThat(requests.load()).isEqualTo(1)
            assertThat(deArrow.cached("abc123def45")?.randomTime).isEqualTo(0.4)
        }

    @Test
    fun cardsAskingAtTheSameTimeShareOneRequest() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val deArrow =
                repository(gate) {
                    200 to
                        """{"titles":[{"title":"t","votes":1,"locked":false,"original":false}],"thumbnails":[]}"""
                }

            val asks = (1..5).map { async(Dispatchers.IO) { deArrow.branding("abc123def45") } }
            delay(200)
            gate.complete(Unit)

            assertThat(asks.awaitAll().map { it?.titles?.single()?.title }).containsExactly("t", "t", "t", "t", "t")
            assertThat(requests.load()).isEqualTo(1)
        }

    @Test
    fun aNetworkFailureIsAskedAgainNextTime() =
        runBlocking {
            val deArrow = repository { -1 to "" }

            assertThat(deArrow.branding("abc123def45")).isNull()
            assertThat(deArrow.branding("abc123def45")).isNull()
            assertThat(requests.load()).isEqualTo(2)
        }

    @Test
    fun aServerErrorIsNotCached() =
        runBlocking {
            val deArrow = repository { 503 to "" }

            deArrow.branding("abc123def45")
            deArrow.branding("abc123def45")

            assertThat(requests.load()).isEqualTo(2)
        }

    @Test
    fun anAnswerExpiresAfterTheTtl() =
        runBlocking {
            val deArrow = repository { 200 to """{"titles":[],"thumbnails":[]}""" }

            deArrow.branding("abc123def45")
            nowMs += DeArrowRepository.TTL_MS + 1
            assertThat(deArrow.cached("abc123def45")).isNull()
            deArrow.branding("abc123def45")

            assertThat(requests.load()).isEqualTo(2)
        }

    @Test
    fun unknownFieldsAreIgnoredAndBrokenBodiesParseToNothing() {
        assertThat(DeArrowRepository.parse("""{"titles":[],"thumbnails":[],"extra":{"a":1}}""")).isNotNull()
        assertThat(DeArrowRepository.parse("<html>")).isNull()
    }
}
