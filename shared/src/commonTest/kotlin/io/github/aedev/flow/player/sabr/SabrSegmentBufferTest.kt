package io.github.aedev.flow.player.sabr

import io.github.aedev.flow.player.sabr.integration.SabrSegmentBuffer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

// Upstream's reads block an executor thread; the port's read suspends, so a coroutine stands in for the thread.
class SabrSegmentBufferTest {
    @Test
    fun nonEmptyReadWaitsForMediaInsteadOfReturningZero() =
        runBlocking {
            val buffer = SabrSegmentBuffer()
            val target = ByteArray(3)
            try {
                val read = async { buffer.read(target, 0, target.size) }

                delay(100)
                buffer.appendSegment(byteArrayOf(4, 5, 6))

                assertEquals(3, withTimeout(2_000) { read.await() })
                assertContentEquals(byteArrayOf(4, 5, 6), target)
            } finally {
                buffer.close()
            }
        }

    @Test
    fun endOfStreamUnblocksWaitingRead() =
        runBlocking {
            val buffer = SabrSegmentBuffer()
            try {
                val read = async { buffer.read(ByteArray(1), 0, 1) }

                buffer.signalEndOfStream()

                assertEquals(-1, withTimeout(2_000) { read.await() })
            } finally {
                buffer.close()
            }
        }
}