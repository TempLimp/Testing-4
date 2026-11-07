/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** One video is one document for phrase detection, however it is opened, watched and liked (#907). */
class NeuroDocumentCountTest {
    private val video = Video("q1", "Quantum gravity explained simply", "Physics Lab", "ch-physics", "", 900, 1L, uploadDate = "")

    private suspend fun FlowNeuroEngine.openWatchAndLike() {
        onVideoInteraction(video, InteractionType.CLICK)
        onVideoInteraction(video, InteractionType.WATCHED, 0.9f)
        onVideoInteraction(video, InteractionType.LIKED)
    }

    @Test
    fun openingWatchingAndLikingOneVideoCountsItOnce() =
        runBlocking {
            val engine = NeuroLearningBenchmark.engine(UserBrain(idfTotalDocuments = 100))
            engine.openWatchAndLike()
            val brain = engine.getBrainSnapshot()
            engine.shutdown()

            assertThat(brain.idfTotalDocuments).isEqualTo(101)
            assertThat(brain.idfWordFrequency.values.max()).isEqualTo(1)
            assertThat(NeuroPhrases.isPhrase("quantum gravity", IdfSnapshot(brain.idfWordFrequency, brain.idfTotalDocuments))).isFalse()
        }

    @Test
    fun watchingTheSameVideoAgainAfterARestartAddsNoDocument() =
        runBlocking {
            val first = NeuroLearningBenchmark.engine(UserBrain(idfTotalDocuments = 100))
            first.openWatchAndLike()
            val saved = first.getBrainSnapshot()
            first.shutdown()

            val second = NeuroLearningBenchmark.engine(saved)
            second.onVideoInteraction(video, InteractionType.WATCHED, 1f)
            val brain = second.getBrainSnapshot()
            second.shutdown()

            assertThat(brain.idfTotalDocuments).isEqualTo(101)
        }
}
