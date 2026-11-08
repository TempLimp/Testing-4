package io.github.aedev.flow.player.factory

import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The Shorts and music profiles are fixed constants rather than user preferences, so nothing else
 * re-checks them: [io.github.aedev.flow.data.local.BufferDurationsTest] only covers the video path,
 * whose durations come from DataStore. These tests are what stops an edit to the constants from
 * drifting from the load control contract (`setBufferDurationsMs`' checks, kept here as [assertContractHolds]).
 */
class LoadControlFactoryTest {
    @Test
    fun shortsProfileIsAcceptedByTheLoadControl() {
        LoadControlFactory.forShorts().assertAccepted()
    }

    @Test
    fun musicProfileIsAcceptedByTheLoadControl() {
        LoadControlFactory.forMusic().assertAccepted()
    }

    @Test
    fun shortsConstantsSatisfyTheLoadControlContractBeforeAnyCoercion() {
        assertContractHolds(
            profile = "shorts",
            minMs = PlayerConfig.SHORTS_MIN_BUFFER_MS,
            maxMs = PlayerConfig.SHORTS_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.SHORTS_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.SHORTS_BUFFER_FOR_REBUFFER_MS,
        )
    }

    @Test
    fun musicConstantsSatisfyTheLoadControlContractBeforeAnyCoercion() {
        assertContractHolds(
            profile = "music",
            minMs = PlayerConfig.MUSIC_MIN_BUFFER_MS,
            maxMs = PlayerConfig.MUSIC_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.MUSIC_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.MUSIC_BUFFER_FOR_REBUFFER_MS,
        )
    }

    @Test
    fun theStockProfilesKeepTheByteTargetsTheyAlwaysHad() {
        fun bytes(memoryClassMb: Int) =
            LoadControlFactory.videoBudget(memoryClassMb, isLowRamDevice = false, requestedMaxMs = 50_000).targetBufferBytes

        assertEquals(PlayerConfig.LOW_MEMORY_MAIN_TARGET_BUFFER_BYTES, bytes(256))
        assertEquals(PlayerConfig.MID_MEMORY_MAIN_TARGET_BUFFER_BYTES, bytes(384))
        assertEquals(PlayerConfig.MAIN_TARGET_BUFFER_BYTES, bytes(512))
    }

    @Test
    fun aLongCustomBufferIsHonouredUpToAFifthOfTheHeap() {
        val pixelHeap = LoadControlFactory.videoBudget(memoryClassMb = 256, isLowRamDevice = false, requestedMaxMs = 180_000)
        val bigHeap = LoadControlFactory.videoBudget(memoryClassMb = 1024, isLowRamDevice = false, requestedMaxMs = 180_000)

        assertEquals(180_000, pixelHeap.maxSafeMaxMs)
        assertEquals(256 * MB / 5, pixelHeap.targetBufferBytes)
        assertEquals(PlayerConfig.MAX_LONG_BUFFER_BYTES, bigHeap.targetBufferBytes)
        assertEquals(PlayerConfig.LOW_MEMORY_MAX_SAFE_MAIN_MIN_BUFFER_MS, pixelHeap.maxSafeMinMs)
    }

    @Test
    fun aLowRamDeviceKeepsItsShortBufferWhateverWasAsked() {
        val budget = LoadControlFactory.videoBudget(memoryClassMb = 192, isLowRamDevice = true, requestedMaxMs = 180_000)

        assertEquals(PlayerConfig.LOW_MEMORY_MAX_SAFE_MAIN_BUFFER_MS, budget.maxSafeMaxMs)
        assertEquals(PlayerConfig.LOW_MEMORY_MAIN_TARGET_BUFFER_BYTES, budget.targetBufferBytes)
    }

    @Test
    fun theUsersVideoBufferIsCappedByTheDeviceAndKeptValid() {
        val control = LoadControlFactory.forVideo(minMs = 60_000, maxMs = 400_000, playbackMs = 2_500, rebufferMs = 5_000, memoryClassMb = 512, isLowRamDevice = false)

        assertEquals(PlayerConfig.MAX_SAFE_MAIN_BUFFER_MS, control.maxBufferMs)
        control.assertAccepted()
    }

    @Test
    fun theContractCheckStillRejectsAProfileThatInvertsMinAndRebuffer() {
        // Keeps the checks above from passing vacuously.
        assertFailsWith<IllegalArgumentException> { assertContractHolds("inverted", 1_500, 8_000, 250, 5_000) }
    }

    private fun LoadControl.assertAccepted() =
        assertContractHolds("built", minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)

    /**
     * Asserts the raw constants, not the coerced output of the factory's build — the coercion
     * is a backstop, and a profile that only survives because of it has drifted from its intent.
     */
    private fun assertContractHolds(
        profile: String,
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
    ) {
        require(playbackMs >= 0) { "$profile: playback ($playbackMs) cannot be less than 0" }
        require(rebufferMs >= 0) { "$profile: rebuffer ($rebufferMs) cannot be less than 0" }
        require(minMs >= playbackMs) { "$profile: min ($minMs) must be >= playback ($playbackMs)" }
        require(minMs >= rebufferMs) { "$profile: min ($minMs) must be >= rebuffer ($rebufferMs)" }
        require(maxMs >= minMs) { "$profile: max ($maxMs) must be >= min ($minMs)" }
    }

    private companion object {
        const val MB = 1024 * 1024
    }
}
