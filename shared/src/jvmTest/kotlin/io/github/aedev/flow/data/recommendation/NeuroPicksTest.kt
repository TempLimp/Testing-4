/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** Picks are keyed the way titles key them, so Your taste can show and undo them (#907). */
class NeuroPicksTest {
    @Test
    fun onboardingPicksAreKeysTitlesProduceSpelledAsChosenAndNotLinkedToEachOther() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine()
            engine.completeOnboarding(setOf("Machine Learning", "Lo-Fi", "Cooking"))
            val brain = engine.getBrainSnapshot()
            engine.shutdown()

            assertThat(brain.globalVector.topics.keys).containsAtLeast("machine learn", "lofi")
            assertThat(NeuroDisplayForms.label(brain.displayForms, "lofi")).isEqualTo("Lo-Fi")
            assertThat(brain.topicAffinities).isEmpty()
        }

    @Test
    fun stopBoostingRemovesAPickByTheKeyYourTasteShows() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine()
            engine.completeOnboarding(setOf("Web Development", "Anime"))
            engine.removePreferredTopic("web development")
            val brain = engine.getBrainSnapshot()
            engine.shutdown()

            assertThat(brain.preferredTopics).containsExactly("Anime")
        }

    @Test
    fun boostingNeverLowersAWeight() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine(UserBrain(globalVector = ContentVector(topics = mapOf("chess" to 0.8))))
            engine.addPreferredTopic("chess")
            val weight =
                engine
                    .getBrainSnapshot()
                    .globalVector.topics
                    .getValue("chess")
            engine.shutdown()

            assertThat(weight).isEqualTo(0.8)
        }

    @Test
    fun picksMadeBeforeSpellingsWereKeptReadAsChosen() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine(UserBrain(preferredTopics = setOf("R&B", "K-Pop", "Guitar", "Call of Duty")))
            val forms = engine.getBrainSnapshot().displayForms
            engine.shutdown()

            assertThat(listOf("rnb", "kpop", "guitar", "call of duty").map { NeuroDisplayForms.label(forms, it) })
                .containsExactly("R&B", "K-Pop", "Guitar", "Call of Duty")
        }
}
