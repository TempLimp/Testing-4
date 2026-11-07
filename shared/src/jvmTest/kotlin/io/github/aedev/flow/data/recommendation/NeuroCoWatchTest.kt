/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import kotlin.time.Clock
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** #907: channels watched together, from the related lists of real watches. */
class NeuroCoWatchTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60L * 60L * 1000L
    private val nobodyWatched = { _: String -> false }

    private fun source(
        channel: String,
        vararg related: String,
        at: Long = now,
    ) = NeuroCoWatchStore.Source(channel, related.toList(), at)

    private fun video(
        id: String,
        channelId: String,
        title: String = "Sourdough bread for beginners",
    ) = Video(id, title, "Channel $channelId", channelId, "", 900, 400_000, uploadDate = "3 days ago")

    private fun uploadsOf(channels: List<String>) = channels.mapIndexed { i, channel -> video("r$i", channel) }

    @Test
    fun oneRelatedListIsNoiseTwoDifferentVideosMakeAPair() {
        val once = NeuroCoWatchStore.edges(listOf(source("bake", "pasta", "cars")), nobodyWatched, now)
        val twice = NeuroCoWatchStore.edges(listOf(source("bake", "pasta", "cars"), source("bake", "pasta")), nobodyWatched, now)

        assertThat(once).isEmpty()
        assertThat(twice.keys).containsExactly(NeuroCoWatchStore.pairKey("bake", "pasta"))
        assertThat(twice.values.single()).isEqualTo(1.0)
    }

    @Test
    fun aPairSeenOnceCountsWhenTheViewerWatchedBothChannels() {
        val edges = NeuroCoWatchStore.edges(listOf(source("bake", "pasta", "cars")), { it in setOf("bake", "pasta") }, now)

        assertThat(edges.keys).containsExactly(NeuroCoWatchStore.pairKey("bake", "pasta"))
        assertThat(edges.values.single()).isEqualTo(0.5)
    }

    @Test
    fun pairsFadeWithA45DayHalfLife() {
        val old =
            NeuroCoWatchStore.edges(
                listOf(source("bake", "pasta", at = now - 45 * day), source("pasta", "bake", at = now - 45 * day)),
                nobodyWatched,
                now,
            )

        assertThat(old.values.single()).isWithin(1e-9).of(0.5)
    }

    @Test
    fun aChannelNextToOneTheViewerWatchesIsANeighbourAChannelNextToNothingIsNot() {
        val edges = mapOf(NeuroCoWatchStore.pairKey("bake", "pasta") to 1.0, NeuroCoWatchStore.pairKey("cars", "trucks") to 1.0)

        assertThat(NeuroCoWatchStore.neighbours(edges) { it == "bake" }).containsExactly("pasta", 1.0)
    }

    @Test
    fun aSourceKeepsTenOtherChannelsAndTheStoreTwoHundredSources() {
        val store = NeuroCoWatchStore()
        store.record("v", "bake", uploadsOf(listOf("bake") + (1..12).map { "ch$it" } + listOf("ch1", "")), now)
        repeat(NeuroCoWatchStore.MAX_SOURCES) { store.record("s$it", "bake", uploadsOf(listOf("pasta")), now + it + 1) }

        assertThat(store.sources()).hasSize(NeuroCoWatchStore.MAX_SOURCES)
        assertThat(store.sources().none { it.related.size > NeuroCoWatchStore.CHANNELS_PER_SOURCE }).isTrue()
        assertThat(store.sources().none { "bake" in it.related }).isTrue()
    }

    @Test
    fun aRelatedListIsKeptOnlyOnceItsVideoIsReallyWatched() =
        runBlocking<Unit> {
            val store = NeuroCoWatchStore()
            val engine = NeuroLearningBenchmark.engine(coWatchStore = store)
            val related = listOf(video("r1", "pasta"), video("r2", "cars"))
            val watched = video("w", "bake")
            val skipped = video("s", "bake")

            engine.noteRelated(watched.id, related)
            engine.noteRelated(skipped.id, related)
            engine.onVideoInteraction(skipped, InteractionType.WATCHED, 0.2f)
            engine.onVideoInteraction(watched, InteractionType.WATCHED, 0.9f)
            engine.shutdown()

            assertThat(
                store.sources(),
            ).containsExactly(
                NeuroCoWatchStore.Source(
                    "bake",
                    listOf("pasta", "cars"),
                    store.sources().single().at,
                    mapOf("pasta" to "Channel pasta", "cars" to "Channel cars"),
                ),
            )
        }

    @Test
    fun feedSeedsTeachNothingHistorySeedsDo() =
        runBlocking<Unit> {
            val store = NeuroCoWatchStore()
            val engine = NeuroLearningBenchmark.engine(coWatchStore = store)

            fun seed(
                id: String,
                source: GraphSeedSource,
            ) = GraphSeedInput(id, "Bread", "bake", source, 1.0, now, 600, 90.0)
            engine.noteRelatedOfWatched(seed("feed", GraphSeedSource.FEED), listOf(video("r", "pasta")))
            engine.noteRelatedOfWatched(seed("hist", GraphSeedSource.WATCH_HISTORY), listOf(video("r", "pasta")))
            engine.shutdown()

            assertThat(store.sources().map { it.related }).containsExactly(listOf("pasta"))
        }

    @Test
    fun aChannelWatchedAlongsideTheViewerSOwnRanksAboveAnEqualStrangerByASmallPrior() =
        runBlocking<Unit> {
            val store = NeuroCoWatchStore()
            store.record("a", "bake", uploadsOf(listOf("pasta")), Clock.System.now().toEpochMilliseconds())
            store.record("b", "bake", uploadsOf(listOf("pasta")), Clock.System.now().toEpochMilliseconds())
            val brain =
                UserBrain(
                    globalVector = ContentVector(topics = mapOf("sourdough" to 0.5, "bread" to 0.4)),
                    channelScores = mapOf("bake" to 0.8),
                    totalInteractions = 100,
                )
            val engine = NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER, coWatchStore = store)

            val ranked = engine.rank(listOf(video("stranger", "nobody"), video("neighbour", "pasta")), emptySet())
            engine.shutdown()

            assertThat(ranked.first().id).isEqualTo("neighbour")
        }

    @Test
    fun channelsWatchedTogetherGlueTheirTopicsIntoOneCluster() {
        val profiles = mapOf("bake" to mapOf("sourdough" to 0.6), "pasta" to mapOf("carbonara" to 0.6))
        val scores = mapOf("sourdough" to 0.5, "carbonara" to 0.5)

        fun clusters(edges: Map<String, Double>) =
            NeuroClusters.buildClusters(scores, emptyMap(), profiles, emptyList(), { it }, channelEdges = edges).size

        assertThat(clusters(emptyMap())).isEqualTo(2)
        assertThat(clusters(mapOf(NeuroCoWatchStore.pairKey("bake", "pasta") to 1.0))).isEqualTo(1)
    }

    @Test
    fun aSourceKeepsTheNamesOfItsRelatedChannels() {
        val store = NeuroCoWatchStore()
        store.record("v", "bake", uploadsOf(listOf("pasta", "cars")), now)

        assertThat(store.channelNames()).containsExactly("pasta", "Channel pasta", "cars", "Channel cars")
    }
}
