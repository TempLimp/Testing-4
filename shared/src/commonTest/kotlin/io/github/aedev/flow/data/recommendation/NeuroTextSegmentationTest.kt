/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

/**
 * Titles in scripts written without spaces split into real words. Upstream runs it on a device, where the
 * word dictionaries live in the platform ICU data; the port's breaker is ICU on both targets (ICU4J on jvm,
 * the system's libicucore on iOS), so it runs on each.
 */
class NeuroTextSegmentationTest {
    private val tokenizer = NeuroTokenizer()

    private fun assertTopics(
        title: String,
        vararg expected: String,
    ) {
        val tokens = tokenizer.tokenize(title)
        expected.forEach { assertTrue("$it not in $tokens for \"$title\"", it in tokens) }
    }

    // Upstream expects 教学 here, but at the pin NeuroFormats reads 教学 as the "tutorial" format, never a topic, and
    // 入门 is a Chinese stop word: 基础, the other word the dictionary splits out of the run, stands in for it.
    @Test
    fun chineseTitlesSplitIntoWords() = assertTopics("吉他教学零基础入门", "吉他", "基础")

    @Test
    fun japaneseTitlesKeepContentWordsAndDropParticles() {
        val tokens = tokenizer.tokenize("ギターの弾き方を初心者向けに解説")
        assertTrue(tokens.toString(), "ギター" in tokens)
        assertTrue(tokens.toString(), tokens.none { it == "の" || it == "を" })
    }

    @Test
    fun thaiTitlesSplitIntoWords() = assertTopics("สอนเล่นกีตาร์สำหรับมือใหม่", "กีตาร์", "สำหรับ")

    @Test
    fun chineseChannelNamesGiveTheirTopicalWords() {
        // Upstream expects 新聞 for 央視新聞, but at the pin Traditional Han folds to Simplified, and its own unit test
        // (NeuroChannelNameTest) wants exactly 新闻 for that name: the expectation follows the unit test.
        listOf("央視新聞" to "新闻", "央视新闻" to "新闻", "英文教室 科技" to "科技").forEach { (name, topic) ->
            val topics = tokenizer.tokenizeChannelName(name)
            assertTrue("$topic not in $topics for \"$name\"", topic in topics)
        }
        assertTrue(tokenizer.tokenizeChannelName("小明").isEmpty())
    }

    @Test
    fun spacedScriptsAreUntouched() = assertTopics("Pixel 10 Pro camera test", "pixel", "camera")
}
