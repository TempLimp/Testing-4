package io.github.aedev.flow.player.config

/**
 * The largest frame the track selector may pick: never above 4K, lower on a small app heap.
 *
 * Upstream's `forDevice(Context)` reads ActivityManager's memory class; iOS has no app heap class, so [forDevice]
 * reads the device's memory ([forPhysicalMemory]).
 */
data class VideoSizeCap(
    val maxWidth: Int,
    val maxHeight: Int,
) {
    companion object {
        val UHD = VideoSizeCap(PlayerConfig.MAX_VIDEO_WIDTH, PlayerConfig.MAX_VIDEO_HEIGHT)

        fun forHeap(
            memoryClassMb: Int,
            isLowRamDevice: Boolean,
        ): VideoSizeCap =
            when {
                isLowRamDevice || memoryClassMb <= 256 -> VideoSizeCap(1920, 1080)
                memoryClassMb <= 384 -> VideoSizeCap(2560, 1440)
                else -> UHD
            }

        /**
         * iOS's counterpart of [forHeap]: an iPhone with 4 GB or more decodes and shows 2160p (the S04b phone gate
         * sustained 2160p60 on an A14 with 6 GB), 3 GB gets 1440p, less 1080p.
         */
        fun forPhysicalMemory(bytes: Long): VideoSizeCap =
            when {
                bytes >= 4L * 1024 * 1024 * 1024 - SLACK -> UHD
                bytes >= 3L * 1024 * 1024 * 1024 - SLACK -> VideoSizeCap(2560, 1440)
                else -> VideoSizeCap(1920, 1080)
            }

        // A device's reported memory is a little under its marketed size.
        private const val SLACK = 256L * 1024 * 1024

        fun forDevice(): VideoSizeCap = forPhysicalMemory(devicePhysicalMemoryBytes())
    }
}

/**
 * The video half of Media3's `DefaultTrackSelector.Parameters` that Flow sets. On iOS
 * [io.github.aedev.flow.player.quality.VideoTrackSelector] turns them into the AVPlayerItem's preferred
 * maximum resolution and peak bit rate, which is all AVPlayer lets an app choose about an HLS rendition.
 */
data class VideoTrackParameters(
    val preferredVideoMimeTypes: List<String> = emptyList(),
    val allowVideoMixedMimeTypeAdaptiveness: Boolean = false,
    val allowMultipleAdaptiveSelections: Boolean = true,
    val minVideoWidth: Int = 0,
    val minVideoHeight: Int = 0,
    val maxVideoWidth: Int = Int.MAX_VALUE,
    val maxVideoHeight: Int = Int.MAX_VALUE,
    val isViewportSizeLimitedByPhysicalDisplaySize: Boolean = true,
    val forceHighestSupportedBitrate: Boolean = false,
) {
    fun clearViewportSizeConstraints(): VideoTrackParameters = copy(isViewportSizeLimitedByPhysicalDisplaySize = false)
}

/**
 * Drops every per-quality size constraint and puts the device limits back. Clearing alone would also
 * lift the heap cap, and a local file clears the screen viewport, so an automatic pick could climb to
 * a size the device can neither decode comfortably nor show.
 */
fun VideoTrackParameters.resetVideoSizeTo(cap: VideoSizeCap): VideoTrackParameters =
    copy(
        minVideoWidth = 0,
        minVideoHeight = 0,
        maxVideoWidth = cap.maxWidth,
        maxVideoHeight = cap.maxHeight,
        isViewportSizeLimitedByPhysicalDisplaySize = true,
    )

/** Automatic quality: any size up to the device cap, no forced bitrate, the default codec order. */
internal fun adaptiveTrackSelectorDefaults(
    builder: VideoTrackParameters,
    cap: VideoSizeCap,
): VideoTrackParameters =
    builder
        .copy(
            preferredVideoMimeTypes = PlayerConfig.PREFERRED_VIDEO_MIME_TYPES.toList(),
            allowVideoMixedMimeTypeAdaptiveness = false,
            allowMultipleAdaptiveSelections = true,
        ).resetVideoSizeTo(cap)
        .copy(forceHighestSupportedBitrate = false)

/** The device's memory in bytes (NSProcessInfo.physicalMemory on iOS). */
internal expect fun devicePhysicalMemoryBytes(): Long

/** Media3's `clearVideoSizeConstraints`: no minimum and no maximum size. */
fun VideoTrackParameters.clearVideoSizeConstraints(): VideoTrackParameters =
    copy(minVideoWidth = 0, minVideoHeight = 0, maxVideoWidth = Int.MAX_VALUE, maxVideoHeight = Int.MAX_VALUE)
