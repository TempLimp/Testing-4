package io.github.aedev.flow.player.error

import io.github.aedev.flow.player.engine.PlaybackException
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.player.error.VideoDecoderRecoveryPolicy.Decision
import kotlin.test.Test

class VideoDecoderRecoveryPolicyTest {
    private val decodingFailed = PlaybackException.ERROR_CODE_DECODING_FAILED

    @Test
    fun firstDecodingFailureRePrepares() {
        val policy = VideoDecoderRecoveryPolicy()

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.REPREPARE)
    }

    @Test
    fun decoderInitFailureRePrepares() {
        val policy = VideoDecoderRecoveryPolicy()

        assertThat(
            policy.onDecoderError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, formatHandled = true),
        ).isEqualTo(Decision.REPREPARE)
    }

    @Test
    fun aSecondFailureBeforeAnyFrameGivesUp() {
        val policy = VideoDecoderRecoveryPolicy()
        policy.onDecoderError(decodingFailed, formatHandled = true)

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.GIVE_UP)
    }

    @Test
    fun aRenderedFrameAllowsAnotherAttempt() {
        val policy = VideoDecoderRecoveryPolicy()
        policy.onDecoderError(decodingFailed, formatHandled = true)
        policy.onFirstFrameRendered()

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.REPREPARE)
    }

    @Test
    fun attemptsPerVideoAreCappedEvenWithFramesInBetween() {
        val policy = VideoDecoderRecoveryPolicy(maxPerVideo = 2)
        repeat(2) {
            assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.REPREPARE)
            policy.onFirstFrameRendered()
        }

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.GIVE_UP)
    }

    @Test
    fun aNewVideoStartsAFreshBudget() {
        val policy = VideoDecoderRecoveryPolicy(maxPerVideo = 1)
        policy.onDecoderError(decodingFailed, formatHandled = true)
        policy.reset()

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = true)).isEqualTo(Decision.REPREPARE)
    }

    @Test
    fun aFormatTheDecoderCannotHandleGivesUp() {
        val policy = VideoDecoderRecoveryPolicy()

        assertThat(policy.onDecoderError(decodingFailed, formatHandled = false)).isEqualTo(Decision.GIVE_UP)
    }

    @Test
    fun decoderQueryAndAudioTrackFailuresGiveUp() {
        val policy = VideoDecoderRecoveryPolicy()

        listOf(
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        ).forEach { code ->
            assertThat(policy.onDecoderError(code, formatHandled = true)).isEqualTo(Decision.GIVE_UP)
        }
    }
}
