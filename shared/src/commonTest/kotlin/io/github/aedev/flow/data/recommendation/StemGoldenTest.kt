package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertTrue
import io.github.aedev.flow.testing.readerLines
import io.github.aedev.flow.testing.requireFixture
import kotlin.test.Test

/**
 * Upstream's stem_golden.tsv, written by its KeySpecTablesTest with the Java Snowball stemmers, read back through
 * the port's Kotlin ones on every target, iOS included (the jvm-only KeySpecTablesTest also regenerates it).
 */
class StemGoldenTest {
    @Test
    fun everyGoldenWordGetsUpstreamsKey() {
        val rows =
            requireFixture("neuro/stem_golden.tsv")
                .readerLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('\t') }
        assertTrue("rows ${rows.size}", rows.size > 1_000)
        val tokenizer = NeuroTokenizer()
        rows.forEach { (language, word, key) ->
            tokenizer.keyLanguage = language
            assertEquals("$language $word", key, tokenizer.keyOf(word))
        }
    }
}
