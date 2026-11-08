package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The countdown decides when autoplay advances, so an off-by-one or a missed cancellation shows up
 * as the player skipping to the next video against the user's wishes. None of this was reachable
 * from a test while the timer lived inside `EnhancedPlayerManager` alongside a real player.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoplayCountdownControllerTest {
    private fun video(id: String = "abc123") =
        Video(
            id = id,
            title = "Next video",
            channelName = "Some channel",
            channelId = "UC123",
            thumbnailUrl = "https://example.test/thumb.jpg",
            duration = 120,
            viewCount = 10L,
            uploadDate = "today",
        )

    @Test
    fun startPublishesTheNextVideoSoTheOverlayCanRenderIt() =
        runTest {
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = {})

            controller.start(totalSeconds = 5, nextVideo = video())
            runCurrent()

            val state = controller.state.value
            assertThat(state.isActive).isTrue()
            assertThat(state.secondsRemaining).isEqualTo(5)
            assertThat(state.totalSeconds).isEqualTo(5)
            assertThat(state.nextVideoTitle).isEqualTo("Next video")
            assertThat(state.nextVideoChannel).isEqualTo("Some channel")
            assertThat(state.nextVideoThumbnailUrl).isEqualTo("https://example.test/thumb.jpg")
        }

    @Test
    fun countsDownOneSecondAtATimeWithoutAdvancingEarly() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 3, nextVideo = video())
            runCurrent()

            repeat(2) { elapsed ->
                advanceTimeBy(1_000)
                runCurrent()
                assertThat(controller.state.value.secondsRemaining).isEqualTo(2 - elapsed)
                assertThat(controller.state.value.isActive).isTrue()
                assertThat(advances).isEqualTo(0)
            }
        }

    @Test
    fun advancesExactlyOnceWhenTheCountdownReachesZero() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 3, nextVideo = video())
            advanceTimeBy(3_000)
            runCurrent()

            assertThat(advances).isEqualTo(1)
            assertThat(controller.state.value).isEqualTo(AutoplayCountdownState())
            assertThat(controller.isActive).isFalse()
        }

    @Test
    fun stopDuringACountdownClearsItAndDoesNotAdvance() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 5, nextVideo = video())
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()

            assertThat(controller.stop()).isTrue()
            assertThat(controller.state.value).isEqualTo(AutoplayCountdownState())
            assertThat(advances).isEqualTo(0)
        }

    @Test
    fun aStoppedCountdownNeverAdvancesEvenAfterItsFullDurationPasses() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 5, nextVideo = video())
            runCurrent()
            controller.stop()

            // The job must actually be cancelled, not merely have its state blanked.
            advanceTimeBy(10_000)
            runCurrent()

            assertThat(advances).isEqualTo(0)
            assertThat(controller.isActive).isFalse()
        }

    @Test
    fun stopReportsFalseWhenNothingIsRunningSoCallersSkipTheirSideEffects() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            assertThat(controller.stop()).isFalse()
            assertThat(advances).isEqualTo(0)
        }

    @Test
    fun restartingReplacesTheRunningCountdownInsteadOfAdvancingTwice() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 5, nextVideo = video("first"))
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()

            controller.start(totalSeconds = 5, nextVideo = video("second"))
            runCurrent()
            assertThat(controller.state.value.secondsRemaining).isEqualTo(5)

            advanceTimeBy(5_000)
            runCurrent()
            assertThat(advances).isEqualTo(1)
        }

    @Test
    fun aZeroSecondCountdownAdvancesImmediately() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 0, nextVideo = video())
            runCurrent()

            assertThat(advances).isEqualTo(1)
            assertThat(controller.isActive).isFalse()
        }

    @Test
    fun holdingARunningCountdownFreezesRemainingTimeAndDoesNotAdvance() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 5, nextVideo = video())
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()
            controller.setHold(true)

            advanceTimeBy(10_000)
            runCurrent()

            assertThat(controller.state.value.isActive).isTrue()
            assertThat(controller.state.value.secondsRemaining).isEqualTo(3)
            assertThat(advances).isEqualTo(0)
        }

    @Test
    fun releasingAHoldContinuesFromTheRemainingSeconds() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 5, nextVideo = video())
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()
            controller.setHold(true)
            advanceTimeBy(10_000)
            runCurrent()
            controller.setHold(false)

            advanceTimeBy(2_000)
            runCurrent()
            assertThat(controller.state.value.secondsRemaining).isEqualTo(1)
            assertThat(advances).isEqualTo(0)

            advanceTimeBy(1_000)
            runCurrent()
            assertThat(advances).isEqualTo(1)
            assertThat(controller.isActive).isFalse()
        }

    @Test
    fun aCountdownThatStartsWhileHeldWaitsUntilTheHoldIsReleased() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.setHold(true)
            controller.start(totalSeconds = 3, nextVideo = video())
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()

            assertThat(controller.state.value.isActive).isTrue()
            assertThat(controller.state.value.secondsRemaining).isEqualTo(3)
            assertThat(advances).isEqualTo(0)

            controller.setHold(false)
            advanceTimeBy(3_000)
            runCurrent()

            assertThat(advances).isEqualTo(1)
            assertThat(controller.isActive).isFalse()
        }

    @Test
    fun stopDuringAHoldClearsItAndDoesNotAdvanceAfterRelease() =
        runTest {
            var advances = 0
            val controller = AutoplayCountdownController(backgroundScope, onElapsed = { advances++ })

            controller.start(totalSeconds = 5, nextVideo = video())
            runCurrent()
            controller.setHold(true)
            assertThat(controller.stop()).isTrue()

            controller.setHold(false)
            advanceTimeBy(10_000)
            runCurrent()

            assertThat(advances).isEqualTo(0)
            assertThat(controller.isActive).isFalse()
        }
}
