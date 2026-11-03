package io.github.aedev.flow.data.local

import io.github.aedev.flow.utils.Log
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

/** The jvm test target's box: upstream's AES-GCM, with a key that lives as long as the process (never shipped). */
internal actual object KeystoreSecretBox {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val key: SecretKey by lazy { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }

    actual fun seal(plain: String): String {
        if (plain.isEmpty()) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        return SECRET_BOX_PREFIX + Base64.encode(cipher.iv + cipher.doFinal(plain.encodeToByteArray()))
    }

    actual fun open(stored: String?): String {
        if (stored.isNullOrEmpty() || !stored.startsWith(SECRET_BOX_PREFIX)) return stored.orEmpty()
        return runCatching {
            val sealed = Base64.decode(stored.removePrefix(SECRET_BOX_PREFIX))
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
                }
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).decodeToString()
        }.getOrElse {
            Log.w(SECRET_BOX_TAG, "Stored secret could not be opened", it)
            ""
        }
    }
}
