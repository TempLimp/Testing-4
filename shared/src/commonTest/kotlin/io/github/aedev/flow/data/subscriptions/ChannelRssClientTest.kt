package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.testing.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test

/**
 * #1094: a sweep's odd 429 or 5xx gets one spaced retry instead of emptying the channel.
 *
 * Upstream scripts the answers through an OkHttp interceptor; the port's client is Ktor, so a MockEngine plays them.
 */
class ChannelRssClientTest {
    private class Scripted(
        private val outcomes: List<Any>,
    ) {
        var calls = 0

        val engine =
            MockEngine {
                val outcome = outcomes[calls.coerceAtMost(outcomes.lastIndex)]
                calls++
                if (outcome is IOException) throw outcome
                val (code, retryAfter) = outcome as Pair<*, *>
                respond(
                    content = FEED,
                    status = HttpStatusCode.fromValue(code as Int),
                    headers = (retryAfter as String?)?.let { headersOf("Retry-After", it) } ?: headersOf(),
                )
            }
    }

    private val sleeps = mutableListOf<Long>()

    private fun client(scripted: Scripted): ChannelRssClient {
        val http = HttpClient(scripted.engine)
        return ChannelRssClient(httpClient = { http }, sleep = { sleeps += it })
    }

    @Test
    fun a429IsRetriedOnceAndTheSecondAnswerIsUsed() =
        runTest {
            val scripted = Scripted(listOf(429 to null, 200 to null))

            val result = client(scripted).fetch("UCa")

            assertThat(result.isSuccess).isTrue()
            assertThat(scripted.calls).isEqualTo(2)
            assertThat(sleeps).hasSize(1)
        }

    @Test
    fun retryAfterSetsTheWaitWithinBounds() =
        runTest {
            client(Scripted(listOf(503 to "3", 200 to null))).fetch("UCa")

            assertThat(sleeps).containsExactly(3_000L)
        }

    @Test
    fun aSecondServerErrorIsAFailureNotAThirdRequest() =
        runTest {
            val scripted = Scripted(listOf(500 to null, 502 to null, 200 to null))

            val result = client(scripted).fetch("UCa")

            assertThat(result.isFailure).isTrue()
            assertThat(scripted.calls).isEqualTo(2)
        }

    @Test
    fun aDroppedConnectionIsRetried() =
        runTest {
            val scripted = Scripted(listOf(IOException("reset"), 200 to null))

            assertThat(client(scripted).fetch("UCa").isSuccess).isTrue()
            assertThat(scripted.calls).isEqualTo(2)
        }

    @Test
    fun a404IsFinal() =
        runTest {
            val scripted = Scripted(listOf(404 to null, 200 to null))

            val result = client(scripted).fetch("UCa")

            assertThat(result.isFailure).isTrue()
            assertThat(scripted.calls).isEqualTo(1)
            assertThat(sleeps).isEmpty()
        }

    private companion object {
        const val FEED = "<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>"
    }
}
