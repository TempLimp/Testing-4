/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/** Description lines are read whatever their script; only shouting and bare lines are skipped (#1395). */
class NeuroDescriptionKeywordsTest {
    private val tokenizer = NeuroTokenizer(PlatformWordBreaker)
    private val empty = IdfSnapshot(emptyMap(), 0)

    private fun keywords(description: String) = tokenizer.extractDescriptionKeywords(description, empty).keys

    @Test
    fun aChineseDescriptionGivesKeywords() {
        assertThat(keywords("周杰倫 演唱會 台北小巨蛋 現場完整收錄 精彩片段一次看")).contains("台北")
    }

    @Test
    fun aJapaneseDescriptionGivesKeywords() {
        assertThat(keywords("今日は東京で一番美味しいラーメン屋さんを紹介します")).isNotEmpty()
    }

    @Test
    fun capitalsInsideAChineseLineDoNotMakeItShouting() {
        assertThat(keywords("【MV】周杰倫 演唱會 台北小巨蛋 現場完整收錄")).contains("台北")
    }

    @Test
    fun anAllCapsLineIsStillSkipped() {
        val found = keywords("SUBSCRIBE NOW FOR MORE VIDEOS\nLearning guitar chords for beginners today")

        assertThat(found).contains("guitar")
        assertThat(found).doesNotContain("subscribe")
    }

    @Test
    fun aLineWithoutLettersTakesNoLineOfTheDescription() {
        val separators = List(NeuroTokenizer.DESCRIPTION_TAKE_LINES) { "━━━━━━━━━━━━━━━━━━━━" }

        assertThat(keywords((separators + "Learning guitar chords for beginners today").joinToString("\n"))).contains("guitar")
    }
}
