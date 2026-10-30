package io.github.aedev.flow.player.quality

import io.github.aedev.flow.player.config.VideoSizeCap
import io.github.aedev.flow.player.config.VideoTrackParameters
import io.github.aedev.flow.player.config.adaptiveTrackSelectorDefaults
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * Upstream's test, on the parameter model the iOS track selector applies (Media3's
 * `DefaultTrackSelector.Parameters` upstream): the same builder calls, the same expectations.
 */
class AdaptiveTrackSelectorDefaultsTest {
    private fun manualLivePick(): VideoTrackParameters =
        VideoTrackParameters()
            .copy(minVideoWidth = 0, minVideoHeight = 143)
            .copy(maxVideoWidth = Int.MAX_VALUE, maxVideoHeight = 144)
            .copy(forceHighestSupportedBitrate = true)

    @Test
    fun defaultsKeepTheHeapCap() {
        val params = adaptiveTrackSelectorDefaults(manualLivePick(), VideoSizeCap(1920, 1080))

        assertThat(params.maxVideoWidth).isEqualTo(1920)
        assertThat(params.maxVideoHeight).isEqualTo(1080)
    }

    @Test
    fun defaultsKeepThe4KCapOnALargeHeap() {
        val params = adaptiveTrackSelectorDefaults(manualLivePick(), VideoSizeCap.UHD)

        assertThat(params.maxVideoWidth).isEqualTo(3840)
        assertThat(params.maxVideoHeight).isEqualTo(2160)
    }

    @Test
    fun defaultsLiftTheManualFloorAndForcedBitrate() {
        val params = adaptiveTrackSelectorDefaults(manualLivePick(), VideoSizeCap.UHD)

        assertThat(params.minVideoHeight).isEqualTo(0)
        assertThat(params.forceHighestSupportedBitrate).isFalse()
    }

    @Test
    fun defaultsPutBackTheScreenViewportALocalFileCleared() {
        val afterLocalFile = manualLivePick().clearViewportSizeConstraints()

        val params = adaptiveTrackSelectorDefaults(afterLocalFile, VideoSizeCap.UHD)

        assertThat(params.isViewportSizeLimitedByPhysicalDisplaySize).isTrue()
    }

    @Test
    fun theHeapCapTightensOnSmallerHeaps() {
        assertThat(VideoSizeCap.forHeap(memoryClassMb = 192, isLowRamDevice = false)).isEqualTo(VideoSizeCap(1920, 1080))
        assertThat(VideoSizeCap.forHeap(memoryClassMb = 512, isLowRamDevice = true)).isEqualTo(VideoSizeCap(1920, 1080))
        assertThat(VideoSizeCap.forHeap(memoryClassMb = 384, isLowRamDevice = false)).isEqualTo(VideoSizeCap(2560, 1440))
        assertThat(VideoSizeCap.forHeap(memoryClassMb = 512, isLowRamDevice = false)).isEqualTo(VideoSizeCap.UHD)
    }
}
