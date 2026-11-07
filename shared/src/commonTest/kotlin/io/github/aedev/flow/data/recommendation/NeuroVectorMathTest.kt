/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class NeuroVectorMathTest {
    private fun vec(vararg topics: Pair<String, Double>) = ContentVector(topics = topics.toMap())

    @Test
    fun identicalTopicVectorsScoreNear1() {
        val sim =
            NeuroVectorMath.calculateCosineSimilarity(
                vec("python" to 1.0),
                vec("python" to 1.0),
            )
        assertThat(sim).isWithin(1e-9).of(1.0)
    }

    @Test
    fun topicSimilarityCountsSharedTopicsOnly() {
        val user = NeuroVectorMath.PreparedVector(vec("python" to 0.6, "cooking:food" to 0.8))

        assertThat(NeuroVectorMath.topicSimilarity(user, vec("chess" to 1.0))).isEqualTo(0.0)
        assertThat(NeuroVectorMath.topicSimilarity(user, vec("python" to 1.0))).isWithin(1e-9).of(0.6)
        assertThat(NeuroVectorMath.topicSimilarity(user, vec("cooking" to 1.0))).isWithin(1e-9).of(0.8 * 0.3)
    }

    @Test
    fun noTopicIntersectionYieldsADampedScalarOnlyScore() {
        // Scalars are equal (0.5) so the raw scalar score is 0.30; with no topic
        // overlap it is damped (I-11a) so off-topic content can be floored.
        val sim =
            NeuroVectorMath.calculateCosineSimilarity(
                vec("python" to 1.0),
                vec("cooking" to 1.0),
            )
        assertThat(sim).isWithin(1e-9).of(0.30 * NeuroVectorMath.SCALAR_ONLY_DAMP)
    }

    @Test
    fun offTopicSimilarityNowFallsBelowTheModerateRelevanceFloor() {
        val sim =
            NeuroVectorMath.calculateCosineSimilarity(
                vec("python" to 1.0),
                vec("cooking" to 1.0),
            )
        assertThat(sim).isLessThan(NeuroScoring.RELEVANCE_FLOOR_MODERATE_THRESHOLD)
    }

    @Test
    fun taggedAndUntaggedTopicsPartialMatchBelowAnExactMatch() {
        val partial =
            NeuroVectorMath.calculateCosineSimilarity(
                vec("metal" to 1.0),
                vec("metal:music" to 1.0),
            )
        assertThat(partial).isGreaterThan(0.30) // beats scalar-only
        assertThat(partial).isLessThan(1.0) // but below an exact match
    }

    @Test
    fun establishedTopicsDecaySlowerThanEmergingOnesWhenOffTarget() {
        val v = ContentVector(topics = mapOf("established" to 0.5, "emerging" to 0.05))
        val out = NeuroVectorMath.adjustVector(v, ContentVector(topics = mapOf("z" to 1.0)), 0.1)
        val establishedRatio = out.topics.getValue("established") / 0.5
        val emergingRatio = out.topics.getValue("emerging") / 0.05
        assertThat(establishedRatio).isGreaterThan(emergingRatio)
    }

    @Test
    fun positiveLearningMovesAnAbsentTopicTowardTheTarget() {
        val out =
            NeuroVectorMath.adjustVector(
                ContentVector(),
                ContentVector(topics = mapOf("newtopic" to 1.0)),
                0.2,
            )
        assertThat(out.topics["newtopic"]).isNotNull()
        assertThat(out.topics.getValue("newtopic")).isGreaterThan(0.0)
    }

    @Test
    fun tinyOffTargetTopicsArePrunedBelowThreshold() {
        val v = ContentVector(topics = mapOf("keep" to 0.5, "drop" to 0.030))
        val out = NeuroVectorMath.adjustVector(v, ContentVector(topics = mapOf("z" to 1.0)), 0.1)
        assertThat(out.topics).containsKey("keep")
        assertThat(out.topics).doesNotContainKey("drop")
    }

    @Test
    fun negativeLearningNeverIncreasesATargetTopic() {
        val v = ContentVector(topics = mapOf("dislike" to 0.6))
        val out = NeuroVectorMath.adjustVector(v, ContentVector(topics = mapOf("dislike" to 1.0)), -0.4)
        assertThat(out.topics.getValue("dislike")).isLessThan(0.6)
    }

    @Test
    fun titleSimilarityIsJaccardOverTokenSets() {
        val sim =
            NeuroVectorMath.calculateTitleSimilarity(
                setOf("a", "b", "c"),
                setOf("b", "c", "d"),
            )
        assertThat(sim).isWithin(1e-9).of(0.5)
    }

    @Test
    fun wordsOfAPhraseLearnAtAShareOfTheirWeightAndPhrasesInFull() {
        val learning =
            NeuroVectorMath.phraseFirst(
                vec("google" to 0.4, "pixel" to 0.4, "google pixel" to 0.6, "review" to 0.3),
            )

        assertThat(learning.topics["google pixel"]).isWithin(1e-9).of(0.6)
        assertThat(learning.topics["google"]).isWithin(1e-9).of(0.4 * NeuroVectorMath.PHRASE_WORD_SHARE)
        assertThat(learning.topics["pixel"]).isWithin(1e-9).of(0.4 * NeuroVectorMath.PHRASE_WORD_SHARE)
        assertThat(learning.topics["review"]).isWithin(1e-9).of(0.3)
    }

    @Test
    fun aTitleWithoutPhrasesIsLearnedUnchanged() {
        val vector = vec("minecraft" to 0.7)

        assertThat(NeuroVectorMath.phraseFirst(vector)).isSameInstanceAs(vector)
    }
}
