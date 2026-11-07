package io.github.aedev.flow.data.lyrics

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/** The port's own: upstream decodes numeric entities with java.lang.Character, the port by hand. */
class LyricsUtilsEntityTest {
    @Test
    fun numericEntitiesBecomeTheirCharactersAboveTheBasicPlaneToo() {
        assertThat(LyricsUtils.decodeHtmlEntities("&#65;&#x42; &#x1F3B5; &amp;")).isEqualTo("AB 🎵 &")
    }

    @Test
    fun aCodePointPastUnicodeStaysAsWritten() {
        assertThat(LyricsUtils.decodeHtmlEntities("&#x110000;")).isEqualTo("&#x110000;")
    }
}
