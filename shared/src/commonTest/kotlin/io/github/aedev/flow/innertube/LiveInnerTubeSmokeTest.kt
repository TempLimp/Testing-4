package io.github.aedev.flow.innertube

import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.pages.search.SearchSection
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The live API, with no account and no key (F19, F35): a YouTube Music search, a YouTube Music
 * browse, and a video search through the WEB client version read off youtube.com. Every target runs
 * it, so on iOS it goes through NSURLSession end to end.
 *
 * A runner YouTube turns away (HTTP 403 or 429, or a consent or "unusual traffic" page where JSON
 * should be) is printed as `LIVE SMOKE: BLOCKED` and passes: that is the runner's address, not the
 * client. Anything else, a parse failure included, fails the test.
 */
class LiveInnerTubeSmokeTest {
    @Test
    fun aMusicSearchFindsTheSong() =
        live("music search") {
            val result = YouTube.search("Queen Bohemian Rhapsody", YouTube.SearchFilter.FILTER_SONG).getOrThrow()

            assertTrue(result.items.isNotEmpty(), "no results")
            assertTrue(result.items.any { it is SongItem && it.title.contains("Bohemian", ignoreCase = true) }, "no matching song in ${result.items.map { it.title }}")
        }

    @Test
    fun theMusicHomeBrowseHasShelves() =
        live("music browse") {
            val home = YouTube.home().getOrThrow()
            if (home.sections.isEmpty()) {
                // A parser that missed the shelves fails; a home sent with none (seen from a CI runner) is a block.
                val raw = InnerTube().browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_home").bodyAsText()
                val shelves = SHELF_RENDERERS.filter { it in raw }
                assertTrue(shelves.isEmpty(), "YouTube sent $shelves but none parsed")
                throw HomeWithoutShelves(raw.length, Regex(""""text"\s*:\s*"([^"]{1,160})"""").findAll(raw).take(3).joinToString(" | ") { it.groupValues[1] })
            }

            assertTrue(home.sections.any { it.title.isNotBlank() && it.items.isNotEmpty() }, "no filled shelf in ${home.sections.map { it.title }}")
        }

    @Test
    fun aVideoSearchFindsVideosWithTheSitesClientVersion() =
        live("video search") {
            val page = YouTube.videoSearch("linus tech tips").getOrThrow()
            assertTrue(page.sections.any { it is SearchSection.Result }, "no results in ${page.sections}")

            val version = WebClientVersion.get(InnerTube())
            assertNotNull(version, "youtube.com named no WEB client version")
            assertTrue(Regex("""\d+\.\d{8}\.\d+\.\d+""").matches(version), "unexpected WEB client version $version")
        }
}

private fun live(
    what: String,
    block: suspend () -> Unit,
) = runBlocking {
    try {
        withTimeout(90.seconds) { block() }
    } catch (error: Throwable) {
        val reason = blockReason(error) ?: throw error
        println("LIVE SMOKE: BLOCKED $what: $reason")
    }
}

/** Why YouTube refused this request, or null when the failure is something else. */
internal fun blockReason(error: Throwable): String? {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is ResponseException && cause.response.status.value in BLOCK_STATUSES) {
            return "HTTP ${cause.response.status.value} from ${cause.response.call.request.url.host}"
        }
        if (cause is HomeWithoutShelves) return "YouTube Music sent ${cause.message}"
        // A consent or "unusual traffic" page arrives as HTML where the body should be JSON.
        val message = cause.message.orEmpty()
        BLOCK_MARKERS.firstOrNull { it in message }?.let { return "a web page instead of JSON ($it)" }
        cause = cause.cause
    }
    return null
}

private val BLOCK_STATUSES = setOf(403, 429)
private val SHELF_RENDERERS = listOf("musicCarouselShelfRenderer", "musicImmersiveCarouselShelfRenderer", "musicShelfRenderer", "gridRenderer")

private class HomeWithoutShelves(
    length: Int,
    texts: String,
) : Exception("a home with no shelves ($length bytes; text: $texts)")
private val BLOCK_MARKERS = listOf("text/html", "consent.youtube.com", "google.com/sorry", "unusual traffic")
