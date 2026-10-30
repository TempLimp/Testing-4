package io.github.aedev.flow.player.stream

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.player.error.StreamDenialKind
import kotlin.test.Test

class ClientGateRegistryTest {
    private var nowMs = 0L
    private val registry = ClientGateRegistry(ttlMs = 1_000L, clockMs = { nowMs })

    @Test
    fun everyGateIsAnnouncedUnderTheUpperCasedNameTheUrlCarried() {
        val announced = mutableListOf<String>()
        val announcing = ClientGateRegistry(ttlMs = 1_000L, clockMs = { nowMs }, onGated = announced::add)

        announcing.reportGated("visionos")
        announcing.reportDenied(streamUrl(client = "ANDROID_VR", expire = 10_000L))
        announcing.reportGated(null)

        assertThat(announced).containsExactly("VISIONOS", "ANDROID_VR").inOrder()
    }

    @Test
    fun aRefusedTokenIsNeverAnnouncedAsAWallEvenOnceItDemotesTheClient() {
        val announced = mutableListOf<String>()
        val announcing = ClientGateRegistry(ttlMs = 1_000L, clockMs = { nowMs }, onGated = announced::add)

        announcing.reportRefused("MWEB")
        announcing.reportRefused("MWEB")

        assertThat(announcing.isGated("MWEB")).isTrue()
        assertThat(announced).isEmpty()
    }

    @Test
    fun aReportedClientIsDemotedUntilItsEntryLapses() {
        registry.reportGated("VISIONOS")

        assertThat(registry.isGated("VISIONOS")).isTrue()
        nowMs += 999L
        assertThat(registry.isGated("VISIONOS")).isTrue()
        nowMs += 1L
        assertThat(registry.isGated("VISIONOS")).isFalse()
    }

    @Test
    fun theClientNameIsMatchedRegardlessOfCaseBecauseItComesOffAUrl() {
        registry.reportGated("visionos")

        assertThat(registry.isGated("VISIONOS")).isTrue()
    }

    @Test
    fun anUnreportedClientIsNeverDemoted() {
        registry.reportGated("VISIONOS")

        assertThat(registry.isGated("MWEB")).isFalse()
        assertThat(registry.isGated(null)).isFalse()
        assertThat(registry.isGated("")).isFalse()
    }

    @Test
    fun reportingAgainExtendsTheDemotion() {
        registry.reportGated("ANDROID_VR")
        nowMs += 900L
        registry.reportGated("ANDROID_VR")
        nowMs += 900L

        assertThat(registry.isGated("ANDROID_VR")).isTrue()
    }

    @Test
    fun lapsedEntriesDropOutOfTheListing() {
        registry.reportGated("VISIONOS")
        registry.reportGated("MWEB")
        assertThat(registry.gatedClients()).containsExactly("VISIONOS", "MWEB")

        nowMs += 1_000L
        assertThat(registry.gatedClients()).isEmpty()
    }

    @Test
    fun oneTokenRefusalIsToleratedAndTheSecondDemotesTheClient() {
        // A single refusal can be a cold attestation the next mint fixes; demoting on it would drop
        // the only client whose token the app can mint.
        assertThat(registry.reportRefused("MWEB")).isFalse()
        assertThat(registry.isGated("MWEB")).isFalse()

        assertThat(registry.reportRefused("mweb")).isTrue()
        assertThat(registry.isGated("MWEB")).isTrue()
    }

    @Test
    fun refusalStrikesAreCountedPerClient() {
        registry.reportRefused("MWEB")
        registry.reportRefused("WEB")

        assertThat(registry.isGated("MWEB")).isFalse()
        assertThat(registry.isGated("WEB")).isFalse()
    }

    @Test
    fun aRefusalDemotionLapsesLikeAnyOther() {
        registry.reportRefused("MWEB")
        registry.reportRefused("MWEB")
        assertThat(registry.isGated("MWEB")).isTrue()

        nowMs += 1_000L

        assertThat(registry.isGated("MWEB")).isFalse()
    }

    @Test
    fun anUnnamedClientIsNeverDemotedByARefusal() {
        assertThat(registry.reportRefused(null)).isFalse()
        assertThat(registry.reportRefused("")).isFalse()
    }

    @Test
    fun clearingResetsRefusalStrikesToo() {
        registry.reportRefused("MWEB")
        registry.clear()

        assertThat(registry.reportRefused("MWEB")).isFalse()
    }

    @Test
    fun clearingRestoresEveryClientAtOnce() {
        registry.reportGated("VISIONOS")
        registry.clear()

        assertThat(registry.isGated("VISIONOS")).isFalse()
    }

    @Test
    fun aRefusedUrlWithoutATokenDemotesTheClientThatMintedIt() {
        val kind = registry.reportDenied(streamUrl(client = "VISIONOS", expire = 3_600L))

        assertThat(kind).isEqualTo(StreamDenialKind.ATTESTATION_GATED)
        assertThat(registry.isGated("VISIONOS")).isTrue()
    }

    @Test
    fun aRefusedTokenDemotesItsClientOnlyOnTheSecondRefusal() {
        val url = streamUrl(client = "MWEB", expire = 3_600L, pot = "token")

        assertThat(registry.reportDenied(url)).isEqualTo(StreamDenialKind.TOKEN_REJECTED)
        assertThat(registry.isGated("MWEB")).isFalse()
        registry.reportDenied(url)
        assertThat(registry.isGated("MWEB")).isTrue()
    }

    @Test
    fun anExpiredUrlDemotesNothingBecauseTheSameClientCanMintAFreshOne() {
        nowMs = 7_200_000L

        val kind = registry.reportDenied(streamUrl(client = "VISIONOS", expire = 3_600L))

        assertThat(kind).isEqualTo(StreamDenialKind.URL_EXPIRED)
        assertThat(registry.isGated("VISIONOS")).isFalse()
    }

    @Test
    fun aUrlThatIsNotAStreamUrlDemotesNothing() {
        assertThat(registry.reportDenied("https://example.com/audio.m4a")).isEqualTo(StreamDenialKind.UNKNOWN)
        assertThat(registry.reportDenied(null)).isEqualTo(StreamDenialKind.UNKNOWN)
        assertThat(registry.gatedClients()).isEmpty()
    }

    private fun streamUrl(
        client: String,
        expire: Long,
        pot: String? = null,
    ): String =
        "https://rr1---sn-a.googlevideo.com/videoplayback?expire=$expire&itag=251&c=$client" +
            (pot?.let { "&pot=$it" } ?: "")
}
