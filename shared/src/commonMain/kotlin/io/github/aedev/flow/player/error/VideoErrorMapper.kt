package io.github.aedev.flow.player.error

import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.error_age_restricted
import io.github.aedev.flow.resources.error_age_restricted_hint
import io.github.aedev.flow.resources.error_bot_wall
import io.github.aedev.flow.resources.error_bot_wall_hint
import io.github.aedev.flow.resources.error_bot_wall_network_hint
import io.github.aedev.flow.resources.error_bot_wall_session
import io.github.aedev.flow.resources.error_bot_wall_session_hint
import io.github.aedev.flow.resources.error_captcha
import io.github.aedev.flow.resources.error_captcha_hint
import io.github.aedev.flow.resources.error_channel_terminated
import io.github.aedev.flow.resources.error_channel_terminated_hint
import io.github.aedev.flow.resources.error_codec_issue
import io.github.aedev.flow.resources.error_codec_issue_hint
import io.github.aedev.flow.resources.error_extraction_failed
import io.github.aedev.flow.resources.error_extraction_failed_hint
import io.github.aedev.flow.resources.error_generic
import io.github.aedev.flow.resources.error_generic_hint
import io.github.aedev.flow.resources.error_geo_restricted
import io.github.aedev.flow.resources.error_geo_restricted_hint
import io.github.aedev.flow.resources.error_http_forbidden
import io.github.aedev.flow.resources.error_http_forbidden_hint
import io.github.aedev.flow.resources.error_http_general
import io.github.aedev.flow.resources.error_http_general_hint
import io.github.aedev.flow.resources.error_login_required
import io.github.aedev.flow.resources.error_login_required_hint
import io.github.aedev.flow.resources.error_members_or_paid
import io.github.aedev.flow.resources.error_members_or_paid_hint
import io.github.aedev.flow.resources.error_network
import io.github.aedev.flow.resources.error_network_hint
import io.github.aedev.flow.resources.error_no_internet
import io.github.aedev.flow.resources.error_no_internet_hint
import io.github.aedev.flow.resources.error_not_available
import io.github.aedev.flow.resources.error_not_available_hint
import io.github.aedev.flow.resources.error_not_supported
import io.github.aedev.flow.resources.error_not_supported_hint
import io.github.aedev.flow.resources.error_paid_content
import io.github.aedev.flow.resources.error_paid_content_hint
import io.github.aedev.flow.resources.error_playback_failed
import io.github.aedev.flow.resources.error_playback_failed_hint
import io.github.aedev.flow.resources.error_private_video
import io.github.aedev.flow.resources.error_private_video_hint
import io.github.aedev.flow.resources.error_rate_limited
import io.github.aedev.flow.resources.error_rate_limited_hint
import io.github.aedev.flow.resources.error_stream_failed
import io.github.aedev.flow.resources.error_stream_failed_hint
import io.github.aedev.flow.resources.error_timeout
import io.github.aedev.flow.resources.error_timeout_hint
import io.github.aedev.flow.resources.error_yt_music_premium
import io.github.aedev.flow.resources.error_yt_music_premium_hint
import io.github.aedev.flow.network.appTrafficUsesVpn
import io.github.aedev.flow.network.isIoError
import io.github.aedev.flow.network.isSocketTimeoutError
import io.github.aedev.flow.network.isUnknownHostError
import io.github.aedev.flow.player.engine.InvalidResponseCodeException
import io.github.aedev.flow.player.engine.PlaybackException

import io.github.aedev.flow.network.appTrafficUsesVpn
import io.github.aedev.flow.player.stream.PlaybackBlock
import io.github.aedev.flow.player.stream.PlaybackBlockedException
import io.github.aedev.flow.utils.Log
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

/**
 * Maps throwables (from NewPipe Extractor, ExoPlayer, network layer, etc.) to
 * human-readable, actionable error messages shown in the video player.
 *
 * Designed to mirror NewPipe's `ErrorInfo.getMessage()` logic but return plain
 * strings suitable for the Flow player's snackbar / error panel.
 */
object VideoErrorMapper {
    private const val TAG = "VideoErrorMapper"

    /**
     * Structured error result carrying a short display message and an optional
     * hint that gives the user more context or a suggested action.
     */
    data class VideoError(
        /** Short primary message — always present, suitable for a snackbar. */
        val message: String,
        /**
         * Optional secondary hint shown below the primary message in the error
         * panel (e.g. "Try a VPN or check your region settings.").
         */
        val hint: String? = null,
        /** Whether it is worth retrying (show a Retry button). */
        val isRetryable: Boolean = true,
        /** Whether this can be "fixed" by the user (vs. a server-side restriction). */
        val isUserActionable: Boolean = false,
        /** A fresh YouTube session may get past it, so the panel offers the reset. */
        val offersSessionReset: Boolean = false,
    )

    /**
     * Converts any [Throwable] thrown during stream resolution or playback to a
     * [VideoError] with a professional, non-technical message.
     *
     * @param context     Android context to resolve string resources.
     * @param throwable   The exception to map.
     * @param videoId     Optional video ID — used only for log output.
     */
    fun from(
        throwable: Throwable?,
        videoId: String? = null,
    ): VideoError {
        Log.w(TAG, "Mapping error for video=$videoId: ${throwable?.let { it::class.simpleName }}: ${throwable?.message}")

        return when {
            // ── Content restriction exceptions (NewPipe Extractor) ────────────

            throwable is AgeRestrictedContentException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_age_restricted),
                    hint = PlayerStrings.get(Res.string.error_age_restricted_hint),
                    isRetryable = false,
                    isUserActionable = false,
                )
            }

            throwable is PrivateContentException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_private_video),
                    hint = PlayerStrings.get(Res.string.error_private_video_hint),
                    isRetryable = false,
                    isUserActionable = false,
                )
            }

            throwable is GeographicRestrictionException ||
                throwable is UnsupportedContentInCountryException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_geo_restricted),
                    hint = PlayerStrings.get(Res.string.error_geo_restricted_hint),
                    isRetryable = false,
                    isUserActionable = true,
                )
            }

            throwable is PaidContentException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_paid_content),
                    hint = PlayerStrings.get(Res.string.error_paid_content_hint),
                    isRetryable = false,
                    isUserActionable = false,
                )
            }

            throwable is YoutubeMusicPremiumContentException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_yt_music_premium),
                    hint = PlayerStrings.get(Res.string.error_yt_music_premium_hint),
                    isRetryable = false,
                    isUserActionable = false,
                )
            }

            throwable is AccountTerminatedException -> {
                val reason = throwable.message?.takeIf { it.isNotBlank() }
                VideoError(
                    message = PlayerStrings.get(Res.string.error_channel_terminated),
                    hint = if (reason != null) reason else PlayerStrings.get(Res.string.error_channel_terminated_hint),
                    isRetryable = false,
                    isUserActionable = false,
                )
            }

            throwable is PlaybackBlockedException && throwable.block == PlaybackBlock.BOT_WALL -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_bot_wall_session),
                    hint = PlayerStrings.get(Res.string.error_bot_wall_session_hint),
                    isRetryable = true,
                    isUserActionable = true,
                    offersSessionReset = true,
                )
            }

            // Only a wall that held for a fresh visitor names the network, and only a VPN that is
            // actually on is named: most walled users have none (#1354).
            throwable is PlaybackBlockedException && throwable.block == PlaybackBlock.NETWORK_WALL -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_bot_wall),
                    hint =
                        PlayerStrings.get(
                            if (appTrafficUsesVpn()) Res.string.error_bot_wall_hint else Res.string.error_bot_wall_network_hint,
                        ),
                    isRetryable = true,
                    isUserActionable = true,
                    offersSessionReset = true,
                )
            }

            throwable is PlaybackBlockedException && throwable.block == PlaybackBlock.REGION -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_geo_restricted),
                    hint = PlayerStrings.get(Res.string.error_geo_restricted_hint),
                    isRetryable = false,
                    isUserActionable = true,
                )
            }

            throwable is PlaybackBlockedException && throwable.block == PlaybackBlock.RATE_LIMITED -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_rate_limited),
                    hint = PlayerStrings.get(Res.string.error_rate_limited_hint),
                    isRetryable = true,
                    isUserActionable = true,
                )
            }

            throwable is PlaybackBlockedException && throwable.block == PlaybackBlock.MEMBERS_OR_PAID -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_members_or_paid),
                    hint = PlayerStrings.get(Res.string.error_members_or_paid_hint),
                    isRetryable = false,
                    isUserActionable = false,
                )
            }

            throwable is PlaybackBlockedException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_login_required),
                    hint = PlayerStrings.get(Res.string.error_login_required_hint),
                    isRetryable = true,
                    isUserActionable = false,
                )
            }

            throwable is ReCaptchaException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_captcha),
                    hint = PlayerStrings.get(Res.string.error_captcha_hint),
                    isRetryable = true,
                    isUserActionable = true,
                )
            }

            // Generic "content not available" must come AFTER the specific sub-classes above
            throwable is ContentNotAvailableException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_not_available),
                    hint = PlayerStrings.get(Res.string.error_not_available_hint),
                    isRetryable = false,
                    isUserActionable = false,
                )
            }

            throwable is ContentNotSupportedException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_not_supported),
                    hint = PlayerStrings.get(Res.string.error_not_supported_hint),
                    isRetryable = false,
                    isUserActionable = false,
                )
            }

            // ── Network / connectivity errors ─────────────────────────────────

            throwable != null && isUnknownHostError(throwable) -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_no_internet),
                    hint = PlayerStrings.get(Res.string.error_no_internet_hint),
                    isRetryable = true,
                    isUserActionable = true,
                )
            }

            throwable != null && isSocketTimeoutError(throwable) -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_timeout),
                    hint = PlayerStrings.get(Res.string.error_timeout_hint),
                    isRetryable = true,
                    isUserActionable = true,
                )
            }

            throwable is kotlinx.coroutines.TimeoutCancellationException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_timeout), // Use common connection reset title
                    hint = PlayerStrings.get(Res.string.error_timeout_hint),
                    isRetryable = true,
                    isUserActionable = true,
                )
            }

            throwable != null && isNetworkRelated(throwable) -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_network),
                    hint = PlayerStrings.get(Res.string.error_network_hint),
                    isRetryable = true,
                    isUserActionable = true,
                )
            }

            // ── ExoPlayer / media source errors ──────────────────────────────

            throwable != null && isExoHttpForbidden(throwable) -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_http_forbidden),
                    hint = PlayerStrings.get(Res.string.error_http_forbidden_hint),
                    isRetryable = true,
                    isUserActionable = false,
                )
            }

            throwable != null && isExoHttpError(throwable) -> {
                val code = getExoHttpCode(throwable) ?: ""
                VideoError(
                    message = PlayerStrings.get(Res.string.error_http_general, code.toString()),
                    hint = PlayerStrings.get(Res.string.error_http_general_hint, code.toString()),
                    isRetryable = true,
                    isUserActionable = false,
                )
            }

            throwable != null && isExoSourceError(throwable) -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_stream_failed),
                    hint = PlayerStrings.get(Res.string.error_stream_failed_hint),
                    isRetryable = true,
                    isUserActionable = false,
                )
            }

            throwable != null && isExoRendererError(throwable) -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_codec_issue),
                    hint = PlayerStrings.get(Res.string.error_codec_issue_hint),
                    isRetryable = true,
                    isUserActionable = true,
                )
            }

            throwable != null && isExoError(throwable) -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_playback_failed),
                    hint = PlayerStrings.get(Res.string.error_playback_failed_hint),
                    isRetryable = true,
                    isUserActionable = false,
                )
            }

            // ── NewPipe parsing error (extraction failed, not network) ────────

            throwable is ExtractionException -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_extraction_failed),
                    hint = PlayerStrings.get(Res.string.error_extraction_failed_hint),
                    isRetryable = true,
                    isUserActionable = false,
                )
            }

            // ── Fallback ─────────────────────────────────────────────────────

            throwable != null -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_generic),
                    hint = buildFallbackHint(throwable),
                    isRetryable = true,
                    isUserActionable = false,
                )
            }

            else -> {
                VideoError(
                    message = PlayerStrings.get(Res.string.error_generic),
                    hint = PlayerStrings.get(Res.string.error_generic_hint),
                    isRetryable = true,
                    isUserActionable = false,
                )
            }
        }
    }

    /**
     * Convenience overload for the timeout-only path where no exception is available.
     */
    fun fromTimeout(): VideoError =
        VideoError(
            message = PlayerStrings.get(Res.string.error_timeout),
            hint = PlayerStrings.get(Res.string.error_timeout_hint),
            isRetryable = true,
            isUserActionable = true,
        )

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun isNetworkRelated(t: Throwable): Boolean =
        isIoError(t) ||
            t.cause?.let { isNetworkRelated(it) } == true

    // Upstream reads Media3's ExoPlaybackException and InvalidResponseCodeException by reflection; the port's player
    // reports PlaybackException with the refused response as its cause.
    private fun isExoHttpForbidden(t: Throwable): Boolean = t is PlaybackException && (t.cause as? InvalidResponseCodeException)?.responseCode == 403

    private fun isExoHttpError(t: Throwable): Boolean = t is PlaybackException && t.cause is InvalidResponseCodeException

    private fun getExoHttpCode(t: Throwable): Int? = (t.cause as? InvalidResponseCodeException)?.responseCode

    private fun isExoSourceError(t: Throwable): Boolean = t is PlaybackException && t.type == PlaybackException.TYPE_SOURCE

    private fun isExoRendererError(t: Throwable): Boolean = t is PlaybackException && t.type == PlaybackException.TYPE_RENDERER

    private fun isExoError(t: Throwable): Boolean = t is PlaybackException

    private fun buildFallbackHint(t: Throwable): String {
        // Provide a minimal technical hint without leaking a raw stack trace.
        val type = t::class.simpleName
        val msg = t.message?.take(120)?.let { if (it.isNotBlank()) it else null }
        return when {
            msg != null -> "$type: $msg"
            else -> "Error type: $type. Please try again or restart the app."
        }
    }
}
