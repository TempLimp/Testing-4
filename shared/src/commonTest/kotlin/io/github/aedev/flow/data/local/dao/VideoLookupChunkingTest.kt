package io.github.aedev.flow.data.local.dao

import io.github.aedev.flow.testing.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Guards #1424: a History lookup after a large import passed SQLite's 999-variable limit. */
class VideoLookupChunkingTest {
    @Test
    fun aLongIdListIsSplitIntoQueriesThatStayUnderTheVariableLimit() =
        runTest {
            val ids = (1..2_500).map { "video$it" }
            val calls = mutableListOf<List<String>>()

            val rows = chunkedLookup(ids) { chunk -> chunk.also { calls += it } }

            assertThat(calls).hasSize(5)
            assertThat(calls.map { it.size }.max()).isAtMost(MAX_IDS_PER_QUERY)
            assertThat(rows).containsExactlyElementsIn(ids).inOrder()
        }

    @Test
    fun aRepeatedIdIsAskedForOnce() =
        runTest {
            val calls = mutableListOf<List<String>>()

            chunkedLookup(listOf("a", "b", "a", "b", "c")) { chunk -> chunk.also { calls += it } }

            assertThat(calls).containsExactly(listOf("a", "b", "c"))
        }

    @Test
    fun noIdsSendsNoQuery() =
        runTest {
            var called = false

            val rows = chunkedLookup(emptyList()) { chunk -> chunk.also { called = true } }

            assertThat(called).isFalse()
            assertThat(rows).isEmpty()
        }
}
