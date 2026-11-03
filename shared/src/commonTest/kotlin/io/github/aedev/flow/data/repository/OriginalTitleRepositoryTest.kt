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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.io.IOException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test

// Upstream answers through an OkHttp interceptor; the port's client is Ktor, so a MockEngine answers instead.
class OriginalTitleRepositoryTest {
    private val requests = AtomicInt(0)

    private fun repository(
        gate: CompletableDeferred<Unit>? = null,
        answer: (String) -> Pair<Int, String>,
    ): OriginalTitleRepository {
        val client =
            HttpClient(
                MockEngine { request ->
                    requests.incrementAndFetch()
                    gate?.let { withTimeoutOrNull(5_000) { it.await() } }
                    val videoUrl = request.url.parameters["url"].orEmpty()
                    val (code, body) = answer(videoUrl)
                    if (code < 0) throw IOException("offline")
                    respond(body, HttpStatusCode.fromValue(code), headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        return OriginalTitleRepository({ client }, Dispatchers.IO)
    }

    @Test
    fun theOEmbedTitleIsTheOriginalOne() =
        runBlocking {
            val titles = repository { 200 to """{"title":"XIAOMI 17T - ПРОСТОЙ, ПРИЯТНЫЙ ФЛАГМАН!","author_name":"DimaViper"}""" }

            assertThat(titles.originalTitle("2BAEyzqxAME")).isEqualTo("XIAOMI 17T - ПРОСТОЙ, ПРИЯТНЫЙ ФЛАГМАН!")
            assertThat(titles.cached("2BAEyzqxAME")).isEqualTo("XIAOMI 17T - ПРОСТОЙ, ПРИЯТНЫЙ ФЛАГМАН!")
        }

    @Test
    fun itAsksForTheWatchUrlOfTheVideo() =
        runBlocking {
            var asked = ""
            val titles = repository { url -> (200 to """{"title":"t"}""").also { asked = url } }

            titles.originalTitle("abc123def45")

            assertThat(asked).isEqualTo("https://www.youtube.com/watch?v=abc123def45")
        }

    @Test
    fun aPageWatchingTheCacheSeesALookupStartedElsewhere() =
        runBlocking {
            val titles = repository { 200 to """{"title":"Нагиев – пенсии, стих в Кремле / вДудь"}""" }
            val seen = async { titles.cachedUpdates("8sWj1Q2SQGQ").take(2).toList() }
            yield()

            titles.originalTitle("8sWj1Q2SQGQ")

            assertThat(withTimeout(5_000) { seen.await() })
                .containsExactly(null, "Нагиев – пенсии, стих в Кремле / вДудь")
                .inOrder()
            assertThat(requests.load()).isEqualTo(1)
        }

    @Test
    fun aVideoIsFetchedOnceHoweverOftenItIsAskedFor() =
        runBlocking {
            val titles = repository { 200 to """{"title":"t"}""" }

            repeat(3) { titles.originalTitle("abc123def45") }

            assertThat(requests.load()).isEqualTo(1)
        }

    @Test
    fun cardsAskingAtTheSameTimeShareOneRequest() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val titles = repository(gate) { 200 to """{"title":"t"}""" }

            val asks = (1..5).map { async(Dispatchers.IO) { titles.originalTitle("abc123def45") } }
            delay(200)
            gate.complete(Unit)

            assertThat(asks.awaitAll()).containsExactly("t", "t", "t", "t", "t")
            assertThat(requests.load()).isEqualTo(1)
        }

    @Test
    fun embeddingTurnedOffIsRememberedAsNoOriginal() =
        runBlocking {
            val titles = repository { 401 to "Unauthorized" }

            assertThat(titles.originalTitle("abc123def45")).isNull()
            assertThat(titles.originalTitle("abc123def45")).isNull()
            assertThat(requests.load()).isEqualTo(1)
        }

    @Test
    fun aNetworkFailureIsAskedAgainNextTime() =
        runBlocking {
            val titles = repository { -1 to "" }

            assertThat(titles.originalTitle("abc123def45")).isNull()
            assertThat(titles.originalTitle("abc123def45")).isNull()
            assertThat(requests.load()).isEqualTo(2)
        }

    @Test
    fun aBodyWithoutATitleIsNoOriginal() {
        assertThat(OriginalTitleRepository.parseTitle("""{"author_name":"x"}""")).isNull()
        assertThat(OriginalTitleRepository.parseTitle("""{"title":"  "}""")).isNull()
        assertThat(OriginalTitleRepository.parseTitle("<html>")).isNull()
    }
}
