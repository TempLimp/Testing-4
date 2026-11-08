package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class BackgroundPlaybackPolicyTest {
    @Test
    fun autoPipEntersForRegularVideoPlayback() {
        val shouldEnter = BackgroundPlaybackPolicy.shouldEnterAutoPip(
            autoPipEnabled = true,
            isVideoPlaying = true,
            explicitBackgroundPlaybackActive = false
        )

        assertThat(shouldEnter).isTrue()
    }

    @Test
    fun autoPipDoesNotEnterDuringExplicitBackgroundPlayback() {
        val shouldEnter = BackgroundPlaybackPolicy.shouldEnterAutoPip(
            autoPipEnabled = true,
            isVideoPlaying = true,
            explicitBackgroundPlaybackActive = true
        )

        assertThat(shouldEnter).isFalse()
    }

    @Test
    fun disabledAutoPipDoesNotEnterForRegularPlayback() {
        val shouldEnter = BackgroundPlaybackPolicy.shouldEnterAutoPip(
            autoPipEnabled = false,
            isVideoPlaying = true,
            explicitBackgroundPlaybackActive = false
        )

        assertThat(shouldEnter).isFalse()
    }

    @Test
    fun manualBackgroundPlaybackSurvivesActivityDestruction() {
        val shouldKeepPlayback = BackgroundPlaybackPolicy.shouldKeepPlaybackInBackground(
            backgroundPlaybackPreferenceEnabled = false,
            explicitBackgroundPlaybackActive = true,
            hasActiveVideo = true
        )

        assertThat(shouldKeepPlayback).isTrue()
    }

    @Test
    fun ordinaryForegroundPlaybackStopsWhenActivityIsDestroyed() {
        val shouldKeepPlayback = BackgroundPlaybackPolicy.shouldKeepPlaybackInBackground(
            backgroundPlaybackPreferenceEnabled = false,
            explicitBackgroundPlaybackActive = false,
            hasActiveVideo = true
        )

        assertThat(shouldKeepPlayback).isFalse()
    }

    @Test
    fun backgroundPreferenceDoesNotRetainInactivePlayback() {
        val shouldKeepPlayback = BackgroundPlaybackPolicy.shouldKeepPlaybackInBackground(
            backgroundPlaybackPreferenceEnabled = true,
            explicitBackgroundPlaybackActive = false,
            hasActiveVideo = false
        )

        assertThat(shouldKeepPlayback).isFalse()
    }

    @Test
    fun selectingCurrentBackgroundVideoReopensIt() {
        val shouldReopen = BackgroundPlaybackPolicy.shouldReopenCurrentVideo(
            requestedVideoId = "video-id",
            currentVideoId = "video-id",
            isBackgroundPlaybackMode = true,
            isMiniPlayerCollapsed = false,
            hasReusablePlayback = true
        )

        assertThat(shouldReopen).isTrue()
    }

    @Test
    fun selectingCurrentVideoInMiniPlayerExpandsExistingPlayback() {
        val shouldReopen = BackgroundPlaybackPolicy.shouldReopenCurrentVideo(
            requestedVideoId = "video-id",
            currentVideoId = "video-id",
            isBackgroundPlaybackMode = false,
            isMiniPlayerCollapsed = true,
            hasReusablePlayback = true
        )

        assertThat(shouldReopen).isTrue()
    }

    @Test
    fun selectingADifferentBackgroundVideoStartsANewPlayback() {
        val shouldReopen = BackgroundPlaybackPolicy.shouldReopenCurrentVideo(
            requestedVideoId = "new-video-id",
            currentVideoId = "current-video-id",
            isBackgroundPlaybackMode = true,
            isMiniPlayerCollapsed = false,
            hasReusablePlayback = true
        )

        assertThat(shouldReopen).isFalse()
    }

    @Test
    fun selectingSameVideoWhileFullPlayerIsOpenKeepsNormalSelectionBehavior() {
        val shouldReopen = BackgroundPlaybackPolicy.shouldReopenCurrentVideo(
            requestedVideoId = "video-id",
            currentVideoId = "video-id",
            isBackgroundPlaybackMode = false,
            isMiniPlayerCollapsed = false,
            hasReusablePlayback = true
        )

        assertThat(shouldReopen).isFalse()
    }

    @Test
    fun minimizedPlaceholderWithoutLoadedPlaybackStillLoadsVideo() {
        val shouldReopen = BackgroundPlaybackPolicy.shouldReopenCurrentVideo(
            requestedVideoId = "video-id",
            currentVideoId = "video-id",
            isBackgroundPlaybackMode = false,
            isMiniPlayerCollapsed = true,
            hasReusablePlayback = false
        )

        assertThat(shouldReopen).isFalse()
    }
}
