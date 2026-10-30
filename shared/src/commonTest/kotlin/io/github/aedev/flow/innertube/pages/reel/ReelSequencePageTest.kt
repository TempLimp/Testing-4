package io.github.aedev.flow.innertube.pages.reel

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNotNull
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Ignore
import kotlin.test.Test

class ReelSequencePageTest {
    private fun parse(raw: String) = Json.parseToJsonElement(raw).jsonObject.toReelSequencePage()

    @Test
    fun readsEntriesAndABareContinuation() {
        val page =
            parse(
                """
                { "entries": [
                    { "command": { "reelWatchEndpoint": {
                        "videoId": "aaaaaaaaaaa", "playerParams": "PP", "params": "P", "softRefreshContinuation": "SOFT",
                        "thumbnail": { "thumbnails": [
                          { "url": "https://i.ytimg.test/small.jpg", "width": 270, "height": 480 },
                          { "url": "https://i.ytimg.test/frame0.jpg", "width": 1080, "height": 1920 } ] }
                    } } },
                    { "command": { "reelWatchEndpoint": { "playerParams": "no id" } } },
                    { "command": { "reelWatchEndpoint": { "videoId": "bbbbbbbbbbb" } } }
                  ],
                  "continuation": "NEXT" }
                """.trimIndent(),
            )

        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), page.entries.map { it.videoId })
        assertEquals("NEXT", page.continuation)
        val first = page.entries.first()
        assertEquals("PP", first.playerParams)
        assertEquals("P", first.params)
        assertEquals("SOFT", first.softRefreshContinuation)
        assertEquals("https://i.ytimg.test/frame0.jpg", first.posterUrl)
        assertNull(page.entries.last().posterUrl)
    }

    @Test
    fun adEntriesAreDroppedOnEveryClientShape() {
        val page =
            parse(
                """
                { "entries": [
                    { "command": { "reelWatchEndpoint": { "videoId": "aaaaaaaaaaa", "videoType": "REEL_VIDEO_TYPE_VIDEO" } } },
                    { "command": { "reelWatchEndpoint": { "videoId": "ad1ad1ad1ad", "videoType": "REEL_VIDEO_TYPE_AD",
                        "adClientParams": { "isAd": true } } } },
                    { "command": { "reelWatchEndpoint": { "videoId": "ad2ad2ad2ad", "adClientParams": { "isAd": true } } } },
                    { "command": { "reelWatchEndpoint": { "videoId": "bbbbbbbbbbb", "adClientParams": { "isAd": false } } } }
                  ],
                  "continuation": "NEXT" }
                """.trimIndent(),
            )

        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), page.entries.map { it.videoId })
    }

    @Test
    fun readsTheWebContinuationEndpoint() {
        val page =
            parse(
                """
                { "entries": [ { "command": { "reelWatchEndpoint": { "videoId": "aaaaaaaaaaa" } } } ],
                  "continuationEndpoint": { "continuationCommand": { "token": "WEB_NEXT", "request": "CONTINUATION_REQUEST_TYPE_REEL_WATCH_SEQUENCE" } } }
                """.trimIndent(),
            )

        assertEquals("WEB_NEXT", page.continuation)
    }

    @Test
    fun anEmptyResponseIsAnEmptyExhaustedPage() {
        val page = parse("""{ "responseContext": {} }""")

        assertTrue(page.entries.isEmpty())
        assertNull(page.continuation)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun iosSeededPageListsReelsWithFullSizePostersAndNeverTheSeed() {
        val page = ReelFixture(ReelFixture.SEQUENCE_SEEDED_IOS).toReelSequencePage()

        assertTrue(page.entries.size >= 3)
        assertNotNull(page.continuation)
        assertTrue(page.entries.none { it.videoId == "pJWqvVnDT0A" })
        page.entries.forEach { entry ->
            assertEquals(11, entry.videoId.length)
            assertNotNull(entry.playerParams)
            assertTrue(entry.posterUrl.orEmpty().contains("frame0"))
        }
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun webSeededPageCarriesItsContinuationInsideAnEndpoint() {
        val page = ReelFixture(ReelFixture.SEQUENCE_SEEDED_WEB).toReelSequencePage()

        assertTrue(page.entries.size >= 3)
        assertNotNull(page.continuation)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun theSeedlessFirstPageHoldsOneEntryAndTheContinuationOfTheRest() {
        val page = ReelFixture(ReelFixture.SEQUENCE_SEEDLESS_ANDROID).toReelSequencePage()

        assertEquals(1, page.entries.size)
        assertNotNull(page.continuation)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun anAndroidContinuationPageParsesWithoutItsPrefetchPayloads() {
        val page = ReelFixture(ReelFixture.SEQUENCE_CONTINUATION_ANDROID).toReelSequencePage()

        assertTrue(page.entries.size >= 3)
        assertNotNull(page.continuation)
    }
}
