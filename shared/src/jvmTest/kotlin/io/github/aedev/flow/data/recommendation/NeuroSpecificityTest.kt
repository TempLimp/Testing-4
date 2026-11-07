package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.REPO_ROOT

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertWithMessage
import kotlin.test.Test
import java.io.File

class NeuroSpecificityTest {
    private val tokenizer = NeuroTokenizer()

    private fun specificity(title: String) = tokenizer.specificity(tokenizer.keysOf(title).single())

    @Test
    fun wordsOfOneSubjectAreSpecific() {
        listOf("pixel", "snapdragon", "linux", "android", "earbuds").forEach {
            assertWithWord(it).that(specificity(it)).isAtLeast(0.9)
        }
    }

    @Test
    fun wordsEverySubjectUsesAreNot() {
        listOf("better", "long", "year", "top", "buy", "normal", "test", "phone").forEach {
            assertWithWord(it).that(specificity(it)).isLessThan(0.5)
        }
    }

    @Test
    fun aPhraseIsAsSpecificAsItsWordsTogether() {
        assertThat(tokenizer.specificity("long term")).isLessThan(0.4)
        assertThat(tokenizer.specificity("galaxi s26")).isEqualTo(1.0)
    }

    @Test
    fun aDomainTagDoesNotChangeTheWord() {
        assertThat(tokenizer.specificity("pixel:tech")).isEqualTo(tokenizer.specificity("pixel"))
    }

    @Test
    fun sharingOnlyACommonWordWithTheViewerCountsForLittle() {
        val viewer = NeuroVectorMath.PreparedVector(ContentVector(topics = mapOf("pixel" to 0.6, "normal" to 0.4)))
        val shared = ContentVector(topics = mapOf("normal" to 1.0))

        val plain = NeuroVectorMath.topicSimilarity(viewer, shared)
        val weighted = NeuroVectorMath.topicSimilarity(viewer, shared, tokenizer::specificity)

        assertThat(weighted).isLessThan(plain * 0.3)
    }

    @Test
    fun theChineseTableHoldsOnlyWordsTheWordBreakerKeepsWhole() {
        val file = File(REPO_ROOT, "upstream/app/src/main/resources/neuro/freq/zh.txt")
        val lines = file.readLines().filter { it.isNotBlank() }
        val whole =
            lines.filter { line ->
                val word = NeuroText.fold(line.substringBeforeLast(' '))
                word.length >= 2 && NeuroText.words(word, PlatformWordBreaker) == listOf(word)
            }
        if (System.getenv("NEURO_WRITE_FREQ_ZH") == "1") file.writeText(whole.joinToString("\n") + "\n")
        assertWithMessage("run the test with NEURO_WRITE_FREQ_ZH=1").that(whole).isEqualTo(lines)
    }

    @Test
    fun chineseWordsEverySubjectUsesAreNotSpecific() {
        val chinese = NeuroTokenizer(PlatformWordBreaker).also { it.languageProfile = "zh" }

        listOf("新闻", "电影", "音乐", "游戏", "时候").forEach { assertWithWord(it).that(chinese.specificity(it)).isLessThan(0.5) }
        assertThat(chinese.specificity("台积电")).isEqualTo(1.0)
    }

    private fun assertWithWord(word: String) =
        io.github.aedev.flow.testing
            .assertWithMessage(word)
}
