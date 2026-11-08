package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.testing.assertEquals
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Upstream's RefusedStreamRetryDataSourceTest on the proxy, which plays that data source's role on iOS: a refused
 * file is fetched once more from the replacement the player recorded; anything else reaches the player as it came.
 */
class LocalHlsProxyRetryJvmTest {
    private val asked = mutableListOf<String>()

    // A googlevideo stand-in: "/old" is refused, "/gone" is not found, anything else is served.
    private val googlevideo =
        embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/{file}") {
                    val file = call.parameters["file"].orEmpty()
                    asked += file
                    when (file) {
                        "old", "older" -> call.respondText("", status = HttpStatusCode.Forbidden)
                        "gone" -> call.respondText("", status = HttpStatusCode.NotFound)
                        else -> call.respondText("bytes of $file", status = HttpStatusCode.PartialContent)
                    }
                }
            }
        }.start(wait = false)

    private val origin = runBlocking { "http://127.0.0.1:${googlevideo.engine.resolvedConnectors().first().port}" }

    private class Path(
        private val replacement: String?,
    ) : LocalHlsProxy.DataPath {
        var swapped: String? = null
        val refusals = mutableListOf<String>()

        override fun resolveUrl(url: String): String = swapped ?: url

        override fun refuses(
            url: String,
            position: Long,
        ) = false

        override suspend fun onRefused(
            position: Long,
            url: String,
        ): Boolean {
            refusals += url
            swapped = replacement ?: return false
            return true
        }
    }

    private fun fetch(
        file: String,
        path: Path,
    ): Pair<HttpStatusCode, String> =
        runBlocking {
            LocalHlsProxy.dataPath = path
            LocalHlsProxy.mediaCache = null
            val rendition = HlsRendition("$origin/$file", "avc1.4d401f", 1_000, false, 0, 9, 10, 19, mimeType = "video/mp4")
            val url = LocalHlsProxy.publishFiles(HlsPresentation(listOf(rendition), null, 1_000)).single()
            HttpClient().use { client ->
                val response = client.get(url) { header(HttpHeaders.Range, "bytes=0-99") }
                response.status to response.bodyAsText()
            }
        }

    @AfterTest
    fun tearDown() {
        LocalHlsProxy.dataPath = null
        googlevideo.stop(0, 0)
    }

    @Test
    fun aRefusedUrlIsDroppedAndTheFileOpensAgainWithAFreshOne() {
        val path = Path(replacement = "$origin/fresh")

        val (status, body) = fetch("old", path)

        assertEquals(HttpStatusCode.PartialContent, status)
        assertEquals("bytes of fresh", body)
        assertEquals(listOf("old", "fresh"), asked)
    }

    @Test
    fun aSecondRefusalIsThePlayersToHandle() {
        val path = Path(replacement = "$origin/older")

        val (status, _) = fetch("old", path)

        assertEquals(HttpStatusCode.Forbidden, status)
        assertEquals(listOf("old", "older"), asked)
        assertEquals(1, path.refusals.size)
    }

    @Test
    fun aRefusalTheHandlerCannotActOnGoesStraightToThePlayer() {
        val path = Path(replacement = null)

        val (status, _) = fetch("old", path)

        assertEquals(HttpStatusCode.Forbidden, status)
        assertEquals(listOf("old"), asked)
    }

    @Test
    fun otherResponsesAreNotRetriedHere() {
        val path = Path(replacement = "$origin/fresh")

        val (status, _) = fetch("gone", path)

        assertEquals(HttpStatusCode.NotFound, status)
        assertEquals(listOf("gone"), asked)
        assertEquals(0, path.refusals.size)
    }
}
