package io.github.aedev.flow.player.recovery

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class ClearedMediaRecoveryStateTest {
    @Test
    fun capturePreservesPausedLocalPlaybackState() {
        val state = ClearedMediaRecoveryState()

        state.capture(
            videoId = "video-id",
            positionMs = 12_345L,
            playWhenReady = false,
            localFilePath = "/media/video.mp4"
        )

        assertThat(state.pendingFor("video-id")).isEqualTo(
            ClearedMediaRecoverySnapshot(
                videoId = "video-id",
                positionMs = 12_345L,
                playWhenReady = false,
                localFilePath = "/media/video.mp4"
            )
        )
    }

    @Test
    fun repeatedEmptyTrimDoesNotReplaceThePendingSnapshot() {
        val state = ClearedMediaRecoveryState()
        state.capture("video-id", 8_000L, playWhenReady = false, localFilePath = null)

        state.capture(null, 0L, playWhenReady = false, localFilePath = null)

        assertThat(state.pendingFor("video-id")?.positionMs).isEqualTo(8_000L)
    }

    @Test
    fun snapshotCannotBeReusedForAnotherVideo() {
        val state = ClearedMediaRecoveryState()
        state.capture("old-video", 8_000L, playWhenReady = false, localFilePath = null)

        assertThat(state.pendingFor("new-video")).isNull()
    }

    @Test
    fun completedRecoveryClearsOnlyTheSnapshotThatWasLoaded() {
        val state = ClearedMediaRecoveryState()
        state.capture("video-id", 8_000L, playWhenReady = false, localFilePath = null)
        val loadedSnapshot = state.pendingFor("video-id")!!
        state.capture("video-id", 9_000L, playWhenReady = false, localFilePath = null)

        state.complete(loadedSnapshot)

        assertThat(state.pendingFor("video-id")?.positionMs).isEqualTo(9_000L)
    }

    @Test
    fun explicitClearPreventsStoppedMediaFromBeingRecovered() {
        val state = ClearedMediaRecoveryState()
        state.capture("video-id", 8_000L, playWhenReady = false, localFilePath = null)

        state.clear()

        assertThat(state.pendingFor("video-id")).isNull()
    }
}
