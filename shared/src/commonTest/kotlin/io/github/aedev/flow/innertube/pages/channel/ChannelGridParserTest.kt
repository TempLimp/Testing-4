package io.github.aedev.flow.innertube.pages.channel

import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import kotlinx.serialization.json.Json
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

/**
 * The grid arrives in four shapes — initial browse, sort switch, page append and the older
 * `richGridContinuation` — and the header carries continuation tokens of its own that must not be
 * mistaken for the grid's next page.
 */
class ChannelGridParserTest {
    private fun parse(
        raw: String,
        kind: ChannelTabKind = ChannelTabKind.Videos,
        fallbackOwner: FeedItemOwner = FeedItemOwner(),
    ) = Json.parseToJsonElement(raw).toChannelTabContent(kind, fallbackOwner)

    private fun lockup(id: String) =
        """
        { "richItemRenderer": { "content": { "lockupViewModel": {
          "contentId": "$id",
          "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
          "metadata": { "lockupMetadataViewModel": { "title": { "content": "Video $id" } } }
        } } } }
        """.trimIndent()

    private fun continuationItem(token: String) =
        """{ "continuationItemRenderer": { "continuationEndpoint": { "continuationCommand": { "token": "$token" } } } }"""

    private val metadata =
        """
        "metadata": { "channelMetadataRenderer": {
          "title": "Linus Tech Tips",
          "externalId": "UCXuqSBlHAE6Xw-yeJA0Tunw",
          "avatar": { "thumbnails": [ { "url": "https://yt3.test/a.jpg", "width": 900, "height": 900 } ] }
        } }
        """.trimIndent()

    @Test
    fun readsAnInitialBrowseWithItsSortBarAndContinuation() {
        val content =
            parse(
                """
                {
                  $metadata,
                  "contents": { "twoColumnBrowseResultsRenderer": { "tabs": [ { "tabRenderer": {
                    "selected": true,
                    "content": { "richGridRenderer": {
                      "header": { "chipBarViewModel": { "chips": [
                        { "chipViewModel": { "text": "Latest", "selected": true,
                          "tapCommand": { "innertubeCommand": { "continuationCommand": { "token": "TOK_LATEST" } } } } },
                        { "chipViewModel": { "text": "Popular",
                          "tapCommand": { "innertubeCommand": { "continuationCommand": { "token": "TOK_POPULAR" } } } } }
                      ] } },
                      "contents": [ ${lockup("a")}, ${lockup("b")}, ${continuationItem("TOK_PAGE_2")} ]
                    } }
                  } } ] } }
                }
                """.trimIndent(),
            )

        assertEquals(listOf("a", "b"), content.items.map { (it as FeedItem.VideoItem).video.id })
        assertEquals(listOf("Latest", "Popular"), content.filters.flatMap { group -> group.options.map { it.label } })
        assertEquals("TOK_PAGE_2", content.continuation)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", content.owner.id)
        assertEquals("Linus Tech Tips", content.owner.name)
        assertEquals("https://yt3.test/a.jpg", content.owner.avatarUrl)
    }

    @Test
    fun readsAPageAppend() {
        val content =
            parse(
                """
                { "onResponseReceivedActions": [ { "appendContinuationItemsAction": {
                  "continuationItems": [ ${lockup("c")}, ${continuationItem("TOK_PAGE_3")} ]
                } } ] }
                """.trimIndent(),
            )

        assertEquals(listOf("c"), content.items.map { (it as FeedItem.VideoItem).video.id })
        assertEquals("TOK_PAGE_3", content.continuation)
    }

    @Test
    fun readsASortSwitchThatReplacesTheGridRatherThanExtendingIt() {
        val content =
            parse(
                """
                { "onResponseReceivedActions": [
                  { "reloadContinuationItemsCommand": { "slot": "RELOAD_CONTINUATION_SLOT_HEADER",
                    "continuationItems": [ { "chipBarViewModel": { "chips": [
                      { "chipViewModel": { "text": "Popular", "selected": true,
                        "tapCommand": { "innertubeCommand": { "continuationCommand": { "token": "TOK_POPULAR" } } } } }
                    ] } } ] } },
                  { "reloadContinuationItemsCommand": { "slot": "RELOAD_CONTINUATION_SLOT_BODY",
                    "continuationItems": [ ${lockup("d")}, ${continuationItem("TOK_POPULAR_2")} ] } }
                ] }
                """.trimIndent(),
            )

        assertEquals(listOf("d"), content.items.map { (it as FeedItem.VideoItem).video.id })
        assertEquals("TOK_POPULAR_2", content.continuation)
        assertEquals(listOf("Popular"), content.filters.flatMap { group -> group.options.map { it.label } })
    }

    @Test
    fun readsTheOlderRichGridContinuation() {
        val content =
            parse(
                """
                { "continuationContents": { "richGridContinuation": {
                  "contents": [ ${lockup("e")}, ${continuationItem("TOK_OLD")} ]
                } } }
                """.trimIndent(),
            )

        assertEquals(listOf("e"), content.items.map { (it as FeedItem.VideoItem).video.id })
        assertEquals("TOK_OLD", content.continuation)
    }

    @Test
    fun doesNotMistakeTheHeadersOwnContinuationForTheGridsNextPage() {
        val content =
            parse(
                """
                {
                  "header": { "pageHeaderRenderer": { "content": { "pageHeaderViewModel": {
                    "description": { "descriptionPreviewViewModel": {
                      "rendererContext": { "commandContext": ${continuationItem("TOK_HEADER_DESCRIPTION")} }
                    } }
                  } } } },
                  "contents": { "twoColumnBrowseResultsRenderer": { "tabs": [ { "tabRenderer": {
                    "content": { "richGridRenderer": { "contents": [ ${lockup("f")} ] } }
                  } } ] } }
                }
                """.trimIndent(),
            )

        assertEquals(listOf("f"), content.items.map { (it as FeedItem.VideoItem).video.id })
        assertEquals(null, content.continuation)
    }

    @Test
    fun aContinuationKeepsTheOwnerTheCallerThreadedIn() {
        val fallback = FeedItemOwner(id = "UCcarried", name = "Carried", avatarUrl = "https://yt3.test/carried.jpg")
        val content =
            parse(
                """
                { "onResponseReceivedActions": [ { "appendContinuationItemsAction": {
                  "continuationItems": [ ${lockup("g")} ]
                } } ] }
                """.trimIndent(),
                fallbackOwner = fallback,
            )

        assertEquals(fallback, content.owner)
        assertEquals("Carried", (content.items.single() as FeedItem.VideoItem).video.channelName)
    }

    @Test
    fun anEmptyTabParsesToNoItemsRatherThanFailing() {
        val content =
            parse(
                """
                { "contents": { "twoColumnBrowseResultsRenderer": { "tabs": [ { "tabRenderer": {
                  "content": { "richGridRenderer": { "contents": [] } }
                } } ] } } }
                """.trimIndent(),
            )

        assertTrue(content.items.isEmpty())
        assertTrue(content.filters.isEmpty())
        assertEquals(null, content.continuation)
    }

    @Test
    fun duplicateItemsAcrossGridShapesAreCollapsed() {
        val content =
            parse(
                """
                {
                  "contents": { "twoColumnBrowseResultsRenderer": { "tabs": [ { "tabRenderer": {
                    "content": { "richGridRenderer": { "contents": [ ${lockup("dup")} ] } }
                  } } ] } },
                  "onResponseReceivedActions": [ { "appendContinuationItemsAction": {
                    "continuationItems": [ ${lockup("dup")} ]
                  } } ]
                }
                """.trimIndent(),
            )

        assertEquals(1, content.items.size)
    }
}
