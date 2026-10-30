package io.github.aedev.flow.player.sabr.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object SabrCpn {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    // SecureRandom's uniform pick from 64 characters, from random UUIDs (Kotlin draws them from the
    // platform's secure generator). Byte 6 holds the UUID version, which would bias its low six bits, so it is skipped.
    @OptIn(ExperimentalUuidApi::class)
    fun generate(): String {
        val bytes = (Uuid.random().toByteArray() + Uuid.random().toByteArray()).filterIndexed { index, _ -> index % 16 != 6 }
        return buildString { bytes.take(16).forEach { append(CHARS[it.toInt() and 0x3F]) } }
    }
}
