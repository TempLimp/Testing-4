package io.github.aedev.flow.data.local

internal const val SECRET_BOX_TAG = "KeystoreSecretBox"
internal const val SECRET_BOX_PREFIX = "ks1:"

/**
 * Seals short secrets with a key that never leaves the device's keystore, so a secret copied
 * out of the preferences file (a backup, another device) is useless anywhere else. Upstream holds an
 * AES-GCM key in the Android Keystore; iOS holds a P-256 key in the Keychain, this device only, and
 * seals with ECIES (AES-GCM under a key agreed with it).
 */
internal expect object KeystoreSecretBox {
    fun seal(plain: String): String

    /**
     * The secret inside [stored]. A value saved before sealing existed is returned as it is; one that
     * no longer opens (the key was wiped with the app's data) reads as empty rather than as garbage.
     */
    fun open(stored: String?): String
}
