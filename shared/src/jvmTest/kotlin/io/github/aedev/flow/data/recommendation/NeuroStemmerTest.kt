/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.REPO_ROOT

import io.github.aedev.flow.testing.UPSTREAM_TEST_RESOURCES

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertWithMessage
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import io.github.aedev.flow.data.recommendation.eval.RealTitleCorpus
import kotlin.test.Test
import java.io.File
import java.util.concurrent.Executors

/** Key spec 2, stage 8: stems by script and profile, never for the engine's own words (#907). */
class NeuroStemmerTest {
    private val tokenizer = NeuroTokenizer()

    private fun keys(
        profile: String,
        vararg words: String,
    ): List<String> {
        tokenizer.keyLanguage = profile
        return words.map(tokenizer::keyOf)
    }

    @Test
    fun theUnstemmedListHoldsEveryWordTheEngineSListsCompareKeysWith() {
        val curated =
            (
                tokenizer.curatedWords + NeuroDiscovery(NeuroTopicCatalog.TOPIC_CATEGORIES, tokenizer).curatedWords +
                    NeuroScoring.CURATED_WORDS
            ).flatMap { NeuroText.fold(it).split(' ') }
                .map(tokenizer::normalizeLemma)
                .filter { word -> word.isNotEmpty() && word.all { it.isLetter() } }
                .toSortedSet()
        val file = File(REPO_ROOT, "upstream/app/src/main/resources/neuro/keys/unstemmed.txt")
        if (System.getenv("NEURO_WRITE_UNSTEMMED") == "1") {
            file.parentFile.mkdirs()
            file.writeText(
                "# Words key spec 2 never stems: the engine's own word lists are written in them (NeuroStemmer).\n" +
                    "# Written by NeuroStemmerTest with NEURO_WRITE_UNSTEMMED=1. Flow Desktop reads the same file.\n" +
                    curated.joinToString("\n", postfix = "\n"),
            )
        }
        val listed = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.toSet()

        assertWithMessage("run the test with NEURO_WRITE_UNSTEMMED=1").that(curated - listed).isEmpty()
    }

    @Test
    fun wordsShareAKeyWithTheirOtherFormsInTheViewerSLanguage() {
        assertThat(keys("en", "guitars", "guitar", "kayaks", "kayaking")).containsExactly("guitar", "guitar", "kayak", "kayak").inOrder()
        assertThat(keys("es", "croquetas", "croqueta")).containsExactly("croquet", "croquet")
        assertThat(keys("fr", "recettes", "recette")).containsExactly("recet", "recet")
        assertThat(keys("de", "rezepte", "rezept")).containsExactly("rezept", "rezept")
        assertThat(keys("ru", "рецепты", "рецептов", "рецепт")).containsExactly("рецепт", "рецепт", "рецепт")
    }

    @Test
    fun theEngineSOwnWordsAndWordsWithDigitsStayAsWritten() {
        assertThat(keys("en", "anime", "animation", "technology", "lyrics", "ps5", "4k", "node.js"))
            .containsExactly(
                "anime",
                "animation",
                "technology",
                "lyrics",
                "ps5",
                "4k",
                "node.js",
            ).inOrder()
    }

    @Test
    fun theScriptAndTheProfileChooseTheStemmerNeverTheTitle() {
        assertThat(keys("tr", "oyunları", "guitars")).containsExactly("oyunları", "guitars").inOrder()
        assertThat(keys("ru", "guitars", "рецепты")).containsExactly("guitar", "рецепт").inOrder()
        assertThat(keys("uk", "рецепти")).containsExactly("рецепти")
        assertThat(keys("ja", "ギター")).containsExactly("ギター")
    }

    @Test
    fun aKeyIsAlwaysItsOwnKey() {
        val words =
            (RealTitleCorpus.titles + NeuroLearningBenchmark.pool().map { it.title } + languageTitles())
                .flatMap { NeuroText.words(NeuroText.fold(it)) }
                .map { piece -> piece.trim { !it.isLetter() } }
                .filter { it.length >= 3 }
                .toSet()
        listOf("en", "es", "pt", "fr", "de", "it", "ru").forEach { profile ->
            tokenizer.keyLanguage = profile
            val changed = words.filter { word -> tokenizer.keyOf(word).let { tokenizer.keyOf(it) != it } }
            assertWithMessage(profile).that(changed).isEmpty()
        }
    }

    @Test
    fun theLemmaMapNeverRewritesItsOwnLemmas() {
        val lemmas = tokenizer.curatedWords.map(tokenizer::normalizeLemma)
        assertThat(lemmas.filter { tokenizer.normalizeLemma(it) != it }).isEmpty()
    }

    @Test
    fun aStemThatStemsAgainLeavesTheWordAsWritten() {
        assertThat(keys("es", "casero", "casa")).containsExactly("casero", "cas").inOrder()
    }

    @Test
    fun stemmingOnManyThreadsGivesTheSameKeysAsOnOne() {
        val words = languageTitles().flatMap { it.lowercase().split(' ') }.filter { word -> word.length >= 3 && word.all { it.isLetter() } }
        val serial = words.map { NeuroStemmer.key(it, "es") }
        val pool = Executors.newFixedThreadPool(8)
        val parallel = List(8) { pool.submit<List<String>> { words.map { NeuroStemmer.key(it, "es") } } }.map { it.get() }
        pool.shutdown()

        parallel.forEach { assertThat(it).isEqualTo(serial) }
    }

    private fun languageTitles(): List<String> =
        File(UPSTREAM_TEST_RESOURCES, "neuro/language_titles.tsv")
                .inputStream()
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringAfter('\t') }
}
