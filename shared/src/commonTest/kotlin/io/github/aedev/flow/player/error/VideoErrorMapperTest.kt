package io.github.aedev.flow.player.error

import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.error_bot_wall
import io.github.aedev.flow.resources.error_bot_wall_network_hint
import io.github.aedev.flow.resources.error_bot_wall_session
import io.github.aedev.flow.resources.error_bot_wall_session_hint
import io.github.aedev.flow.resources.error_generic
import io.github.aedev.flow.resources.error_login_required
import io.github.aedev.flow.resources.error_login_required_hint
import org.jetbrains.compose.resources.StringResource
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.player.stream.PlaybackBlock
import io.github.aedev.flow.player.stream.PlaybackBlockedException
import kotlin.test.Test

class VideoErrorMapperTest {
    // Upstream mocks Context.getString; the port reads the strings themselves.
    private fun string(resource: StringResource) = PlayerStrings.get(resource)

    @Test
    fun aBotWallOnTheVisitorOffersASessionResetInsteadOfBlamingTheNetwork() {
        val error = VideoErrorMapper.from(PlaybackBlockedException(PlaybackBlock.BOT_WALL))

        assertThat(error.message).isEqualTo(string(Res.string.error_bot_wall_session))
        assertThat(error.hint).isEqualTo(string(Res.string.error_bot_wall_session_hint))
        assertThat(error.isRetryable).isTrue()
        assertThat(error.offersSessionReset).isTrue()
    }

    @Test
    fun aWallThatHeldForAFreshVisitorNamesTheNetworkAndNoVPNUnlessOneIsOn1354() {
        val error = VideoErrorMapper.from(PlaybackBlockedException(PlaybackBlock.NETWORK_WALL))

        assertThat(error.message).isEqualTo(string(Res.string.error_bot_wall))
        assertThat(error.hint).isEqualTo(string(Res.string.error_bot_wall_network_hint))
    }

    @Test
    fun aSignInWallSaysAnAccountIsNeeded() {
        val error = VideoErrorMapper.from(PlaybackBlockedException(PlaybackBlock.LOGIN_REQUIRED))

        assertThat(error.message).isEqualTo(string(Res.string.error_login_required))
        assertThat(error.hint).isEqualTo(string(Res.string.error_login_required_hint))
    }

    @Test
    fun aFailureWithNoCauseStaysGeneric() {
        assertThat(VideoErrorMapper.from(null).message).isEqualTo(string(Res.string.error_generic))
    }
}
