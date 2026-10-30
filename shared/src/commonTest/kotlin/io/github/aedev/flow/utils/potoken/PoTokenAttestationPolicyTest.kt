package io.github.aedev.flow.utils.potoken

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class PoTokenAttestationPolicyTest {
    private fun token(characters: Int): String {
        val unpadded = "A".repeat(characters)
        return unpadded + "=".repeat((4 - unpadded.length % 4) % 4)
    }

    @Test
    fun aWarmFullTrustSessionIsReusedAsIs() {
        val reattest =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = true,
                isExpired = false,
                sessionIdChanged = false,
                lastTokenWasLowTrust = false,
            )

        assertThat(reattest).isFalse()
    }

    @Test
    fun noSessionYetForcesAnAttestation() {
        val reattest =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = false,
                isExpired = false,
                sessionIdChanged = false,
                lastTokenWasLowTrust = false,
            )

        assertThat(reattest).isTrue()
    }

    @Test
    fun aLowTrustTokenIsNeverPinned() {
        val reattest =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = true,
                isExpired = false,
                sessionIdChanged = false,
                lastTokenWasLowTrust = true,
            )

        assertThat(reattest).isTrue()
    }

    @Test
    fun expiryAndASessionChangeEachForceAnAttestation() {
        val expired =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = true,
                isExpired = true,
                sessionIdChanged = false,
                lastTokenWasLowTrust = false,
            )
        val newSession =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = false,
                hasSession = true,
                isExpired = false,
                sessionIdChanged = true,
                lastTokenWasLowTrust = false,
            )

        assertThat(expired).isTrue()
        assertThat(newSession).isTrue()
    }

    @Test
    fun aCallerForcingRecreationAlwaysWins() {
        val reattest =
            PoTokenAttestationPolicy.shouldReattest(
                forceRecreate = true,
                hasSession = true,
                isExpired = false,
                sessionIdChanged = false,
                lastTokenWasLowTrust = false,
            )

        assertThat(reattest).isTrue()
    }

    @Test
    fun tokenLengthIgnoresBase64Padding() {
        assertThat(PoTokenAttestationPolicy.tokenLength("QUJD")).isEqualTo(4)
        assertThat(PoTokenAttestationPolicy.tokenLength("QUJDRA==")).isEqualTo(6)
    }

    @Test
    fun aGenuinelyShortAttestationIsTreatedAsLowTrust() {
        assertThat(PoTokenAttestationPolicy.isLowTrust(token(88))).isTrue()
    }

    @Test
    fun theTokenLengthsYouTubeActuallyReturnsAreAccepted() {
        // Measured on device: every one of 88 mints came back at one of these two lengths, and
        // playback on them was healthy. The old byte-based threshold rejected both.
        assertThat(PoTokenAttestationPolicy.isLowTrust(token(116))).isFalse()
        assertThat(PoTokenAttestationPolicy.isLowTrust(token(120))).isFalse()
    }

    @Test
    fun theDocumentedFullTrustFloorIsAccepted() {
        assertThat(PoTokenAttestationPolicy.isLowTrust(token(110))).isFalse()
    }
}
