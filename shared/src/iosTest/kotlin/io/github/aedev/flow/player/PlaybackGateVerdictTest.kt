package io.github.aedev.flow.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackGateVerdictTest {
    private val walled = "BOT_WALL, reason=Sign in to confirm you're not a bot"

    // The ladder's report from CI run 41219504331, the song after the visitor was re-rolled.
    private val report =
        "ALL clients failed BSTsnWoslP4: VISIONOS: $walled | MWEB: $walled | " +
            "WEB: SABR resolved but no format survived decipher/n-transform | TV_TIZEN: $walled | " +
            "ANDROID_VR: $walled | ANDROID_VR: $walled | ANDROID_VR: $walled | ANDROID_VR: $walled | ANDROID: $walled"

    @Test
    fun aWallWithOnlyWebAnsweringSabrOnlyIsABlock() {
        assertEquals("BOT_WALL (WEB answered SABR-only)", sabrOnlyWall("BSTsnWoslP4", report))
    }

    @Test
    fun aCipheredClientThatYieldedNothingIsNotABlock() {
        val mweb = report.replace("MWEB: $walled", "MWEB: SABR resolved but no format survived decipher/n-transform")
        assertNull(sabrOnlyWall("BSTsnWoslP4", mweb))
    }

    @Test
    fun anyOtherFailureIsNotABlock() {
        assertNull(sabrOnlyWall("BSTsnWoslP4", report.replace("TV_TIZEN: $walled", "TV_TIZEN: timeout or null response")))
        assertNull(sabrOnlyWall("other", report))
        assertNull(sabrOnlyWall("BSTsnWoslP4", null))
    }
}
