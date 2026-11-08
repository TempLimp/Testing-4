package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import kotlin.test.Test

/**
 * The queue decides what plays next and what the queue sheet shows, and the two have to agree: an
 * index that drifts from the published list makes the sheet highlight one video and play another.
 * None of this was reachable from a test while the state lived inside `EnhancedPlayerManager`.
 */
class PlaybackQueueControllerTest {
    private fun video(id: String) =
        Video(
            id = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC123",
            thumbnailUrl = "https://example.test/$id.jpg",
            duration = 120,
            viewCount = 10L,
            uploadDate = "today",
        )

    private fun ids(videos: List<Video>) = videos.map(Video::id)

    private fun controllerOf(
        vararg ids: String,
        startIndex: Int = 0,
    ): PlaybackQueueController =
        PlaybackQueueController().apply {
            setQueue(ids.map(::video), startIndex, title = "Playlist")
        }

    @Test
    fun setQueueStartsAtTheRequestedIndexAndPublishesTheQueue() {
        val controller = PlaybackQueueController()

        val start = controller.setQueue(listOf(video("a"), video("b"), video("c")), startIndex = 1, title = "Playlist")

        assertThat(start?.id).isEqualTo("b")
        assertThat(controller.currentIndex).isEqualTo(1)
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
        assertThat(controller.currentIndexState.value).isEqualTo(1)
        assertThat(controller.title).isEqualTo("Playlist")
    }

    @Test
    fun setQueueOnAnEmptyListParksTheIndexInsteadOfPointingAtAVideo() {
        val controller = PlaybackQueueController()

        val start = controller.setQueue(emptyList(), startIndex = 0, title = null)

        assertThat(start).isNull()
        assertThat(controller.currentIndex).isEqualTo(-1)
        assertThat(controller.isEmpty).isTrue()
    }

    @Test
    fun setQueueWhileShuffledPlaysTheTappedVideoAndPublishesTheOrderItPlays() {
        val controller = controllerOf("a", "b", "c")
        controller.setShuffleEnabled(true)

        val start = controller.setQueue(listOf(video("x"), video("y"), video("z")), startIndex = 2, title = null)

        // The published list, the index and the video handed back must describe the same item, or
        // the queue sheet highlights one row and playback starts on another.
        assertThat(start?.id).isEqualTo("z")
        assertThat(controller.videos.value[controller.currentIndex].id).isEqualTo("z")
        assertThat(ids(controller.videos.value)).containsExactly("x", "y", "z")
    }

    @Test
    fun moveToOutOfRangeLeavesThePositionAlone() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        assertThat(controller.moveTo(5)).isNull()
        assertThat(controller.moveTo(-1)).isNull()
        assertThat(controller.currentIndex).isEqualTo(1)
    }

    @Test
    fun movePreviousStopsAtTheHeadOfTheQueue() {
        val controller = controllerOf("a", "b", startIndex = 1)

        assertThat(controller.movePrevious()?.id).isEqualTo("a")
        assertThat(controller.movePrevious()).isNull()
        assertThat(controller.currentIndex).isEqualTo(0)
    }

    @Test
    fun hasNextFollowsTheLoopSettingAtTheEndOfTheQueue() {
        val controller = controllerOf("a", "b", startIndex = 1)

        assertThat(controller.hasNext).isFalse()
        assertThat(controller.nextVideo()).isNull()

        controller.setLoopEnabled(true)

        assertThat(controller.hasNext).isTrue()
        assertThat(controller.nextVideo()?.id).isEqualTo("a")
    }

    @Test
    fun addNextInsertsStraightAfterTheCurrentVideo() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        val outcome = controller.addNext(video("x"), currentlyPlaying = null)

        assertThat(outcome).isEqualTo(QueueAddOutcome.Inserted)
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "x", "c").inOrder()
        assertThat(controller.currentVideo?.id).isEqualTo("b")
    }

    @Test
    fun appendAddsToTheEndWithoutMovingTheCurrentVideo() {
        val controller = controllerOf("a", "b", startIndex = 1)

        val outcome = controller.append(video("x"), currentlyPlaying = null)

        assertThat(outcome).isEqualTo(QueueAddOutcome.Inserted)
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "x").inOrder()
        assertThat(controller.currentIndex).isEqualTo(1)
    }

    @Test
    fun queueingAgainstAnEmptyQueueKeepsThePlayingVideoAsTheFirstEntry() {
        val controller = PlaybackQueueController()

        val outcome = controller.addNext(video("x"), currentlyPlaying = video("playing"))

        assertThat(outcome).isEqualTo(QueueAddOutcome.QueueCreated)
        assertThat(ids(controller.videos.value)).containsExactly("playing", "x").inOrder()
        assertThat(controller.currentIndex).isEqualTo(0)
    }

    @Test
    fun queueingWithNothingPlayingAndNothingQueuedReportsThereIsNoQueue() {
        val controller = PlaybackQueueController()

        val outcome = controller.append(video("x"), currentlyPlaying = null)

        assertThat(outcome).isEqualTo(QueueAddOutcome.NoActiveQueue)
        assertThat(controller.isEmpty).isTrue()
        assertThat(controller.currentIndex).isEqualTo(-1)
    }

    @Test
    fun removingAnEarlierVideoKeepsTheSameVideoCurrent() {
        val controller = controllerOf("a", "b", "c", startIndex = 2)

        assertThat(controller.removeAt(0)).isNotNull()
        assertThat(controller.currentIndex).isEqualTo(1)
        assertThat(controller.currentVideo?.id).isEqualTo("c")
        assertThat(controller.currentIndexState.value).isEqualTo(1)
    }

    @Test
    fun theCurrentlyPlayingVideoCannotBeRemovedOutFromUnderPlayback() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        assertThat(controller.removeAt(1)).isNull()
        assertThat(controller.removeAt(9)).isNull()
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun undoingARemovalBeforeTheCurrentVideoPutsItBackAndKeepsTheSameVideoPlaying() {
        val controller = controllerOf("a", "b", "c", startIndex = 2)

        val removed = controller.removeAt(0)!!

        assertThat(controller.restore(removed)).isTrue()
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
        assertThat(controller.currentVideo?.id).isEqualTo("c")
        assertThat(controller.currentIndex).isEqualTo(2)
    }

    @Test
    fun undoingARemovalAfterTheCurrentVideoLeavesThePositionAlone() {
        val controller = controllerOf("a", "b", "c", startIndex = 0)

        controller.restore(controller.removeAt(2)!!)

        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
        assertThat(controller.currentIndex).isEqualTo(0)
    }

    @Test
    fun aRemovalUndoneWhileShuffledComesBackInTheOriginalOrderToo() {
        val controller = controllerOf("a", "b", "c", "d", startIndex = 0)
        controller.setShuffleEnabled(true)
        val shuffled = ids(controller.videos.value)

        controller.restore(controller.removeAt(shuffled.indexOf("c"))!!)

        assertThat(ids(controller.videos.value)).containsExactlyElementsIn(shuffled).inOrder()
        controller.setShuffleEnabled(false)
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c", "d").inOrder()
    }

    @Test
    fun anUndoFromAQueueThatHasSinceBeenReplacedDoesNothing() {
        val controller = controllerOf("a", "b", "c")
        val removed = controller.removeAt(1)!!

        controller.setQueue(listOf(video("x"), video("y")), startIndex = 0, title = "Other")

        assertThat(controller.restore(removed)).isFalse()
        assertThat(ids(controller.videos.value)).containsExactly("x", "y").inOrder()
    }

    @Test
    fun turningShuffleOffRestoresTheOriginalOrderAndStaysOnTheCurrentVideo() {
        val controller = controllerOf("a", "b", "c", "d", startIndex = 2)

        assertThat(controller.setShuffleEnabled(true)).isTrue()
        assertThat(controller.currentVideo?.id).isEqualTo("c")

        assertThat(controller.setShuffleEnabled(false)).isTrue()
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c", "d").inOrder()
        assertThat(controller.currentVideo?.id).isEqualTo("c")
    }

    @Test
    fun aVideoQueuedWhileShuffledSurvivesTurningShuffleOff() {
        val controller = controllerOf("a", "b", "c")
        controller.setShuffleEnabled(true)

        controller.addNext(video("x"), currentlyPlaying = null)
        controller.append(video("y"), currentlyPlaying = null)
        controller.setShuffleEnabled(false)

        // Added videos have to be folded into the pre-shuffle order too, or they vanish from the
        // queue the moment shuffle is turned off.
        assertThat(ids(controller.videos.value)).containsExactly("a", "x", "b", "c", "y").inOrder()
    }

    @Test
    fun reorderingWhileShuffledLeavesTheOriginalOrderIntact() {
        val controller = controllerOf("a", "b", "c")
        controller.setShuffleEnabled(true)

        assertThat(controller.move(fromIndex = 1, toIndex = 2)).isTrue()
        controller.setShuffleEnabled(false)

        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun reorderingWhileUnshuffledBecomesTheOrderToRestoreLater() {
        val controller = controllerOf("a", "b", "c")

        assertThat(controller.move(fromIndex = 2, toIndex = 0)).isTrue()
        assertThat(ids(controller.videos.value)).containsExactly("c", "a", "b").inOrder()

        controller.setShuffleEnabled(true)
        controller.setShuffleEnabled(false)

        assertThat(ids(controller.videos.value)).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun togglingShuffleToItsCurrentValueOrOnAnEmptyQueueChangesNothing() {
        val empty = PlaybackQueueController()
        assertThat(empty.setShuffleEnabled(true)).isFalse()
        assertThat(empty.shuffleEnabled).isFalse()

        val controller = controllerOf("a", "b")
        assertThat(controller.setShuffleEnabled(false)).isFalse()
    }

    @Test
    fun clearResetsTheQueueAlongWithItsTitleAndToggles() {
        val controller = controllerOf("a", "b", startIndex = 1)
        controller.setLoopEnabled(true)
        controller.setShuffleEnabled(true)

        controller.clear()

        assertThat(controller.isEmpty).isTrue()
        assertThat(controller.size).isEqualTo(0)
        assertThat(controller.currentIndex).isEqualTo(-1)
        assertThat(controller.videos.value).isEmpty()
        assertThat(controller.currentIndexState.value).isEqualTo(-1)
        assertThat(controller.title).isNull()
        assertThat(controller.loopEnabled).isFalse()
        assertThat(controller.shuffleEnabled).isFalse()
        assertThat(controller.hasNext).isFalse()
    }

    @Test
    fun isCurrentOnlyMatchesTheVideoAtTheCurrentPosition() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        assertThat(controller.isCurrent("b")).isTrue()
        assertThat(controller.isCurrent("a")).isFalse()
        assertThat(controller.isCurrent("missing")).isFalse()
    }

    @Test
    fun thePickedVideoIsNotReachedByAdvanceSoItCanResume() {
        val controller = controllerOf("a", "b", "c", startIndex = 1)

        assertThat(controller.isReachedByAdvance("b")).isFalse()
    }

    @Test
    fun aVideoTheQueueMovesToIsReachedByAdvance() {
        val controller = controllerOf("a", "b", "c", startIndex = 0)

        controller.moveTo(1)

        assertThat(controller.isReachedByAdvance("b")).isTrue()
        assertThat(controller.isReachedByAdvance("a")).isFalse()
    }

    @Test
    fun movingBackToThePickedVideoAfterAdvancingCountsAsAnAdvance() {
        val controller = controllerOf("a", "b", startIndex = 0)

        controller.moveTo(1)
        controller.movePrevious()

        assertThat(controller.isReachedByAdvance("a")).isTrue()
    }

    @Test
    fun settingAQueueWithShuffleOffPlaysItInOrderEvenAfterAShuffledOne() {
        val controller = PlaybackQueueController()
        controller.setQueue(listOf(video("a"), video("b"), video("c")), startIndex = 0, title = null, shuffle = true)

        controller.setQueue(listOf(video("a"), video("b"), video("c")), startIndex = 0, title = null, shuffle = false)

        assertThat(controller.shuffleEnabled).isFalse()
        assertThat(ids(controller.videos.value)).containsExactly("a", "b", "c").inOrder()
    }
}
