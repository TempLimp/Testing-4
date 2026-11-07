package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

/** The ICU pieces FlowNeuro reads text with: ICU4J on jvm, the system's libicucore on iOS. */
class IcuTextTest {
    @Test
    fun scriptsAreReadAsAndroidReadsThem() {
        mapOf(
            'a' to UnicodeScript.LATIN,
            'é' to UnicodeScript.LATIN,
            'я' to UnicodeScript.CYRILLIC,
            'ع' to UnicodeScript.ARABIC,
            'अ' to UnicodeScript.DEVANAGARI,
            '中' to UnicodeScript.HAN,
            'あ' to UnicodeScript.HIRAGANA,
            'ア' to UnicodeScript.KATAKANA,
            '한' to UnicodeScript.HANGUL,
            'ก' to UnicodeScript.THAI,
            'ລ' to UnicodeScript.LAO,
            'ក' to UnicodeScript.KHMER,
            'က' to UnicodeScript.MYANMAR,
            '1' to UnicodeScript.OTHER,
            ' ' to UnicodeScript.OTHER,
            'Ω' to UnicodeScript.OTHER,
        ).forEach { (c, script) -> assertEquals("$c", script, UnicodeScript.of(c.code)) }
    }

    @Test
    fun wordBreaksKeepEveryCharacterAndSplitUnspacedRuns() {
        val text = "吉他教学零基础入门, ok"
        val pieces = icuWordBreaks(text)
        assertEquals(text, pieces.joinToString(""))
        assertTrue(pieces.toString(), "吉他" in pieces)
        assertTrue(pieces.toString(), "," in pieces)
        assertTrue(pieces.toString(), "ok" in pieces)
        assertEquals(emptyList<String>(), icuWordBreaks(""))
    }

    @Test
    fun nfkcFoldsCompatibilityForms() {
        assertEquals("phonk", "ｐｈｏｎｋ".normalizeNfkc())
        assertEquals("fi1", "ﬁ①".normalizeNfkc())
        assertEquals("é", "é".normalizeNfkc())
    }

    @Test
    fun gzipWritesAGzipStream() {
        val bytes = gzip("hello, recap".encodeToByteArray())
        // Magic, deflate, and the input length (ISIZE) little-endian in the trailer.
        assertEquals(0x1f, bytes[0].toInt() and 0xff)
        assertEquals(0x8b, bytes[1].toInt() and 0xff)
        assertEquals(8, bytes[2].toInt())
        assertEquals(12, bytes[bytes.size - 4].toInt())
        assertEquals(0x1f, gzip(ByteArray(0))[0].toInt() and 0xff)
    }
}
