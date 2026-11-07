/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertWithMessage
import kotlin.test.Test

/** Channel names give topics only for words the engine knows (#1341). */
class NeuroChannelNameTest {
    private val tokenizer = NeuroTokenizer()
    private val icu = NeuroTokenizer(PlatformWordBreaker)

    @Test
    fun spacedNamesKeepTheirTopicalWords() {
        assertThat(tokenizer.tokenizeChannelName("Linus Tech Tips")).containsExactly("tech")
        assertThat(tokenizer.tokenizeChannelName("Python Engineer")).containsExactly("python")
        assertThat(tokenizer.tokenizeChannelName("Fireship")).isEmpty()
        assertThat(tokenizer.tokenizeChannelName("Binging with Babish")).isEmpty()
    }

    @Test
    fun aNameOfAScriptWithoutSpacesHasNoTopicsUnlessItHoldsATopicalWord() {
        assertThat(tokenizer.tokenizeChannelName("")).isEmpty()
        assertThat(tokenizer.tokenizeChannelName("央")).isEmpty()
    }

    @Test
    fun aChineseChannelNameGivesItsTopic() {
        assertThat(tokenizer.tokenizeChannelName("TVBS新聞")).containsExactly("新闻")
        assertThat(icu.tokenizeChannelName("央視新聞")).containsExactly("新闻")
        assertThat(icu.tokenizeChannelName("阿滴英文")).containsExactly("英文")
        assertThat(icu.tokenizeChannelName("英文教室 科技")).containsExactly("英文", "科技")
    }

    @Test
    fun theChineseTopicListIsFoldedWholeWords() {
        val topics = NeuroLanguages.channelTopics
        assertThat(topics).contains("新闻")
        assertThat(topics).doesNotContain("新聞")
        topics.forEach { word ->
            assertWithMessage(word).that(word).isEqualTo(NeuroText.fold(word))
            assertWithMessage(word).that(word.length).isAtLeast(2)
            assertWithMessage(word).that(word.any(Char::isWhitespace)).isFalse()
            assertWithMessage(word).that(NeuroText.words(word, PlatformWordBreaker)).containsExactly(word)
        }
    }

    @Test
    fun noTopicWordIsAlsoAStopWord() {
        val stops = NeuroLanguages.stopList("zh")
        assertThat(NeuroLanguages.channelTopics.filter { it in stops }).isEmpty()
    }
}
