/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class NeuroQueryRotationTest {
    private val tokenize = { query: String -> query.split(' ').toSet() }
    private val queries = listOf("claude 5.5", "android iphone", "gym test", "horror anime", "claude horror", "software")

    @Test
    fun freshQueriesLeadAndRecentOnesFillInWhenTooFewAreFresh() {
        val recent = listOf(setOf("claude", "5.5"), setOf("android", "iphone"), setOf("gym", "test"), setOf("horror", "anime"))

        val rotated = NeuroScoring.rotateQueries(queries, recent, tokenize)

        assertThat(
            rotated,
        ).containsExactly("claude horror", "software", "claude 5.5", "android iphone", "gym test", "horror anime").inOrder()
    }

    @Test
    fun enoughFreshQueriesReplaceTheRecentOnes() {
        val rotated = NeuroScoring.rotateQueries(queries, listOf(setOf("claude", "5.5"), setOf("android", "iphone")), tokenize)

        assertThat(rotated).containsExactly("gym test", "horror anime", "claude horror", "software").inOrder()
    }
}
