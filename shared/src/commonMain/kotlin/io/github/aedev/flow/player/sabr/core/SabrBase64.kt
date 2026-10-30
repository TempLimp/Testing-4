package io.github.aedev.flow.player.sabr.core

import kotlin.io.encoding.Base64

object SabrBase64 {
    fun decode(value: String): ByteArray? {
        val normalized = value
            .filterNot(Char::isWhitespace)
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
        if (normalized.isEmpty() || normalized.length % 4 == 1) return null

        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        // okio's decodeBase64: null rather than an exception on bad input.
        return runCatching { Base64.decode(padded) }.getOrNull()
    }
}
