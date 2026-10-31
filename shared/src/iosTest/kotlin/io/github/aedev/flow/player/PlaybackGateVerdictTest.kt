package io.github.aedev.flow.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackGateVerdictTest {
    private val walled = "BOT_WALL, reason=Sign in to confirm you're not a bot"
    private val sabrOnly = "SABR resolved but no format survived decipher/n-transform"

    // The ladder's report from CI run 41219504331, the song after the visitor was re-rolled.
    private val report =
        "ALL clients failed BSTsnWoslP4: VISIONOS: $walled | MWEB: $walled | WEB: $sabrOnly | TV_TIZEN: $walled | " +
            "ANDROID_VR: $walled | ANDROID_VR: $walled | ANDROID_VR: $walled | ANDROID_VR: $walled | ANDROID: $walled"

    private fun with(
        client: String,
        reason: String,
    ) = report.replace("$client: $walled", "$client: $reason")

    @Test
    fun aWallWhereOnlyWebAnsweredSabrOnlyIsTheNetwork() {
        assertEquals("bot wall x8, web client SABR-only (S05) x1", networkRefusal("BSTsnWoslP4", report))
    }

    @Test
    fun refusalsAndUnansweredClientsAreTheNetwork() {
        assertEquals(
            "bot wall x7, web client SABR-only (S05) x1, status LOGIN_REQUIRED x1",
            networkRefusal("BSTsnWoslP4", with("ANDROID", "status=LOGIN_REQUIRED, reason=Sign in")),
        )
        assertEquals(
            "bot wall x7, web client SABR-only (S05) x1, no stream offered x1",
            networkRefusal("BSTsnWoslP4", with("TV_TIZEN", "timeout or null response")),
        )
    }

    @Test
    fun formatsThePortFailedToMakePlayableAreAFault() {
        assertNull(networkRefusal("BSTsnWoslP4", with("MWEB", sabrOnly)))
        assertNull(networkRefusal("BSTsnWoslP4", with("TV_TIZEN", "12/40 formats solved")))
        assertNull(networkRefusal("BSTsnWoslP4", with("VISIONOS", "18 formats, none resolvable (SABR-only)")))
        assertNull(networkRefusal("BSTsnWoslP4", with("ANDROID", "exception=IllegalStateException: boom")))
    }

    @Test
    fun noReportIsNoVerdict() {
        assertNull(networkRefusal("other", report))
        assertNull(networkRefusal("BSTsnWoslP4", null))
    }
}
