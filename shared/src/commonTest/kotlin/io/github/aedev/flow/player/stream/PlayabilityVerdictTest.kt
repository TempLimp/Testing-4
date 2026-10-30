package io.github.aedev.flow.player.stream

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test

class PlayabilityVerdictTest {
    @Test
    fun everyClientReportingERROROrUNPLAYABLEMeansTheVideoIsGone() {
        assertThat(
            PlayabilityVerdict.isGone(listOf("ANDROID_VR: status=ERROR, reason=Video unavailable", "IOS: status=UNPLAYABLE, reason=x")),
        ).isTrue()
    }

    @Test
    fun aWebClientThatServedFormatsMeansTheVideoExists() {
        val webOnly =
            listOf(
                "VISIONOS: status=UNPLAYABLE, reason=This video is not available",
                "MWEB: SABR resolved but no format survived decipher/n-transform",
                "ANDROID_VR: status=UNPLAYABLE, reason=This video is not available",
                "MOBILE: 22 formats, none resolvable (SABR-only)",
            )
        val webDemoted =
            listOf(
                "VISIONOS: status=UNPLAYABLE, reason=This video is not available",
                "all SABR clients demoted: MWEB",
                "WEB: SABR resolved but no format survived decipher/n-transform",
                "ANDROID_VR: status=UNPLAYABLE, reason=This video is not available",
            )

        assertThat(PlayabilityVerdict.isGone(webOnly)).isFalse()
        assertThat(PlayabilityVerdict.isGone(webDemoted)).isFalse()
    }

    @Test
    fun aClientSkippedWithoutAskingLeavesARemovedVideoGone() {
        val reasons =
            listOf(
                "VISIONOS: status=ERROR, reason=Video unavailable",
                "all SABR clients demoted: MWEB, WEB",
                "ANDROID_VR: status=UNPLAYABLE, reason=Video unavailable",
                "TV_TIZEN: status=ERROR, reason=Video unavailable",
            )

        assertThat(PlayabilityVerdict.isGone(reasons)).isTrue()
    }

    @Test
    fun aBotCheckTimeoutOrExceptionLeavesTheAnswerUnknown() {
        assertThat(PlayabilityVerdict.isGone(listOf("WEB: BOT_WALL, reason=Sign in", "IOS: status=ERROR, reason=x"))).isFalse()
        assertThat(PlayabilityVerdict.isGone(listOf("IOS: timeout or null response", "WEB: status=ERROR, reason=x"))).isFalse()
        assertThat(PlayabilityVerdict.isGone(listOf("IOS: exception=IOException: offline"))).isFalse()
    }

    @Test
    fun aSignInWallOrOtherStatusIsNotTreatedAsGone() {
        assertThat(PlayabilityVerdict.isGone(listOf("IOS: status=LOGIN_REQUIRED, reason=private"))).isFalse()
        assertThat(PlayabilityVerdict.isGone(listOf("IOS: no adaptive formats"))).isFalse()
        assertThat(PlayabilityVerdict.isGone(emptyList())).isFalse()
    }

    @Test
    fun aBotCheckIsToldApartFromAnAgeCheck() {
        assertThat(PlayabilityVerdict.isBotWall("Sign in to confirm you’re not a bot")).isTrue()
        assertThat(PlayabilityVerdict.isBotWall("Sign in to confirm you're not a bot")).isTrue()
        assertThat(PlayabilityVerdict.isBotWall("Sign in to confirm your age")).isFalse()
        assertThat(PlayabilityVerdict.isBotWall(null)).isFalse()
    }

    @Test
    fun aWallOnEveryClientThatAnsweredBlocksTheVisitorAndTheNetworkOnceAFreshOneIsWalled() {
        val reasons = listOf("VISIONOS: BOT_WALL, reason=Sign in", "MWEB: timeout or null response")

        assertThat(PlayabilityVerdict.block(reasons)).isEqualTo(PlaybackBlock.BOT_WALL)
        assertThat(PlayabilityVerdict.block(reasons, freshVisitorWalled = true)).isEqualTo(PlaybackBlock.NETWORK_WALL)
        assertThat(PlayabilityVerdict.block(reasons + "IOS: status=ERROR, reason=x")).isNull()
    }

    @Test
    fun aSignInStatusWithoutABotCheckNeedsAnAccount() {
        val reasons = listOf("VISIONOS: status=LOGIN_REQUIRED, reason=Sign in to confirm your age")

        assertThat(PlayabilityVerdict.block(reasons)).isEqualTo(PlaybackBlock.LOGIN_REQUIRED)
    }

    @Test
    fun otherFailuresCarryNoBlock() {
        assertThat(PlayabilityVerdict.block(listOf("IOS: status=ERROR, reason=x", "WEB: timeout or null response"))).isNull()
        assertThat(PlayabilityVerdict.block(emptyList())).isNull()
    }

    @Test
    fun aWallOnEveryAskedClientIsAFullWallOneThatAnsweredOtherwiseIsNot1354() {
        val walled =
            listOf(
                "ANDROID_VR: BOT_WALL, reason=Sign in to confirm you're not a bot",
                "WEB_EMBEDDED: BOT_WALL, reason=Sign in to confirm you're not a bot",
                "MWEB: PoToken unavailable (WebView missing/broken?)",
                "all SABR clients demoted: WEB",
            )
        assertThat(PlayabilityVerdict.everyClientWalled(walled)).isTrue()
        assertThat(PlayabilityVerdict.everyClientWalled(walled + "IOS: timeout or null response")).isTrue()
        assertThat(PlayabilityVerdict.everyClientWalled(walled + "IOS: status=ERROR, reason=x")).isFalse()
        assertThat(PlayabilityVerdict.everyClientWalled(listOf("MWEB: no visitorData"))).isFalse()
        assertThat(PlayabilityVerdict.everyClientWalled(emptyList())).isFalse()
    }

    @Test
    fun aSongBlockedInThisCountryIsRefusedToTheViewerNotGone1378() {
        val reasons =
            listOf(
                "VISIONOS: status=UNPLAYABLE, reason=Video unavailable, " +
                    "subreason=The uploader has not made this video available in your country, details=yes",
                "ANDROID_VR: status=UNPLAYABLE, reason=Video unavailable, " +
                    "subreason=The uploader has not made this video available in your country, details=yes",
                "MWEB: PoToken unavailable (WebView missing/broken?)",
            )

        assertThat(PlayabilityVerdict.isGone(reasons)).isFalse()
        assertThat(PlayabilityVerdict.block(reasons)).isEqualTo(PlaybackBlock.REGION)
    }

    @Test
    fun anUnplayableAnswerThatStillCarriesTheVideoSDetailsIsNotGone() {
        val reasons = listOf("VISIONOS: status=UNPLAYABLE, reason=This video is not available, details=yes", "TV_TIZEN: not asked")

        assertThat(PlayabilityVerdict.isGone(reasons)).isFalse()
        assertThat(PlayabilityVerdict.block(reasons)).isNull()
    }

    @Test
    fun aRemovedVideoWithoutDetailsIsStillGone() {
        val reasons =
            listOf(
                "VISIONOS: status=ERROR, reason=This video is unavailable",
                "ANDROID_VR: status=UNPLAYABLE, reason=This video is unavailable",
            )

        assertThat(PlayabilityVerdict.isGone(reasons)).isTrue()
    }

    @Test
    fun aRateLimitAndAMembersOnlyVideoAreRefusalsOfTheViewer() {
        val limited = listOf("VISIONOS: status=ERROR, reason=This content isn't available, try again later.")
        val members = listOf("VISIONOS: status=UNPLAYABLE, reason=Join this channel to get access to members-only content like this video.")

        assertThat(PlayabilityVerdict.isGone(limited)).isFalse()
        assertThat(PlayabilityVerdict.block(limited)).isEqualTo(PlaybackBlock.RATE_LIMITED)
        assertThat(PlayabilityVerdict.isGone(members)).isFalse()
        assertThat(PlayabilityVerdict.block(members)).isEqualTo(PlaybackBlock.MEMBERS_OR_PAID)
    }

    @Test
    fun aGoneVideoAndACountryMembershipOrSignInRefusalStayFailedAWallDoesNot() {
        assertThat(PlayabilityVerdict.isLasting(isGone = true, block = null)).isTrue()
        listOf(PlaybackBlock.REGION, PlaybackBlock.MEMBERS_OR_PAID, PlaybackBlock.LOGIN_REQUIRED).forEach { block ->
            assertThat(PlayabilityVerdict.isLasting(isGone = false, block = block)).isTrue()
        }
        listOf(PlaybackBlock.BOT_WALL, PlaybackBlock.NETWORK_WALL, PlaybackBlock.RATE_LIMITED, null).forEach { block ->
            assertThat(PlayabilityVerdict.isLasting(isGone = false, block = block)).isFalse()
        }
    }

    @Test
    fun oneAnswerRefusesTheViewerOnlyForTheirCountryOrAMembership() {
        fun refuses(
            status: String,
            reason: String?,
        ) = PlayabilityVerdict.refusesViewer(PlayerResponse.PlayabilityStatus(status = status, reason = reason), hasVideoDetails = true)

        assertThat(refuses("UNPLAYABLE", "The uploader has not made this video available in your country")).isTrue()
        assertThat(refuses("UNPLAYABLE", "Join this channel to get access to members-only content like this video")).isTrue()
        assertThat(refuses("UNPLAYABLE", "Playback on other apps has been disabled by the video owner")).isFalse()
        assertThat(refuses("ERROR", "Video unavailable")).isFalse()
        assertThat(refuses("LOGIN_REQUIRED", "Sign in to confirm your age")).isFalse()
        assertThat(refuses("LOGIN_REQUIRED", "Sign in to confirm you're not a bot")).isFalse()
        assertThat(refuses("UNPLAYABLE", "This content isn't available, try again later")).isFalse()
        assertThat(refuses("OK", null)).isFalse()
    }

    @Test
    fun aRefusalIsWrittenWithItsSubreasonAndWhetherDetailsCameBack() {
        val status =
            Json { ignoreUnknownKeys = true }.decodeFromString<PlayerResponse.PlayabilityStatus>(
                """{"status":"UNPLAYABLE","reason":"Video unavailable","errorScreen":{"playerErrorMessageRenderer":""" +
                    """{"subreason":{"runs":[{"text":"The uploader has not made this video available in your country"}]}}}}""",
            )

        assertThat(PlayabilityVerdict.refusal(status, hasVideoDetails = true)).isEqualTo(
            "status=UNPLAYABLE, reason=Video unavailable, " +
                "subreason=The uploader has not made this video available in your country, details=yes",
        )
        assertThat(PlayabilityVerdict.refusal(status.copy(reason = "Sign in to confirm you're not a bot"), hasVideoDetails = false))
            .isEqualTo("BOT_WALL, reason=Sign in to confirm you're not a bot")
    }
}
