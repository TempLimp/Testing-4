package io.github.aedev.flow.player.recovery

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class StreamExpiryRecoveryControllerTest {
    private fun controller() = StreamExpiryRecoveryController(maxRetries = 3)

    @Test
    fun theFirstExpiryReloadsWithoutEvictingTheCache() {
        val decision = controller().onStreamExpired("vid_a")

        assertThat(decision).isEqualTo(
            StreamExpiryRecoveryController.Decision.Reload(attempt = 1, limit = 3, evictCache = false),
        )
    }

    @Test
    fun aRepeatExpiryEvictsWhatTheCacheHoldsForTheVideo() {
        val recovery = controller()
        recovery.onStreamExpired("vid_a")

        val second = recovery.onStreamExpired("vid_a") as StreamExpiryRecoveryController.Decision.Reload

        assertThat(second.attempt).isEqualTo(2)
        assertThat(second.evictCache).isTrue()
    }

    @Test
    fun theBudgetIsSpentAfterTheRetryLimitAndEveryLaterExpiryIsIgnored() {
        val recovery = controller()

        repeat(3) { assertThat(recovery.onStreamExpired("vid_a")).isInstanceOf(StreamExpiryRecoveryController.Decision.Reload::class) }

        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(StreamExpiryRecoveryController.Decision.GiveUp)
        assertThat(recovery.abandonedVideoId).isEqualTo("vid_a")
        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(StreamExpiryRecoveryController.Decision.Ignored)
        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(StreamExpiryRecoveryController.Decision.Ignored)
    }

    @Test
    fun aDifferentVideoStartsFromAFreshBudget() {
        val recovery = controller()
        repeat(4) { recovery.onStreamExpired("vid_a") }

        val other = recovery.onStreamExpired("vid_b")

        assertThat(other).isEqualTo(
            StreamExpiryRecoveryController.Decision.Reload(attempt = 1, limit = 3, evictCache = false),
        )
    }

    @Test
    fun aLongPlaylistOfRefusedVideosNeverRunsOutOfBudgetPartwayThrough() {
        val recovery = controller()

        repeat(10) { index ->
            repeat(2) { recovery.onStreamExpired("vid_$index") }
        }

        assertThat(recovery.onStreamExpired("vid_10")).isEqualTo(
            StreamExpiryRecoveryController.Decision.Reload(attempt = 1, limit = 3, evictCache = false),
        )
    }

    @Test
    fun playbackThePlayerAbandonedTakesNoFurtherExpiryEvents() {
        val recovery = controller()

        recovery.onPlaybackAbandoned("vid_a")

        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(StreamExpiryRecoveryController.Decision.Ignored)
        assertThat(recovery.onStreamExpired("vid_b")).isInstanceOf(StreamExpiryRecoveryController.Decision.Reload::class)
    }

    @Test
    fun askingForPlaybackAgainRestoresTheBudgetForAVideoThatWasGivenUpOn() {
        val recovery = controller()
        repeat(4) { recovery.onStreamExpired("vid_a") }

        recovery.onPlaybackRequested()

        assertThat(recovery.abandonedVideoId).isNull()
        assertThat(recovery.onStreamExpired("vid_a")).isEqualTo(
            StreamExpiryRecoveryController.Decision.Reload(attempt = 1, limit = 3, evictCache = false),
        )
    }

    @Test
    fun loadingAnotherVideoReleasesTheAbandonedLatchButLoadingTheSameOneKeepsIt() {
        val recovery = controller()
        recovery.onPlaybackAbandoned("vid_a")

        recovery.onLoadStarted("vid_a")
        assertThat(recovery.abandonedVideoId).isEqualTo("vid_a")

        recovery.onLoadStarted("vid_b")
        assertThat(recovery.abandonedVideoId).isNull()
    }
}
