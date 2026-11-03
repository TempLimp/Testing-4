package org.xmlpull.v1

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The shim behaves as Android's KXmlParser does for the calls upstream makes. */
class XmlPullParserTest {
    private fun parser(
        xml: String,
        namespaceAware: Boolean = false,
    ): XmlPullParser =
        XmlPullParserFactory
            .newInstance()
            .apply { isNamespaceAware = namespaceAware }
            .newPullParser()
            .apply { setInput(xml) }

    private fun XmlPullParser.events(): List<String> =
        buildList {
            while (next() != XmlPullParser.END_DOCUMENT) {
                add(
                    when (eventType) {
                        XmlPullParser.START_TAG -> "<$name>"
                        XmlPullParser.END_TAG -> "</$name>"
                        else -> "'$text'"
                    },
                )
            }
        }

    @Test
    fun textEntitiesAndCdataArriveAsOneTextEvent() {
        val events = parser("<p>a &amp; b<![CDATA[ <c> ]]>&#233;<!-- note --> d</p>").events()

        assertEquals(listOf("<p>", "'a & b <c> é d'", "</p>"), events)
    }

    @Test
    fun whitespaceBetweenTagsIsText() {
        assertEquals(listOf("<a>", "' '", "<b>", "</b>", "</a>"), parser("<a> <b/></a>").events())
    }

    @Test
    fun theDocumentStartsBeforeTheFirstTag() {
        val parser = parser("<?xml version=\"1.0\"?><!DOCTYPE a><a/>")

        assertEquals(XmlPullParser.START_DOCUMENT, parser.eventType)
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(1, parser.depth)
        assertEquals(XmlPullParser.END_TAG, parser.next())
        assertEquals(1, parser.depth)
        assertEquals(XmlPullParser.END_DOCUMENT, parser.next())
        assertEquals(0, parser.depth)
    }

    @Test
    fun namesKeepTheirPrefixUnlessNamespaceAware() {
        val xml = "<feed xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\"><yt:videoId>x</yt:videoId></feed>"

        assertEquals(listOf("<feed>", "<yt:videoId>", "'x'", "</yt:videoId>", "</feed>"), parser(xml).events())
        assertEquals(listOf("<feed>", "<videoId>", "'x'", "</videoId>", "</feed>"), parser(xml, namespaceAware = true).events())
    }

    @Test
    fun aNullNamespaceFindsAnAttributeInAnyNamespace() {
        val parser = parser("<m:t xmlns:m=\"urn:m\" m:url=\"u\" views=\"3\"/>", namespaceAware = true)
        parser.next()

        assertEquals("u", parser.getAttributeValue(null, "url"))
        assertEquals("u", parser.getAttributeValue("urn:m", "url"))
        assertEquals("3", parser.getAttributeValue(null, "views"))
        assertNull(parser.getAttributeValue(null, "missing"))
        assertEquals(2, parser.attributeCount)
    }

    @Test
    fun nextTextReadsAnElementsTextAndStopsOnItsEndTag() {
        val parser = parser("<a><b>one &lt; two</b><c></c></a>")
        parser.next()
        parser.next()

        assertEquals("one < two", parser.nextText())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()
        assertEquals("", parser.nextText())
    }

    @Test
    fun nextTextRefusesAnElementWithChildren() {
        val parser = parser("<a><b/></a>")
        parser.next()

        assertFailsWith<XmlPullParserException> { parser.nextText() }
    }
}
