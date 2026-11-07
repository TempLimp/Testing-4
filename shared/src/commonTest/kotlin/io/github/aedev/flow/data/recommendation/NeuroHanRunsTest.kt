/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.utils.merge

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertWithMessage
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.testing.readerLines
import kotlin.test.Test

/** [NeuroKeySpec] 3: Hant fold and merged Han runs (#1341). */
class NeuroHanRunsTest {
    private val tokenizer = NeuroTokenizer(PlatformWordBreaker)

    private fun merged(title: String) = tokenizer.chunk(title).merged

    private fun video(
        id: String,
        title: String,
    ) = Video(id, title, "", "", "", 600, 1L, uploadDate = "")

    private fun idfOf(titles: List<String>): IdfSnapshot {
        val counts = HashMap<String, Int>()
        titles.forEachIndexed { index, title ->
            val video = video("v$index", title)
            tokenizer
                .documentTerms(
                    video,
                    tokenizer.extractFeatures(video, IdfSnapshot(emptyMap(), 0)),
                ).forEach { counts.merge(it, 1, Int::plus) }
        }
        return IdfSnapshot(counts, titles.size)
    }

    @Test
    fun traditionalCharactersReadAsSimplifiedOnes() {
        assertThat(NeuroText.fold("機器學習")).isEqualTo("机器学习")
        assertThat(tokenizer.chunk("英語學習 如何學好英文").chunks).isEqualTo(tokenizer.chunk("英语学习 如何学好英文").chunks)
        assertThat(tokenizer.tokenizeChannelName("TVBS新聞")).containsExactly("新闻")
    }

    @Test
    fun textWithKanaIsJapaneseAndItsKanjiStay() {
        assertThat(NeuroText.fold("宮崎駿 ジブリ")).isEqualTo("宮崎駿 ジブリ")
        assertThat(NeuroText.fold("宮崎駿")).isEqualTo("宫崎骏")
    }

    @Test
    fun theTableMapsOneCharacterToOneSimplifiedCharacterThatMapsNoFurther() {
        val lines =
            NeuroResources
                .text("fold/hant-hans.txt")!!
                .readerLines()
                .filter { !it.startsWith("#") }
        val keys = lines.map { it[0] }.toSet()
        assertThat(lines.size).isAtLeast(2000)
        lines.forEach { line ->
            assertWithMessage(line).that(line.length).isEqualTo(2)
            assertWithMessage(line).that(line[1] in keys).isFalse()
            assertWithMessage(line).that(NeuroText.fold(line.substring(0, 1))).isEqualTo(line.substring(1))
        }
    }

    @Test
    fun aTitleKeepsTheSpellingItWasWrittenInForDisplay() {
        val spellings = NeuroDisplayForms.spellingsIn("英語學習 入門", listOf("英语 学习"), tokenizer)

        assertThat(spellings).containsExactly("英语 学习", "英語學習")
    }

    @Test
    fun charactersTheBreakerLeavesSingleAreOneCandidateWithTheWordBeforeThem() {
        assertThat(merged("【4K】周杰倫 演唱會 完整版")).containsExactly("周杰伦", "演唱会")
        assertThat(merged("李永樂老師 講解 相對論")).containsExactly("李永乐", "相对论")
        assertThat(tokenizer.chunk("周杰倫 演唱會").chunks).doesNotContain(listOf("周杰伦"))
    }

    @Test
    fun aRunEndsAtABreakCharacterAndHoldsNoStopWordOrFormatWord() {
        assertThat(merged("老高與小茉")).containsExactly("老高", "小茉")
        assertThat(merged("周杰倫的新歌")).containsExactly("周杰伦")
        assertThat(merged("iPhone 17 Pro 開箱評測")).isEmpty()
        assertThat(tokenizer.chunk("iPhone 17 Pro 開箱評測").formats).containsExactly("unboxing", "review")
    }

    @Test
    fun aRunIsTwoToFiveCharacters() {
        assertThat(merged("周杰倫周杰倫")).isEmpty()
        assertThat(merged("周杰")).containsExactly("周杰")
    }

    @Test
    fun charactersApartAreNotOneRun() {
        assertThat(merged("周 杰 倫")).isEmpty()
        assertThat(merged("周，杰，倫")).isEmpty()
    }

    @Test
    fun japaneseAndEarlierSpecsMergeNothing() {
        assertThat(merged("大谷翔平 ホームラン 50号")).isEmpty()
        assertThat(NeuroLanguages.runBreaks("ja")).isNull()
        val spec2 = tokenizer.atKeySpec(2)
        assertThat(spec2.chunk("周杰倫 演唱會").merged).isEmpty()
        assertThat(spec2.chunk("英語學習").chunks).isEqualTo(listOf(listOf("英語", "學習")))
    }

    @Test
    fun aRunIsAKeyOnceItKeepsAppearingAndNotBefore() {
        val others = List(9) { "Tech review number $it" }
        val twice = idfOf(listOf("周杰倫 新歌", "周杰倫 最新作品") + others)
        val thrice = idfOf(listOf("周杰倫 新歌", "周杰倫 最新作品", "周杰倫 演唱會") + others)
        val title = video("new", "周杰倫 新歌")

        assertThat(tokenizer.extractFeatures(title, IdfSnapshot(emptyMap(), 0)).topics).doesNotContainKey("周杰伦")
        assertThat(tokenizer.extractFeatures(title, twice).topics).doesNotContainKey("周杰伦")
        assertThat(tokenizer.extractFeatures(title, thrice).topics).containsKey("周杰伦")
    }

    @Test
    fun aRunWhoseCharactersMostlyAppearElsewhereIsNotAKey() {
        val alone = List(12) { "大 小 号" }
        val idf = idfOf(listOf("大小", "大小", "大小") + alone + List(5) { "other $it" })

        assertThat(NeuroPhrases.isMergedRun("大小", idf)).isFalse()
    }

    @Test
    fun theCharactersOfARunCountTowardDocumentFrequency() {
        val video = video("v", "周杰倫 演唱會")

        val terms = tokenizer.documentTerms(video, tokenizer.extractFeatures(video, IdfSnapshot(emptyMap(), 0)))

        assertThat(terms).containsAtLeast("周杰伦", "周", "杰", "伦")
    }

    @Test
    fun traditionalKeysOfAStoredBrainFoldIntoSimplifiedOnesAndKeepTheirSpelling() {
        val brain =
            UserBrain(
                schemaVersion = 20,
                globalVector = ContentVector(topics = mapOf("機器學習" to 0.4, "机器学习" to 0.2, "python" to 0.3)),
                idfWordFrequency = mapOf("機器學習" to 3, "机器学习" to 4),
                blockedTopics = setOf("新聞"),
            )

        val migrated = NeuroMaintenance.runIfNeeded(brain, tokenizer)

        assertThat(migrated.globalVector.topics.keys).containsAtLeast("机器学习", "python")
        assertThat(migrated.globalVector.topics).doesNotContainKey("機器學習")
        assertThat(migrated.globalVector.topics.getValue("机器学习")).isEqualTo(0.4)
        assertThat(migrated.idfWordFrequency["机器学习"]).isEqualTo(7)
        assertThat(NeuroDisplayForms.label(migrated.displayForms, "机器学习")).isEqualTo("機器學習")
        assertThat(migrated.blockedTopics).containsExactly("新闻")
        assertThat(migrated.schemaVersion).isEqualTo(NeuroMaintenance.KEY_SPEC_3_SCHEMA_VERSION)
    }

    @Test
    fun aJapaneseViewerKeepsTheirKanjiKeys() {
        val japanese = NeuroTokenizer(PlatformWordBreaker).also { it.languageProfile = "ja" }
        val brain = UserBrain(schemaVersion = 20, globalVector = ContentVector(topics = mapOf("宮崎駿" to 0.4)))

        val migrated = NeuroMaintenance.runIfNeeded(brain, japanese)

        assertThat(migrated.globalVector.topics.keys).contains("宮崎駿")
    }

    @Test
    fun theBroadChineseTopicsAreNeverRecordedAsADislike() {
        val news = ContentVector(topics = mapOf("新闻" to 0.9, "台积电" to 0.5))

        val keys = NeuroScoring.extractRejectionKeys(news)

        assertThat(keys).contains("~台积电")
        assertThat(keys).doesNotContain("~新闻")
    }
}
