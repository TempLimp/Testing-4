package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BufferDurationsTest {
    @Test
    fun heapCapBelowAStoredResumeThresholdStillYieldsDurationsTheLoadControlAccepts() {
        // Issue #876: a Pixel-class heap caps the min buffer at 8s while the stored resume threshold is 10s,
        // which is reachable from the Custom rebuffer slider.
        val result = sanitizeOnLowHeapDevice(minMs = 30_000, maxMs = 50_000, playbackMs = 2_500, rebufferMs = 10_000)

        assertEquals(LOW_HEAP_MIN_CAP_MS, result.minMs)
        assertEquals(LOW_HEAP_MIN_CAP_MS, result.rebufferMs)
        assertEquals(LOW_HEAP_MAX_CAP_MS, result.maxMs)
        assertEquals(2_500, result.playbackMs)
    }

    @Test
    fun theLoadControlStillRejectsTheCombinationThatCrashedOnStartup() {
        // Keeps the checks above from passing vacuously: this is what the shipped build handed Media3.
        assertFailsWith<IllegalArgumentException> { setBufferDurationsMs(LOW_HEAP_MIN_CAP_MS, LOW_HEAP_MAX_CAP_MS, 2_500, 10_000) }
    }

    @Test
    fun customSliderExtremesCannotProduceARejectedCombination() {
        // The Custom sliders write each duration independently: min bottoms out at 1s while playback and
        // rebuffer reach 5s and 10s.
        val result = sanitizeOnLowHeapDevice(minMs = 1_000, maxMs = 30_000, playbackMs = 5_000, rebufferMs = 10_000)

        assertAcceptedByLoadControl(result)
    }

    @Test
    fun durationsThatAlreadySatisfyTheContractAreLeftAlone() {
        val result =
            BufferDurations.sanitize(
                minMs = 15_000,
                maxMs = 40_000,
                playbackMs = 2_500,
                rebufferMs = 5_000,
                maxSafeMinMs = 20_000,
                maxSafeMaxMs = 45_000,
            )

        assertEquals(
            BufferDurations(minMs = 15_000, maxMs = 40_000, playbackMs = 2_500, rebufferMs = 5_000),
            result,
        )
    }

    @Test
    fun aStoredMaxBufferBelowTheMinBufferIsGivenLoadingHeadroom() {
        val result = sanitizeOnLowHeapDevice(minMs = 8_000, maxMs = 3_000, playbackMs = 1_000, rebufferMs = 2_000)

        assertEquals(LOW_HEAP_MIN_CAP_MS + BufferDurations.MAX_HEADROOM_MS, result.maxMs)
        assertAcceptedByLoadControl(result)
    }

    @Test
    fun corruptDurationsFallBackToTheFloorsInsteadOfThrowing() {
        val result = sanitizeOnLowHeapDevice(minMs = -1, maxMs = 0, playbackMs = -5_000, rebufferMs = Int.MIN_VALUE)

        assertEquals(BufferDurations.MIN_FLOOR_MS, result.minMs)
        assertEquals(BufferDurations.PLAYBACK_FLOOR_MS, result.playbackMs)
        assertEquals(BufferDurations.REBUFFER_FLOOR_MS, result.rebufferMs)
        assertAcceptedByLoadControl(result)
    }

    @Test
    fun capsSmallerThanTheFloorsDoNotInvertAnyRange() {
        val result =
            BufferDurations.sanitize(
                minMs = 30_000,
                maxMs = 50_000,
                playbackMs = 2_500,
                rebufferMs = 5_000,
                maxSafeMinMs = 0,
                maxSafeMaxMs = 0,
            )

        assertEquals(BufferDurations.MIN_FLOOR_MS, result.minMs)
        assertAcceptedByLoadControl(result)
    }

    @Test
    fun everyCombinationOfRealisticStoredValuesSurvivesEveryHeapCap() {
        val stored = listOf(-1, 0, 1_000, 2_500, 10_000, 30_000, 180_000, Int.MAX_VALUE)
        val caps = listOf(0, BufferDurations.MIN_FLOOR_MS, LOW_HEAP_MIN_CAP_MS, 20_000)

        for (min in stored) {
            for (rebuffer in stored) {
                for (playback in stored) {
                    for (cap in caps) {
                        assertAcceptedByLoadControl(
                            BufferDurations.sanitize(
                                minMs = min,
                                maxMs = stored.first(),
                                playbackMs = playback,
                                rebufferMs = rebuffer,
                                maxSafeMinMs = cap,
                                maxSafeMaxMs = cap,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun sanitizeOnLowHeapDevice(
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
    ): BufferDurations =
        BufferDurations.sanitize(
            minMs = minMs,
            maxMs = maxMs,
            playbackMs = playbackMs,
            rebufferMs = rebufferMs,
            maxSafeMinMs = LOW_HEAP_MIN_CAP_MS,
            maxSafeMaxMs = LOW_HEAP_MAX_CAP_MS,
        )

    /** Pins the assertions Media3 itself makes, so the invariant cannot drift from the one that crashes. */
    private fun assertAcceptedByLoadControl(durations: BufferDurations) {
        setBufferDurationsMs(
            durations.minMs,
            durations.maxMs,
            durations.playbackMs,
            durations.rebufferMs,
        )
        assertTrue("durations must stay positive: $durations", durations.playbackMs > 0)
    }

    /**
     * Media3 1.11.0's `DefaultLoadControl.Builder.setBufferDurationsMs` checks, in its order: Media3 is not on
     * iOS, and upstream's tests hand the durations to it to prove the crash cannot come back.
     */
    private fun setBufferDurationsMs(
        minBufferMs: Int,
        maxBufferMs: Int,
        bufferForPlaybackMs: Int,
        bufferForPlaybackAfterRebufferMs: Int,
    ) {
        require(bufferForPlaybackMs >= 0) { "bufferForPlaybackMs cannot be less than 0" }
        require(bufferForPlaybackAfterRebufferMs >= 0) { "bufferForPlaybackAfterRebufferMs cannot be less than 0" }
        require(minBufferMs >= bufferForPlaybackMs) { "minBufferMs cannot be less than bufferForPlaybackMs" }
        require(minBufferMs >= bufferForPlaybackAfterRebufferMs) { "minBufferMs cannot be less than bufferForPlaybackAfterRebufferMs" }
        require(maxBufferMs >= minBufferMs) { "maxBufferMs cannot be less than minBufferMs" }
    }

    private companion object {
        /** Mirrors PlayerConfig's low-heap budgets, which PlayerFactory passes in on Pixel-class devices. */
        const val LOW_HEAP_MIN_CAP_MS = 8_000
        const val LOW_HEAP_MAX_CAP_MS = 18_000
    }
}
