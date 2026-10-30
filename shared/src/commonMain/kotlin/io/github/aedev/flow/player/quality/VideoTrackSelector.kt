package io.github.aedev.flow.player.quality

import io.github.aedev.flow.player.config.VideoTrackParameters

/**
 * The two Media3 objects [QualityManager] reads, as the platform player supplies them: upstream hands
 * it ExoPlayer's `DefaultBandwidthMeter` and `DefaultTrackSelector`; on iOS the AVPlayerItem's access
 * log estimates bandwidth and its preferred resolution and peak bit rate select the rendition.
 */
interface BandwidthMeter {
    /** Bits per second, as `DefaultBandwidthMeter.getBitrateEstimate()`. */
    val bitrateEstimate: Long
}

interface VideoTrackSelector {
    var parameters: VideoTrackParameters
}
