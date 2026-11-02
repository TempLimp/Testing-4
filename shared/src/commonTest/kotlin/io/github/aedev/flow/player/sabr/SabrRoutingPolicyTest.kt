package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class SabrRoutingPolicyTest {
    @Test
    fun directLaddersBelowTheFloorAreUpgradeCandidates() {
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(360)).isTrue()
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(480)).isTrue()
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(0)).isTrue()
    }

    @Test
    fun directLaddersAtOrAboveTheFloorAreAcceptedAsIs() {
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(720)).isFalse()
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(1080)).isFalse()
        assertThat(SabrRoutingPolicy.shouldAttemptSabrUpgrade(2160)).isFalse()
    }

    @Test
    fun sabrIsPreferredOnlyWhenItBeatsTheDirectCeiling() {
        // SABR 1080p vs direct 360p -> upgrade
        assertThat(SabrRoutingPolicy.shouldPreferSabr(false, 1080, 360)).isTrue()
        // SABR equal to direct -> keep direct (no needless session)
        assertThat(SabrRoutingPolicy.shouldPreferSabr(false, 1080, 1080)).isFalse()
        // SABR lower than direct -> keep direct
        assertThat(SabrRoutingPolicy.shouldPreferSabr(false, 720, 1080)).isFalse()
        // No SABR height -> never prefer
        assertThat(SabrRoutingPolicy.shouldPreferSabr(false, 0, 0)).isFalse()
    }

    @Test
    fun forcedEscalationAlwaysPrefersSabrRegardlessOfHeights() {
        // A 403/expiry reload forces SABR even if heights look equal or unknown
        assertThat(SabrRoutingPolicy.shouldPreferSabr(true, 0, 1080)).isTrue()
        assertThat(SabrRoutingPolicy.shouldPreferSabr(true, 1080, 1080)).isTrue()
    }

    @Test
    fun anUpgradeThatTimedOutOrResolvedNothingKeepsThePlayableDirectResult() {
        assertThat(SabrRoutingPolicy.upgradeReplacesDirect(directMaxHeight = 480, sabrHeight = null)).isFalse()
    }

    @Test
    fun anUpgradeReplacesTheDirectLadderOnlyWhenItIsTaller() {
        assertThat(SabrRoutingPolicy.upgradeReplacesDirect(directMaxHeight = 480, sabrHeight = 1080)).isTrue()
        assertThat(SabrRoutingPolicy.upgradeReplacesDirect(directMaxHeight = 480, sabrHeight = 480)).isFalse()
        assertThat(SabrRoutingPolicy.upgradeReplacesDirect(directMaxHeight = 480, sabrHeight = 360)).isFalse()
    }
}