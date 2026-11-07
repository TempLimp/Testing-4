/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class NeuroBlockingTest {
    private val tokenizer = NeuroTokenizer()
    private val lemma: (String) -> String = tokenizer::normalizeLemma

    private fun matchers(vararg blocked: String) =
        NeuroScoring.buildBlockedMatchers(blocked.toSet(), NeuroTopicCatalog.TOPIC_CATEGORIES, lemma)

    private fun blocked(
        title: String,
        channel: String = "",
        vararg blockedTopics: String,
    ) = NeuroScoring.isBlockedByText(title, channel, matchers(*blockedTopics), lemma)

    @Test
    fun blockingOneTopicDoesNotBlockItsCategorySiblings() {
        // The old expansion nuked ALL of Gaming when any Gaming topic was blocked.
        assertThat(blocked("Minecraft castle build", "", "fortnite")).isFalse()
        assertThat(blocked("Best esports moments", "", "fortnite")).isFalse()
        assertThat(blocked("Fortnite chapter 5 gameplay", "", "fortnite")).isTrue()
    }

    @Test
    fun blockingTheCategoryNAMEStillBlocksTheWholeCategory() {
        assertThat(blocked("Fortnite chapter 5", "", "gaming")).isTrue()
        assertThat(blocked("Minecraft survival ep 1", "", "gaming")).isTrue()
        assertThat(blocked("Sourdough baking basics", "", "gaming")).isFalse()
    }

    @Test
    fun tokenBoundariesArtDoesNotBlockStartup() {
        assertThat(blocked("My startup journey", "", "art")).isFalse()
        assertThat(blocked("Watercolor art tutorial", "", "art")).isTrue()
    }

    @Test
    fun singleWordBlockMatchesByTokenAndLemma() {
        assertThat(blocked("Digital art timelapse", "", "art")).isTrue()
        assertThat(blocked("ART SHOWCASE 2026", "", "art")).isTrue()
        assertThat(blocked("Gaming setup tour", "", "game")).isTrue()
        assertThat(blocked("Cartography for beginners", "", "art")).isFalse()
    }

    @Test
    fun multiWordBlockMatchesAsPhrase() {
        assertThat(blocked("Best hip hop tracks of 2026", "", "hip hop")).isTrue()
        assertThat(blocked("Hip mobility exercises", "", "hip hop")).isFalse()
    }

    @Test
    fun channelNamesAreMatchedToo() {
        assertThat(blocked("Weekly recap", "Crypto Daily", "crypto")).isTrue()
        assertThat(blocked("Weekly recap", "Cooking Daily", "crypto")).isFalse()
    }
}
