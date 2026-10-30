package io.github.aedev.flow.player.stream

import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

/**
 * Cases are the real shapes observed from the InnerTube `player` endpoint, not invented ones —
 * every VISIONOS response carries an HLS manifest, which is what made the naive rule misfire.
 */
class LiveDetectionRulesTest {
    private fun rule(
        isLive: Boolean? = null,
        isPostLiveDvr: Boolean? = null,
        hasLiveStreamability: Boolean = false,
        hasHlsManifest: Boolean = false,
        hasAdaptiveFormats: Boolean = false,
    ) = LiveDetectionRules.isLiveNow(
        isLive = isLive,
        isPostLiveDvr = isPostLiveDvr,
        hasLiveStreamability = hasLiveStreamability,
        hasHlsManifest = hasHlsManifest,
        hasAdaptiveFormats = hasAdaptiveFormats,
    )

    @Test
    fun visionosVODShipsAnHlsManifestAndIsNotLive() {
        // The regression: 98 adaptive formats alongside a manifest. Reading this as live returns an
        // empty ladder, so codecs and audio tracks vanish from the UI.
        assertFalse(rule(hasHlsManifest = true, hasAdaptiveFormats = true))
    }

    @Test
    fun visionosShortShipsAnHlsManifestAndIsNotLive() {
        assertFalse(rule(hasHlsManifest = true, hasAdaptiveFormats = true))
    }

    @Test
    fun aBroadcastingStreamIsLive() {
        assertTrue(rule(isLive = true, hasLiveStreamability = true, hasHlsManifest = true, hasAdaptiveFormats = true))
    }

    @Test
    fun aPostLiveDvrStreamIsLiveEvenWithAnAdaptiveLadder() {
        assertTrue(rule(isPostLiveDvr = true, hasHlsManifest = true, hasAdaptiveFormats = true))
    }

    @Test
    fun liveStreamabilityAloneIsEnough() {
        assertTrue(rule(hasLiveStreamability = true))
    }

    @Test
    fun aManifestWithNothingElsePlayableIsStillTreatedAsLive() {
        // The clause's original purpose, preserved: SABR-only live responses expose no adaptive
        // formats, so the manifest is the only thing there is to play.
        assertTrue(rule(hasHlsManifest = true, hasAdaptiveFormats = false))
    }

    @Test
    fun aPlainVODWithNeitherManifestNorLiveFlagsIsNotLive() {
        assertFalse(rule(hasAdaptiveFormats = true))
    }
}
