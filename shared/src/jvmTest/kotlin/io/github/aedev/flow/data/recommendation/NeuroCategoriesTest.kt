/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** #907: YouTube's category lowers a foreign video a little, never more. */
class NeuroCategoriesTest {
    private val musicViewer = mapOf("music" to 40.0, "comedy" to 10.0)

    private fun video(
        id: String,
        channelId: String,
        category: String? = null,
    ) = Video(id, "Lofi beats to relax", "Channel $channelId", channelId, "", 1800, 400_000, uploadDate = "3 days ago", category = category)

    @Test
    fun categoriesAreReadAsTheMicroformatWritesThem() {
        assertThat(NeuroCategories.idOf("Science & Technology")).isEqualTo("science")
        assertThat(NeuroCategories.idOf(" music ")).isEqualTo("music")
        assertThat(NeuroCategories.idOf("Shows")).isNull()
        assertThat(NeuroCategories.idOf(null)).isNull()
    }

    @Test
    fun aForeignCategoryTakesAtMostAThirdOffAFamiliarOneNothing() {
        assertThat(NeuroCategories.factor(musicViewer, ChannelCategory("gaming", 1.0))).isWithin(1e-9).of(1 - NeuroCategories.MAX_PENALTY)
        assertThat(NeuroCategories.factor(musicViewer, ChannelCategory("comedy", 1.0))).isEqualTo(1.0)
        assertThat(NeuroCategories.factor(musicViewer, ChannelCategory("gaming", 0.5))).isGreaterThan(1 - NeuroCategories.MAX_PENALTY)
    }

    @Test
    fun aDefaultCategorySaysLessThanAChosenOne() {
        val entertainment = NeuroCategories.factor(musicViewer, ChannelCategory("entertainment", 1.0))

        assertThat(entertainment).isGreaterThan(NeuroCategories.factor(musicViewer, ChannelCategory("gaming", 1.0)))
    }

    @Test
    fun nothingChangesBeforeTwentyCategorizedWatches() {
        val few = (1..19).fold(emptyMap<String, Double>()) { mix, _ -> NeuroCategories.learn(mix, "music") }

        assertThat(NeuroCategories.factor(few, ChannelCategory("gaming", 1.0))).isEqualTo(1.0)
        assertThat(NeuroCategories.factor(NeuroCategories.learn(few, "music"), ChannelCategory("gaming", 1.0))).isLessThan(1.0)
    }

    @Test
    fun theMixFollowsWhatTheViewerWatchesNow() {
        val old = (1..400).fold(emptyMap<String, Double>()) { mix, _ -> NeuroCategories.learn(mix, "gaming") }
        val now = (1..400).fold(old) { mix, _ -> NeuroCategories.learn(mix, "music") }

        assertThat(now.getValue("music")).isGreaterThan(now.getValue("gaming") * 2)
        assertThat(now.values.sum()).isAtMost(400.0)
    }

    @Test
    fun aChannelSCategoryFirmsUpWithEveryVideoSeenInIt() {
        val store = NeuroCategoryStore()
        store.record("ch", "gaming")
        val once = store.categoryOf("ch")!!.confidence
        repeat(4) { store.record("ch", "gaming") }

        assertThat(store.categoryOf("ch")!!.confidence).isGreaterThan(once)
        assertThat(store.categoryOf("unknown")).isNull()
    }

    @Test
    fun aWatchedVideoTeachesItsChannelSCategoryAndTheViewerSMix() =
        runBlocking {
            val store = NeuroCategoryStore()
            val engine = NeuroLearningBenchmark.engine(categoryStore = store)

            engine.onVideoInteraction(video("v", "ch-music", category = "Music"), InteractionType.WATCHED, 0.9f)
            engine.onVideoInteraction(video("s", "ch-shorts", category = "Comedy").copy(isShort = true), InteractionType.WATCHED, 0.9f)
            engine.onVideoInteraction(video("k", "ch-news", category = "News & Politics"), InteractionType.SKIPPED)

            assertThat(store.categoryOf("ch-music")?.id).isEqualTo("music")
            assertThat(store.categoryOf("ch-news")?.id).isEqualTo("news")
            assertThat(engine.getBrainSnapshot().categoryAffinity).containsExactly("music", 1.0)
            engine.shutdown()
        }

    @Test
    fun threeGamesReadAsOneGamingChipThatSearchesEachGame() =
        runBlocking<Unit> {
            val store = NeuroCategoryStore()
            listOf("ch-mc", "ch-cod", "ch-fn").forEach { channel -> repeat(3) { store.record(channel, "gaming") } }
            repeat(3) { store.record("ch-lofi", "music") }
            val evidence = TopicEvidence(watchSignals = 3, positiveSignals = 3, positiveScore = 2.0)
            val topics = mapOf("minecraft" to 0.6, "call of duty" to 0.55, "fortnite" to 0.5, "lofi" to 0.45)
            val brain =
                UserBrain(
                    globalVector = ContentVector(topics = topics),
                    channelTopicProfiles =
                        mapOf(
                            "ch-mc" to mapOf("minecraft" to 0.6),
                            "ch-cod" to mapOf("call of duty" to 0.6),
                            "ch-fn" to mapOf("fortnite" to 0.6),
                            "ch-lofi" to mapOf("lofi" to 0.6),
                        ),
                    topicEvidence = topics.mapValues { evidence },
                    totalInteractions = 100,
                )
            val engine = NeuroLearningBenchmark.engine(brain, categoryStore = store)

            val chips = engine.interestChips(1_800_000_000_000L)
            engine.shutdown()

            assertThat(chips.map { it.category }).containsExactly("gaming", null).inOrder()
            assertThat(chips.first().parts.map { it.representative }).containsExactly("minecraft", "call of duty", "fortnite")
            assertThat(chips.last().label).isEqualTo("Lofi")
        }

    @Test
    fun aVideoFromAChannelInAForeignCategoryRanksBelowAnEqualOneUnlessSubscribed() =
        runBlocking {
            val store = NeuroCategoryStore()
            repeat(3) { store.record("ch-game", "gaming") }
            val brain =
                UserBrain(
                    globalVector = ContentVector(topics = mapOf("lofi" to 0.5, "beat" to 0.4)),
                    categoryAffinity = musicViewer,
                    totalInteractions = 100,
                )
            val engine = NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER, store)
            val gaming = video("game", "ch-game")
            val unknown = video("other", "ch-new")

            assertThat(engine.rank(listOf(gaming, unknown), emptySet()).first().id).isEqualTo("other")
            assertThat(engine.rank(listOf(gaming, unknown), setOf("ch-game")).first().id).isEqualTo("game")
            engine.shutdown()
        }
}
