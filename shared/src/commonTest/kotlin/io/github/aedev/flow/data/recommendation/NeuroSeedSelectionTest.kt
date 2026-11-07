/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/** R-2: cluster-diversified seed selection for related-graph retrieval. */
class NeuroSeedSelectionTest {

    private fun seed(id: String, cluster: String, weight: Double) = SeedRank(id, cluster, weight)

    @Test
    fun respectsTheMaxSeedsCap() {
        val seeds = (1..10).map { seed("v$it", "c$it", it.toDouble()) }
        assertThat(NeuroScoring.pickDiverseSeeds(seeds, maxSeeds = 4, maxPerCluster = 2)).hasSize(4)
    }

    @Test
    fun neverExceedsMaxPerClusterWithinACluster() {
        val seeds = (1..6).map { seed("v$it", "music", it.toDouble()) }
        // All in one cluster → capped by cluster, not by maxSeeds.
        assertThat(NeuroScoring.pickDiverseSeeds(seeds, maxSeeds = 4, maxPerCluster = 2)).hasSize(2)
    }

    @Test
    fun prefersHigherWeightSeeds() {
        val seeds = listOf(seed("low", "a", 0.1), seed("high", "a", 0.9), seed("mid", "b", 0.5))
        assertThat(NeuroScoring.pickDiverseSeeds(seeds, maxSeeds = 2, maxPerCluster = 2))
            .containsExactly("high", "mid").inOrder()
    }

    @Test
    fun aBingeInOneClusterDoesNotCrowdOutOtherInterests() {
        val seeds = (1..5).map { seed("binge$it", "gaming", 0.9 - it * 0.01) } +
            seed("other", "cooking", 0.5)
        val picked = NeuroScoring.pickDiverseSeeds(seeds, maxSeeds = 4, maxPerCluster = 2)
        assertThat(picked).contains("other")
        assertThat(picked.count { it.startsWith("binge") }).isAtMost(2)
    }
}
