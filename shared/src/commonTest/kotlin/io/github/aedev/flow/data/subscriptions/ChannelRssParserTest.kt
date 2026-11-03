package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertTrue
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.test.Test

class ChannelRssParserTest {
    /** Mirrors the namespace-aware parser [ChannelRssParser.parse] builds on device (upstream: kXML2's KXmlParser). */
    private fun parse(xml: String): ChannelRssFeed {
        val parser =
            XmlPullParserFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newPullParser()
                .apply { setInput(xml) }
        return ChannelRssParser.readFeed(parser)
    }

    private fun entriesOf(xml: String): List<ChannelRssEntry> = parse(xml).entries

    /** A channel feed shaped like YouTube's, entries newest first. */
    private fun feed(
        vararg ids: String,
        links: Map<String, String> = emptyMap(),
    ): String =
        buildString {
            // No leading whitespace: an XML declaration must start at offset 0.
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append(
                "<feed xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\" " +
                    "xmlns:media=\"http://search.yahoo.com/mrss/\" " +
                    "xmlns=\"http://www.w3.org/2005/Atom\">",
            )
            append("<title>Channel Name</title>")
            append("<link rel=\"alternate\" href=\"https://www.youtube.com/channel/x\"/>")
            append("<author><name>Uploader Name</name><uri>https://www.youtube.com/channel/x</uri></author>")
            ids.forEach { id ->
                append("<entry>")
                append("<yt:videoId>$id</yt:videoId>")
                append("<title>Title $id</title>")
                links[id]?.let { append("<link rel=\"alternate\" href=\"$it\"/>") }
                append("<published>2026-05-01T12:00:00+00:00</published>")
                append("<media:group>")
                append("<media:title>Title $id</media:title>")
                append("<media:thumbnail url=\"https://i.ytimg.com/vi/$id/hq.jpg\"/>")
                append("<media:description>Description $id</media:description>")
                append("<media:community><media:statistics views=\"4242\"/></media:community>")
                append("</media:group>")
                append("</entry>")
            }
            append("</feed>")
        }

    @Test
    fun parsesEveryEntryNewestFirst() {
        val videos = entriesOf(feed("aaa", "bbb", "ccc"))

        assertEquals(listOf("aaa", "bbb", "ccc"), videos.map { it.videoId })
        assertEquals("Title aaa", videos.first().title)
        assertEquals("https://i.ytimg.com/vi/aaa/hq.jpg", videos.first().thumbnailUrl)
    }

    @Test
    fun entryTitleWinsOverTheRepeatedMediaTitle() {
        assertEquals("Title aaa", entriesOf(feed("aaa")).single().title)
    }

    @Test
    fun channelTitleIsNotMistakenForAnEntry() {
        val videos = entriesOf(feed("aaa"))

        assertEquals(1, videos.size)
        assertTrue(videos.none { it.title == "Channel Name" })
    }

    @Test
    fun authorNameIsReadAsTheChannelName() {
        assertEquals("Uploader Name", parse(feed("aaa")).channelName)
    }

    @Test
    fun publishedDateIsParsedToEpochMillis() {
        val expected = 1777636800000L // 2026-05-01T12:00:00Z

        assertEquals(expected, entriesOf(feed("aaa")).single().publishedAtMillis)
    }

    @Test
    fun viewCountIsReadFromMediaStatistics() {
        assertEquals(4242L, entriesOf(feed("aaa")).single().viewCount)
    }

    @Test
    fun descriptionIsReadFromMediaDescription() {
        assertEquals("Description aaa", entriesOf(feed("aaa")).single().description)
    }

    @Test
    fun unparseablePublishedDateFallsBackToZeroRatherThanThrowing() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
              <entry><yt:videoId>aaa</yt:videoId><title>Title aaa</title><published>not-a-date</published></entry>
            </feed>
            """.trimIndent()

        assertEquals(0L, entriesOf(xml).single().publishedAtMillis)
    }

    @Test
    fun entryWithoutAVideoIdIsSkipped() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
              <entry><title>No id here</title></entry>
              <entry><yt:videoId>bbb</yt:videoId><title>Real one</title></entry>
            </feed>
            """.trimIndent()

        assertEquals(listOf("bbb"), entriesOf(xml).map { it.videoId })
    }

    @Test
    fun malformedXmlYieldsNoEntriesInsteadOfThrowing() {
        assertEquals(emptyList<ChannelRssEntry>(), entriesOf("<feed><entry>"))
    }

    @Test
    fun missingThumbnailStaysNull() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
              <entry><yt:videoId>aaa</yt:videoId><title>Title aaa</title></entry>
            </feed>
            """.trimIndent()

        assertNull(entriesOf(xml).single().thumbnailUrl)
    }

    @Test
    fun firstEverCheckAnnouncesNothing() {
        val entries = entriesOf(feed("aaa", "bbb"))

        assertEquals(emptyList<ChannelRssEntry>(), ChannelRssParser.newEntriesSince(entries, lastVideoId = null))
    }

    @Test
    fun unchangedFeedAnnouncesNothing() {
        val entries = entriesOf(feed("aaa", "bbb"))

        assertEquals(emptyList<ChannelRssEntry>(), ChannelRssParser.newEntriesSince(entries, lastVideoId = "aaa"))
    }

    @Test
    fun everyVideoPublishedSinceTheLastCheckIsAnnounced() {
        val entries = entriesOf(feed("new1", "new2", "new3", "known"))

        val fresh = ChannelRssParser.newEntriesSince(entries, lastVideoId = "known")

        assertEquals(listOf("new1", "new2", "new3"), fresh.map { it.videoId })
    }

    @Test
    fun aBurstOfNewVideosIsCapped() {
        val ids = (1..12).map { "v$it" }.toTypedArray()
        val entries = entriesOf(feed(*ids, "known"))

        val fresh = ChannelRssParser.newEntriesSince(entries, lastVideoId = "known")

        assertEquals(ChannelRssParser.MAX_NEW_PER_CHANNEL, fresh.size)
        assertEquals("v1", fresh.first().videoId)
    }

    @Test
    fun pointerThatAgedOutOfTheFeedAnnouncesOnlyTheNewest() {
        val entries = entriesOf(feed("aaa", "bbb", "ccc"))

        val fresh = ChannelRssParser.newEntriesSince(entries, lastVideoId = "long-gone")

        assertEquals(listOf("aaa"), fresh.map { it.videoId })
    }

    @Test
    fun emptyFeedAnnouncesNothing() {
        assertEquals(emptyList<ChannelRssEntry>(), ChannelRssParser.newEntriesSince(emptyList(), lastVideoId = "aaa"))
    }

    @Test
    fun entryLinkTellsAReelFromAVideo() {
        val entries =
            entriesOf(
                feed(
                    "reel",
                    "video",
                    links =
                        mapOf(
                            "reel" to "https://www.youtube.com/shorts/reel",
                            "video" to "https://www.youtube.com/watch?v=video",
                        ),
                ),
            )

        assertEquals(listOf(true, false), entries.map { it.isShort })
    }

    @Test
    fun entryWithoutARecognisedLinkHasNoReelVerdict() {
        val entries = entriesOf(feed("bare", "other", links = mapOf("other" to "https://www.youtube.com/live/other")))

        assertEquals(listOf(null, null), entries.map { it.isShort })
    }
}
