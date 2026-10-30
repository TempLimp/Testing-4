@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("DEPRECATION")

package io.github.aedev.flow.utils.cipher

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH

// CommonCrypto's MD5, deprecated for security use; here it only names a script.
internal actual fun md5(data: ByteArray): ByteArray {
    val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
    data.usePinned { input ->
        digest.usePinned { output ->
            CC_MD5(if (data.isEmpty()) null else input.addressOf(0), data.size.convert(), output.addressOf(0).reinterpret())
        }
    }
    return digest
}
