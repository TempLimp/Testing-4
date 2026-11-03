package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.skipped
import kotlin.test.Test

/** Upstream has no test of the box (it needs the Android Keystore); these hold the port's to upstream's contract. */
class KeystoreSecretBoxTest {
    /**
     * The Keychain answers errSecMissingEntitlement (-34018) to a process that carries no entitlements, which the
     * simulator's Kotlin test runner may be; the test then says so instead of failing on the runner.
     */
    private fun withKeychain(block: () -> Unit) {
        val probe = runCatching { KeystoreSecretBox.seal("probe") }
        if (probe.exceptionOrNull()?.message?.contains("-34018") == true) {
            skipped("KEYCHAIN: unavailable to this test process (-34018)")
            return
        }
        probe.getOrThrow()
        block()
    }

    @Test
    fun aSealedSecretOpensToItself() =
        withKeychain {
            val sealed = KeystoreSecretBox.seal("hunter2 ü")

            assertThat(sealed).startsWith(SECRET_BOX_PREFIX)
            assertThat(sealed).doesNotContain("hunter2")
            assertThat(KeystoreSecretBox.open(sealed)).isEqualTo("hunter2 ü")
        }

    @Test
    fun sealingTwiceGivesDifferentBoxes() =
        withKeychain {
            assertThat(KeystoreSecretBox.seal("same")).isNotEqualTo(KeystoreSecretBox.seal("same"))
        }

    @Test
    fun anEmptySecretStaysEmpty() {
        assertThat(KeystoreSecretBox.seal("")).isEqualTo("")
        assertThat(KeystoreSecretBox.open("")).isEqualTo("")
        assertThat(KeystoreSecretBox.open(null)).isEqualTo("")
    }

    @Test
    fun aValueSavedBeforeSealingExistedReadsAsItIs() {
        assertThat(KeystoreSecretBox.open("plain password")).isEqualTo("plain password")
    }

    @Test
    fun aBoxThatNoLongerOpensReadsAsEmpty() =
        withKeychain {
            val sealed = KeystoreSecretBox.seal("secret")
            val tampered = sealed.dropLast(4) + (if (sealed.endsWith("AAAA")) "BBBB" else "AAAA")

            assertThat(KeystoreSecretBox.open(tampered)).isEqualTo("")
            assertThat(KeystoreSecretBox.open(SECRET_BOX_PREFIX + "not base64 !")).isEqualTo("")
        }
}
