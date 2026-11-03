@file:OptIn(ExperimentalForeignApi::class)

package io.github.aedev.flow.data.local

import io.github.aedev.flow.utils.Log
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorGetCode
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemCopyMatching
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyRef
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef
import kotlin.io.encoding.Base64

internal actual object KeystoreSecretBox {
    private const val KEY_TAG = "flow_preference_secrets"
    private const val KEY_BITS = 256

    private val lock = SynchronizedObject()
    private var cachedKey: SecKeyRef? = null

    actual fun seal(plain: String): String {
        if (plain.isEmpty()) return plain
        val publicKey = SecKeyCopyPublicKey(privateKey()) ?: error("The Keychain key has no public key")
        try {
            val sealed =
                memScoped {
                    val error = alloc<CFErrorRefVar>()
                    val data =
                        withCfData(plain.encodeToByteArray()) {
                            SecKeyCreateEncryptedData(publicKey, kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM, it, error.ptr)
                        } ?: error("Sealing failed")
                    data.toByteArray().also { CFRelease(data) }
                }
            return SECRET_BOX_PREFIX + Base64.encode(sealed)
        } finally {
            CFRelease(publicKey)
        }
    }

    actual fun open(stored: String?): String {
        if (stored.isNullOrEmpty() || !stored.startsWith(SECRET_BOX_PREFIX)) return stored.orEmpty()
        return runCatching {
            val sealed = Base64.decode(stored.removePrefix(SECRET_BOX_PREFIX))
            memScoped {
                val error = alloc<CFErrorRefVar>()
                val data =
                    withCfData(sealed) {
                        SecKeyCreateDecryptedData(privateKey(), kSecKeyAlgorithmECIESEncryptionCofactorVariableIVX963SHA256AESGCM, it, error.ptr)
                    } ?: error("Opening failed")
                data.toByteArray().also { CFRelease(data) }.decodeToString()
            }
        }.getOrElse {
            Log.w(SECRET_BOX_TAG, "Stored secret could not be opened", it)
            ""
        }
    }

    /** The Keychain's key, made on first use; kept for this device only, so it never travels in a backup. */
    private fun privateKey(): SecKeyRef =
        synchronized(lock) {
            cachedKey ?: (findKey() ?: createKey()).also { cachedKey = it }
        }

    private fun findKey(): SecKeyRef? =
        withCfData(KEY_TAG.encodeToByteArray()) { tag ->
            withCfDictionary(
                kSecClass to kSecClassKey,
                kSecAttrApplicationTag to tag,
                kSecAttrKeyType to kSecAttrKeyTypeECSECPrimeRandom,
                kSecReturnRef to kCFBooleanTrue,
            ) { query ->
                memScoped {
                    val result = alloc<CFTypeRefVar>()
                    if (SecItemCopyMatching(query, result.ptr) == errSecSuccess) result.value?.reinterpret() else null
                }
            }
        }

    private fun createKey(): SecKeyRef =
        withCfData(KEY_TAG.encodeToByteArray()) { tag ->
            withCfNumber(KEY_BITS) { bits ->
                withCfDictionary(
                    kSecAttrIsPermanent to kCFBooleanTrue,
                    kSecAttrApplicationTag to tag,
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                ) { privateAttributes ->
                    withCfDictionary(
                        kSecAttrKeyType to kSecAttrKeyTypeECSECPrimeRandom,
                        kSecAttrKeySizeInBits to bits,
                        kSecPrivateKeyAttrs to privateAttributes,
                    ) { attributes ->
                        memScoped {
                            val error = alloc<CFErrorRefVar>()
                            SecKeyCreateRandomKey(attributes, error.ptr)
                                ?: error("The Keychain refused the key: ${error.value?.let(::CFErrorGetCode)}")
                        }
                    }
                }
            }
        }
}

private fun CFDataRef.toByteArray(): ByteArray {
    val length = CFDataGetLength(this).toInt()
    return if (length == 0) ByteArray(0) else CFDataGetBytePtr(this)!!.readBytes(length)
}

private inline fun <T> withCfData(
    bytes: ByteArray,
    block: (CFDataRef) -> T,
): T {
    val data =
        if (bytes.isEmpty()) {
            CFDataCreate(null, null, 0)
        } else {
            bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.convert()) }
        } ?: error("CFDataCreate failed")
    try {
        return block(data)
    } finally {
        CFRelease(data)
    }
}

private inline fun <T> withCfNumber(
    value: Int,
    block: (CFTypeRef) -> T,
): T {
    val number =
        memScoped {
            val boxed = alloc<IntVar>().apply { this.value = value }
            CFNumberCreate(null, kCFNumberIntType, boxed.ptr)
        } ?: error("CFNumberCreate failed")
    try {
        return block(number)
    } finally {
        CFRelease(number)
    }
}

private inline fun <T> withCfDictionary(
    vararg entries: Pair<CPointer<*>?, CPointer<*>?>,
    block: (CFDictionaryRef) -> T,
): T {
    val dictionary =
        CFDictionaryCreateMutable(null, entries.size.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            ?: error("CFDictionaryCreateMutable failed")
    try {
        entries.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
        return block(dictionary)
    } finally {
        CFRelease(dictionary)
    }
}
