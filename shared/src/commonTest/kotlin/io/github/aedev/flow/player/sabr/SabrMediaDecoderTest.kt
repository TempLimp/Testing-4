package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.core.SabrMediaDecoder
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class SabrMediaDecoderTest {
    @Test
    fun uncompressedMediaIsReturnedUnchanged() {
        val media = byteArrayOf(1, 2, 3)

        assertContentEquals(media, SabrMediaDecoder.decode(0, media))
    }

    @Test
    fun gzipMediaIsDecodedBeforeDelivery() {
        val media = "SABR segment payload".encodeToByteArray()
        // GZIPOutputStream is jvm-only; this is Python's gzip.compress of the same text with mtime=0.
        val compressed =
            byteArrayOf(
                31, -117, 8, 0, 0, 0, 0, 0, 2, -1, 11, 118, 116, 10, 82, 40, 78, 77, -49, 77, -51, 43, 81, 40, 72, -84, -52,
                -55, 79, 76, 1, 0, -8, 22, 19, 102, 20, 0, 0, 0,
            )

        assertContentEquals(media, SabrMediaDecoder.decode(1, compressed))
    }

    // Not upstream's: iOS decodes brotli with its own library, so both decoders read one stream. It is the smallest
    // valid one: a window of 2^16, one uncompressed meta-block of the 20 bytes, then an empty last meta-block (RFC 7932).
    @Test
    fun brotliMediaIsDecodedBeforeDelivery() {
        val media = "SABR segment payload".encodeToByteArray()
        val compressed = byteArrayOf(0x30, 0x01, 0x10) + media + byteArrayOf(0x03)

        assertContentEquals(media, SabrMediaDecoder.decode(2, compressed))
    }

    @Test
    fun unknownCompressionIsRejected() {
        assertFailsWith<IOException> {
            SabrMediaDecoder.decode(99, byteArrayOf(1))
        }
    }
}