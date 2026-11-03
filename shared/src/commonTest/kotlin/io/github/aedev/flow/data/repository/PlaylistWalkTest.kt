package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.testing.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PlaylistWalkTest {
    private fun video(id: String) =
        Video(
            id = id,
            title = id,
            channelName = "",
            channelId = "",
            thumbnailUrl = "",
            duration = 0,
            viewCount = 0L,
            uploadDate = "",
        )

    private fun pages(vararg entries: Pair<String, Pair<List<String>, String?>>): Map<String, Pair<List<Video>, String?>> =
        entries.associate { (token, page) -> token to (page.first.map(::video) to page.second) }

    @Test
    fun walksEveryPageUntilThereIsNoNextToken() =
        runTest {
            val book = pages("p2" to (listOf("c", "b") to "p3"), "p3" to (listOf("d") to null))
            val seen = mutableListOf<Int>()

            val walk = walkPlaylistPages(listOf(video("a"), video("b")), "p2", 60, { book[it] }, onPage = { seen += it.size })

            assertThat(walk.finished).isTrue()
            assertThat(walk.videos.map { it.id }).containsExactly("a", "b", "c", "d").inOrder()
            assertThat(seen).containsExactly(3, 4).inOrder()
        }

    @Test
    fun anEmptyPageThatStillPointsFurtherDoesNotEndTheWalk() =
        runTest {
            val book = pages("p2" to (emptyList<String>() to "p3"), "p3" to (listOf("b") to null))

            val walk = walkPlaylistPages(listOf(video("a")), "p2", 60, { book[it] })

            assertThat(walk.finished).isTrue()
            assertThat(walk.videos.map { it.id }).containsExactly("a", "b").inOrder()
        }

    @Test
    fun aFailedPageLeavesTheWalkUnfinished() =
        runTest {
            val book = pages("p2" to (listOf("b") to "p3"))

            val walk = walkPlaylistPages(listOf(video("a")), "p2", 60, { book[it] })

            assertThat(walk.finished).isFalse()
            assertThat(walk.videos.map { it.id }).containsExactly("a", "b").inOrder()
        }

    @Test
    fun aRepeatedTokenOrThePageBudgetStopsAnUnfinishedWalk() =
        runTest {
            var loopRequests = 0
            val looping =
                walkPlaylistPages(listOf(video("a")), "same", 60, {
                    loopRequests++
                    listOf(video("x$loopRequests")) to "same"
                })

            assertThat(looping.finished).isFalse()
            assertThat(loopRequests).isEqualTo(1)

            var budgetRequests = 0
            val capped =
                walkPlaylistPages(listOf(video("a")), "t0", 3, {
                    budgetRequests++
                    listOf(video("y$budgetRequests")) to "t$budgetRequests"
                })

            assertThat(capped.finished).isFalse()
            assertThat(budgetRequests).isEqualTo(2)
        }

    @Test
    fun aPlaylistWithOnePageIsFinishedAtOnce() =
        runTest {
            val walk = walkPlaylistPages(listOf(video("a")), null, 60, { error("no next page") })

            assertThat(walk.finished).isTrue()
        }
}
