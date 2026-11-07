package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * The port's own: upstream walks a javax.xml DOM, the port an element tree built on KtXmlReader (XmlTree), so the
 * parts of the walk that read namespaces, prefixed attributes and the text between spans are checked here.
 */
class TTMLParserStructureTest {
    private val ttml =
        """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
          <head><metadata><audio lyricOffset="0.5"/></metadata></head>
          <body>
            <div ttm:agent="v1">
              <p begin="1.000" end="3.000"><span begin="1.000" end="1.400">Hel</span><span begin="1.400" end="1.800">lo</span> <span begin="1.900" end="2.500">world</span><span ttm:role="x-translation">Hallo Welt</span><span ttm:role="x-bg"><span begin="2.600" end="2.900">(ooh)</span></span></p>
            </div>
            <div><p begin="4.000" end="5.000" ttm:agent="v2">Plain &amp; simple</p></div>
          </body>
        </tt>
        """.trimIndent()

    @Test
    fun wordsJoinAcrossSpansUntilASpaceAndKeepTheirTimesWithTheGlobalOffset() {
        val first = TTMLParser.parseTTMLToLyricsEntries(ttml).first()

        assertThat(first.time).isEqualTo(1_500L)
        assertThat(first.text).isEqualTo("Hello world")
        assertThat(first.words?.map { Triple(it.text, it.startTime, it.endTime) })
            .containsExactly(Triple("Hello", 1_500L, 2_300L), Triple("world", 2_400L, 3_000L))
            .inOrder()
        assertThat(first.agent).isEqualTo("v1")
        assertThat(first.translation).isEqualTo("Hallo Welt")
    }

    @Test
    fun aBackgroundSpanBecomesItsOwnLineAndPlainTextKeepsItsEntities() {
        val entries = TTMLParser.parseTTMLToLyricsEntries(ttml)

        val background = entries.single { it.isBackground }
        assertThat(background.text).isEqualTo("(ooh)")
        // Its span carries no begin of its own, so the line starts with its paragraph; its word keeps its time.
        assertThat(background.time).isEqualTo(1_500L)
        assertThat(background.words?.single()?.startTime).isEqualTo(3_100L)
        assertThat(background.agent).isEqualTo("bg")
        val plain = entries.last()
        assertThat(plain.text).isEqualTo("Plain & simple")
        assertThat(plain.words).isNull()
        assertThat(plain.agent).isEqualTo("v2")
    }

    @Test
    fun aDocumentThatIsNotXmlGivesNoLines() {
        assertThat(TTMLParser.parseTTMLToLyricsEntries("<tt><body><p begin=\"1\">x</body>")).isEmpty()
    }
}
