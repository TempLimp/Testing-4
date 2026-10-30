package io.github.aedev.flow.utils.cipher

import java.security.MessageDigest

internal actual fun md5(data: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(data)
