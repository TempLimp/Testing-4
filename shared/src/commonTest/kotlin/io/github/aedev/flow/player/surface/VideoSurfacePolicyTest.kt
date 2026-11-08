package io.github.aedev.flow.player.surface

import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class VideoSurfacePolicyTest {
    @Test
    fun surfaceViewRestoreWaitsForAValidHolder() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = VideoSurfacePolicy.SDK_UPSIDE_DOWN_CAKE,
                isDisplayInteractive = true,
                isSurfaceValid = false,
            ),
        ).isFalse()
    }

    @Test
    fun surfaceViewRestoresOnceItsHolderIsValid() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = VideoSurfacePolicy.SDK_UPSIDE_DOWN_CAKE,
                isDisplayInteractive = true,
                isSurfaceValid = true,
            ),
        ).isTrue()
    }

    @Test
    fun textureViewRestoreDoesNotRequireASurfaceHolder() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = VideoSurfacePolicy.SDK_TIRAMISU,
                isDisplayInteractive = true,
                isSurfaceValid = false,
            ),
        ).isTrue()
    }

    @Test
    fun videoOutputStaysDisabledWhileDisplayIsNotInteractive() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = VideoSurfacePolicy.SDK_UPSIDE_DOWN_CAKE,
                isDisplayInteractive = false,
                isSurfaceValid = true,
            ),
        ).isFalse()
    }

    @Test
    fun pausedReadyPlaybackResyncsAfterASurfaceReattach() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = FlowPlayer.STATE_READY,
            ),
        ).isTrue()
    }

    @Test
    fun pausedBufferingPlaybackResyncsAfterASurfaceReattach() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = FlowPlayer.STATE_BUFFERING,
            ),
        ).isTrue()
    }

    @Test
    fun activePlaybackIsNotInterruptedByAResyncSeek() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = true,
                isLive = false,
                playbackState = FlowPlayer.STATE_READY,
            ),
        ).isFalse()
    }

    @Test
    fun liveStreamsNeverResyncViaSeek() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = true,
                playbackState = FlowPlayer.STATE_READY,
            ),
        ).isFalse()
    }

    @Test
    fun idleAndEndedPlayersAreNotResynced() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = FlowPlayer.STATE_IDLE,
            ),
        ).isFalse()
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = FlowPlayer.STATE_ENDED,
            ),
        ).isFalse()
    }

    @Test
    fun theResyncTargetNeverLandsOnThePositionThePlayerAlreadyReports() {
        // A seek resolving to the current millisecond returns before the renderers are disabled,
        // so the target has to differ by at least one.
        assertThat(VideoSurfacePolicy.resyncSeekTargetMs(315066L)).isEqualTo(315065L)
        assertThat(VideoSurfacePolicy.resyncSeekTargetMs(1L)).isEqualTo(0L)
    }

    @Test
    fun aPlayerAtTheVeryStartNudgesForwardInsteadOfBelowZero() {
        assertThat(VideoSurfacePolicy.resyncSeekTargetMs(0L)).isEqualTo(1L)
    }

    @Test
    fun aSurfaceThatCameBackOnARunningVideoIsWatchedForAStuckFirstFrame() {
        assertThat(
            VideoSurfacePolicy.shouldWatchFirstFrame(
                wasSurfaceValid = false,
                reenabledVideo = false,
                isAudioOnly = false,
                isLive = false,
            ),
        ).isTrue()
    }

    @Test
    fun turningVideoBackOnAfterAudioOnlyIsLeftToJoinWithoutAFlush() {
        assertThat(
            VideoSurfacePolicy.shouldWatchFirstFrame(
                wasSurfaceValid = false,
                reenabledVideo = true,
                isAudioOnly = false,
                isLive = false,
            ),
        ).isFalse()
    }

    @Test
    fun audioOnlyLiveAndAnUnchangedSurfaceAreNeverWatched() {
        assertThat(VideoSurfacePolicy.shouldWatchFirstFrame(false, false, isAudioOnly = true, isLive = false)).isFalse()
        assertThat(VideoSurfacePolicy.shouldWatchFirstFrame(false, false, isAudioOnly = false, isLive = true)).isFalse()
        assertThat(VideoSurfacePolicy.shouldWatchFirstFrame(true, false, isAudioOnly = false, isLive = false)).isFalse()
    }
}
