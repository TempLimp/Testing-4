package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class ServicePlaybackLoadPolicyTest {
    @Test
    fun serviceCommitsWhileItStillOwnsAnUnpreparedTarget() {
        val decision = ServicePlaybackLoadPolicy.decide(
            requestedVideoId = "video-b",
            playerVideoId = "video-b",
            globalVideoId = "video-b",
            isPreparedForRequestedVideo = false,
        )

        assertEquals(ServicePlaybackCommitDecision.COMMIT, decision)
    }

    @Test
    fun serviceDoesNotReplacePlaybackPreparedByTheUi() {
        val decision = ServicePlaybackLoadPolicy.decide(
            requestedVideoId = "video-b",
            playerVideoId = "video-b",
            globalVideoId = "video-b",
            isPreparedForRequestedVideo = true,
        )

        assertEquals(ServicePlaybackCommitDecision.SKIP_ALREADY_PREPARED, decision)
    }

    @Test
    fun serviceDoesNotCommitAfterPlayerAdvancesAgain() {
        val decision = ServicePlaybackLoadPolicy.decide(
            requestedVideoId = "video-b",
            playerVideoId = "video-c",
            globalVideoId = "video-c",
            isPreparedForRequestedVideo = false,
        )

        assertEquals(ServicePlaybackCommitDecision.SKIP_STALE_REQUEST, decision)
    }

    @Test
    fun serviceDoesNotCommitAfterGlobalSelectionChanges() {
        val decision = ServicePlaybackLoadPolicy.decide(
            requestedVideoId = "video-b",
            playerVideoId = "video-b",
            globalVideoId = "video-c",
            isPreparedForRequestedVideo = false,
        )

        assertEquals(ServicePlaybackCommitDecision.SKIP_STALE_REQUEST, decision)
    }
}
