package io.github.aedev.flow.data.video.downloader.collection

import io.github.aedev.flow.data.local.entity.CollectionItemState
import io.github.aedev.flow.data.local.entity.DownloadCollectionItemEntity
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class CollectionPlanTest {
    private fun member(
        videoId: String,
        position: Int,
        state: CollectionItemState = CollectionItemState.WANTED,
        ownsDownload: Boolean = true,
    ) = DownloadCollectionItemEntity("PL", videoId, position, addedAt = 1L, state = state, ownsDownload = ownsDownload)

    private fun plan(
        ids: List<String>,
        existing: List<DownloadCollectionItemEntity> = emptyList(),
        downloaded: Set<String> = emptySet(),
        complete: Boolean = true,
    ) = CollectionPlan.of("PL", ids, existing, { it in downloaded }, complete, now = 9L)

    @Test
    fun aFirstDownloadQueuesEveryVideoInListOrder() {
        val result = plan(listOf("a", "b", "c"))

        assertThat(result.toQueue).containsExactly("a", "b", "c").inOrder()
        assertThat(result.members.map { it.position }).containsExactly(0, 1, 2).inOrder()
        assertThat(result.members.all { it.ownsDownload && it.addedAt == 9L }).isTrue()
    }

    @Test
    fun aVideoAlreadyDownloadedElsewhereIsReferencedNotQueuedOrOwned() {
        val result = plan(listOf("a", "b"), downloaded = setOf("a"))

        assertThat(result.toQueue).containsExactly("b")
        assertThat(result.members.first { it.videoId == "a" }.ownsDownload).isFalse()
    }

    @Test
    fun aSyncQueuesOnlyWhatIsNewOrStillMissing() {
        val existing = listOf(member("a", 0), member("b", 1))

        val result = plan(listOf("a", "b", "c"), existing, downloaded = setOf("a"))

        assertThat(result.toQueue).containsExactly("b", "c").inOrder()
    }

    @Test
    fun aVideoTheUserExcludedIsNeverQueuedAgain() {
        val existing = listOf(member("a", 0, CollectionItemState.EXCLUDED))

        val result = plan(listOf("a", "b"), existing)

        assertThat(result.toQueue).containsExactly("b")
        assertThat(result.members.first { it.videoId == "a" }.state).isEqualTo(CollectionItemState.EXCLUDED)
    }

    @Test
    fun aVideoGoneFromACompleteListIsMarkedRemovedUpstreamAndKept() {
        val existing = listOf(member("a", 0), member("b", 1))

        val result = plan(listOf("b"), existing, downloaded = setOf("a", "b"))

        val gone = result.members.single { it.videoId == "a" }
        assertThat(gone.state).isEqualTo(CollectionItemState.REMOVED_UPSTREAM)
        assertThat(gone.ownsDownload).isTrue()
        assertThat(result.members.single { it.videoId == "b" }.position).isEqualTo(0)
    }

    @Test
    fun aTruncatedLoadMarksNothingAsRemoved() {
        val existing = listOf(member("a", 0), member("b", 1))

        val result = plan(listOf("b"), existing, downloaded = setOf("a", "b"), complete = false)

        assertThat(result.members.map { it.videoId }).containsExactly("b")
    }

    @Test
    fun aVideoBackInTheListIsWantedAgain() {
        val existing = listOf(member("a", 3, CollectionItemState.REMOVED_UPSTREAM))

        val result = plan(listOf("a"), existing)

        assertThat(result.members.single().state).isEqualTo(CollectionItemState.WANTED)
        assertThat(result.members.single().position).isEqualTo(0)
        assertThat(result.toQueue).containsExactly("a")
    }

    @Test
    fun duplicateIdsInAListBecomeOneMemberAtTheFirstPosition() {
        val result = plan(listOf("a", "b", "a"))

        assertThat(result.members.map { it.videoId }).containsExactly("a", "b").inOrder()
        assertThat(result.toQueue).containsExactly("a", "b").inOrder()
    }
}
