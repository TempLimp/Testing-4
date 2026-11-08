package io.github.aedev.flow.player

import io.github.aedev.flow.player.engine.FlowPlayer
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * Audio-only is what keeps playback going with the screen off, so its flags getting out of step is
 * not cosmetic: leaving video tracks enabled with no surface stalls playback, and dropping
 * audio-only on a queue advance has already caused exactly that.
 */
class AudioOnlyModeTest {
    @Test
    fun aFreshModePlaysVideoAndNeedsNoRestore() {
        val mode = AudioOnlyMode()

        assertThat(mode.isActive).isFalse()
        assertThat(mode.restorePending).isFalse()
        assertThat(mode.tracksDisabled).isFalse()
        assertThat(mode.needsVideoRestore).isFalse()
    }

    @Test
    fun restoringWithoutASurfaceDefersInsteadOfDroppingAudioOnly() {
        val mode = AudioOnlyMode()
        mode.enter()

        assertThat(mode.restore(canRestore = false)).isFalse()

        // Still audio-only: dropping it here would re-enable video tracks with nothing to draw on.
        assertThat(mode.isActive).isTrue()
        assertThat(mode.restorePending).isTrue()
        assertThat(mode.needsVideoRestore).isTrue()
    }

    @Test
    fun aDeferredRestoreCompletesOnceASurfaceIsBack() {
        val mode = AudioOnlyMode()
        mode.enter()
        mode.restore(canRestore = false)

        assertThat(mode.restore(canRestore = true)).isTrue()
        assertThat(mode.isActive).isFalse()
        assertThat(mode.restorePending).isFalse()
        assertThat(mode.needsVideoRestore).isFalse()
    }

    @Test
    fun newStreamsCanKeepPlaybackAudioOnlyAcrossAQueueAdvance() {
        val mode = AudioOnlyMode()
        mode.enter()

        mode.applyStreams(keepAudioOnly = true)
        assertThat(mode.isActive).isTrue()

        mode.applyStreams(keepAudioOnly = false)
        assertThat(mode.isActive).isFalse()
    }

    @Test
    fun disablingTracksReportsOnlyTheTransitions() {
        val mode = AudioOnlyMode()

        assertThat(mode.setTracksDisabled(true)).isTrue()
        assertThat(mode.setTracksDisabled(true)).isFalse()
        assertThat(mode.tracksDisabled).isTrue()

        assertThat(mode.setTracksDisabled(false)).isTrue()
        assertThat(mode.setTracksDisabled(false)).isFalse()
        assertThat(mode.tracksDisabled).isFalse()
    }

    @Test
    fun resetClearsAudioOnlyButADeferredRestoreStillRunsLater() {
        val mode = AudioOnlyMode()
        mode.enter()
        mode.restore(canRestore = false)

        mode.reset()

        assertThat(mode.isActive).isFalse()
        assertThat(mode.restorePending).isTrue()
        assertThat(mode.needsVideoRestore).isTrue()
    }
}

class BackgroundHandoffPolicyTest {
    private fun needsReload(
        isSameVideo: Boolean = true,
        hasMediaItem: Boolean = true,
        playbackState: Int? = FlowPlayer.STATE_READY,
        isPrepared: Boolean = true,
    ) = BackgroundHandoffPolicy.needsServiceLayerReload(isSameVideo, hasMediaItem, playbackState, isPrepared)

    @Test
    fun aPreparedPlayerCarriesOnPlayingInTheBackground() {
        assertThat(needsReload()).isFalse()
    }

    @Test
    fun aPlayerWithNothingLoadedIsReloadedFromTheServiceLayer() {
        assertThat(needsReload(hasMediaItem = false)).isTrue()
        assertThat(needsReload(hasMediaItem = false, playbackState = null)).isTrue()
    }

    @Test
    fun anIdleOrUnpreparedPlayerIsReloaded() {
        assertThat(needsReload(playbackState = FlowPlayer.STATE_IDLE)).isTrue()
        assertThat(needsReload(isPrepared = false)).isTrue()
    }

    @Test
    fun aPlayerOnADifferentVideoIsLeftAlone() {
        // Reloading here would yank playback back to the session's video mid-switch.
        assertThat(needsReload(isSameVideo = false, hasMediaItem = false)).isFalse()
        assertThat(needsReload(isSameVideo = false, isPrepared = false)).isFalse()
    }
}
