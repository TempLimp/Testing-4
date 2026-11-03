package io.github.aedev.flow.data.video.downloader.work

import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

class StallLimitTest {
    private val limit = StallLimit(ticks = 3) {}

    @Test
    fun aByteCountThatKeepsMovingNeverStalls() {
        (1L..100L).forEach { assertFalse(limit.stillAt(it * 1_000)) }
    }

    @Test
    fun aByteCountStuckForTheLimitStalls() {
        assertFalse(limit.stillAt(500))
        assertFalse(limit.stillAt(500))
        assertFalse(limit.stillAt(500))
        assertTrue(limit.stillAt(500))
    }

    @Test
    fun oneNewByteStartsTheCountAgain() {
        repeat(3) { limit.stillAt(500) }
        assertFalse(limit.stillAt(501))
        assertFalse(limit.stillAt(501))
        assertFalse(limit.stillAt(501))
        assertTrue(limit.stillAt(501))
    }

    @Test
    fun aTransferThatNeverStartsStallsToo() {
        assertFalse(limit.stillAt(0))
        repeat(2) { assertFalse(limit.stillAt(0)) }
        assertTrue(limit.stillAt(0))
    }
}
