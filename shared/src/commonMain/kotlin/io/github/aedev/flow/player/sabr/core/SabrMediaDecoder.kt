package io.github.aedev.flow.player.sabr.core

import kotlinx.io.IOException

internal object SabrMediaDecoder {
    private const val COMPRESSION_NONE = 0
    private const val COMPRESSION_GZIP = 1
    private const val COMPRESSION_BROTLI = 2

    fun decode(compressionType: Int, data: ByteArray): ByteArray = when (compressionType) {
        COMPRESSION_NONE -> data
        COMPRESSION_GZIP -> gunzip(data)
        COMPRESSION_BROTLI -> unbrotli(data)
        else -> throw IOException("Unsupported SABR media compression: $compressionType")
    }
}

/** GZIPInputStream's job: the platform's inflater (java.util.zip on the jvm, libcompression on iOS). */
internal expect fun gunzip(data: ByteArray): ByteArray

/** BrotliInputStream's job: org.brotli's decoder on the jvm, libcompression on iOS. */
internal expect fun unbrotli(data: ByteArray): ByteArray
