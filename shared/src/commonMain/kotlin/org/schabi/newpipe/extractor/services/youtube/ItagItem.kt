/*
 * Ported from NewPipe Extractor v0.26.5, ItagItem.java
 * Copyright (C) 2016 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 */

package org.schabi.newpipe.extractor.services.youtube

import io.github.aedev.flow.utils.Locale
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.MediaFormat.M4A
import org.schabi.newpipe.extractor.MediaFormat.MPEG_4
import org.schabi.newpipe.extractor.MediaFormat.WEBM
import org.schabi.newpipe.extractor.MediaFormat.WEBMA
import org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS
import org.schabi.newpipe.extractor.MediaFormat.v3GPP
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.ItagItem.ItagType.AUDIO
import org.schabi.newpipe.extractor.services.youtube.ItagItem.ItagType.VIDEO
import org.schabi.newpipe.extractor.services.youtube.ItagItem.ItagType.VIDEO_ONLY
import org.schabi.newpipe.extractor.stream.AudioTrackType

class ItagItem private constructor(
    val mediaFormat: MediaFormat,
    val id: Int,
    val itagType: ItagType,
) {
    enum class ItagType {
        AUDIO,
        VIDEO,
        VIDEO_ONLY,
    }

    @Deprecated("Use averageBitrate")
    var avgBitrate: Int = AVERAGE_BITRATE_UNKNOWN

    @Deprecated("Use resolutionString")
    var resolutionString: String? = null

    var fps: Int = FPS_NOT_APPLICABLE_OR_UNKNOWN
        set(value) {
            field = if (value > 0) value else FPS_NOT_APPLICABLE_OR_UNKNOWN
        }

    var sampleRate: Int = SAMPLE_RATE_UNKNOWN
        set(value) {
            field = if (value > 0) value else SAMPLE_RATE_UNKNOWN
        }

    var audioChannels: Int = AUDIO_CHANNELS_NOT_APPLICABLE_OR_UNKNOWN
        set(value) {
            field = if (value > 0) value else AUDIO_CHANNELS_NOT_APPLICABLE_OR_UNKNOWN
        }

    var bitrate: Int = 0
    var width: Int = 0
    var height: Int = 0
    var initStart: Int = 0
    var initEnd: Int = 0
    var indexStart: Int = 0
    var indexEnd: Int = 0
    var quality: String? = null
    var codec: String? = null

    var targetDurationSec: Int = TARGET_DURATION_SEC_UNKNOWN
        set(value) {
            field = if (value > 0) value else TARGET_DURATION_SEC_UNKNOWN
        }

    var approxDurationMs: Long = APPROX_DURATION_MS_UNKNOWN
        set(value) {
            field = if (value > 0) value else APPROX_DURATION_MS_UNKNOWN
        }

    var contentLength: Long = CONTENT_LENGTH_UNKNOWN
        set(value) {
            field = if (value > 0) value else CONTENT_LENGTH_UNKNOWN
        }

    var audioTrackId: String? = null
    var audioTrackName: String? = null
    var audioTrackType: AudioTrackType? = null
    var audioLocale: Locale? = null
    var isDrc: Boolean = false
    var lastModified: Long = 0
    var xtags: String? = null

    @Suppress("DEPRECATION")
    val averageBitrate: Int
        get() = avgBitrate

    // Java assigns fps directly here; the clamping setter differs only for fps <= 0, which only the copy keeps (as -1).
    @Suppress("DEPRECATION")
    constructor(id: Int, type: ItagType, format: MediaFormat, resolution: String) : this(format, id, type) {
        resolutionString = resolution
        fps = 30
    }

    @Suppress("DEPRECATION")
    constructor(id: Int, type: ItagType, format: MediaFormat, resolution: String, fps: Int) : this(format, id, type) {
        resolutionString = resolution
        this.fps = fps
    }

    @Suppress("DEPRECATION")
    constructor(id: Int, type: ItagType, format: MediaFormat, avgBitrate: Int) : this(format, id, type) {
        this.avgBitrate = avgBitrate
    }

    @Suppress("DEPRECATION")
    constructor(itagItem: ItagItem) : this(itagItem.mediaFormat, itagItem.id, itagItem.itagType) {
        avgBitrate = itagItem.avgBitrate
        sampleRate = itagItem.sampleRate
        audioChannels = itagItem.audioChannels
        resolutionString = itagItem.resolutionString
        fps = itagItem.fps
        bitrate = itagItem.bitrate
        width = itagItem.width
        height = itagItem.height
        initStart = itagItem.initStart
        initEnd = itagItem.initEnd
        indexStart = itagItem.indexStart
        indexEnd = itagItem.indexEnd
        quality = itagItem.quality
        codec = itagItem.codec
        targetDurationSec = itagItem.targetDurationSec
        approxDurationMs = itagItem.approxDurationMs
        contentLength = itagItem.contentLength
        audioTrackId = itagItem.audioTrackId
        audioTrackName = itagItem.audioTrackName
        audioTrackType = itagItem.audioTrackType
        audioLocale = itagItem.audioLocale
    }

    companion object {
        const val AVERAGE_BITRATE_UNKNOWN = -1
        const val SAMPLE_RATE_UNKNOWN = -1
        const val FPS_NOT_APPLICABLE_OR_UNKNOWN = -1
        const val TARGET_DURATION_SEC_UNKNOWN = -1
        const val AUDIO_CHANNELS_NOT_APPLICABLE_OR_UNKNOWN = -1
        const val CONTENT_LENGTH_UNKNOWN = -1L
        const val APPROX_DURATION_MS_UNKNOWN = -1L
        const val LAST_MODIFIED_UNKOWN = -1L

        private val ITAG_LIST =
            arrayOf(
                ItagItem(17, VIDEO, v3GPP, "144p"),
                ItagItem(36, VIDEO, v3GPP, "240p"),
                ItagItem(18, VIDEO, MPEG_4, "360p"),
                ItagItem(34, VIDEO, MPEG_4, "360p"),
                ItagItem(35, VIDEO, MPEG_4, "480p"),
                ItagItem(59, VIDEO, MPEG_4, "480p"),
                ItagItem(78, VIDEO, MPEG_4, "480p"),
                ItagItem(22, VIDEO, MPEG_4, "720p"),
                ItagItem(37, VIDEO, MPEG_4, "1080p"),
                ItagItem(38, VIDEO, MPEG_4, "1080p"),
                ItagItem(43, VIDEO, WEBM, "360p"),
                ItagItem(44, VIDEO, WEBM, "480p"),
                ItagItem(45, VIDEO, WEBM, "720p"),
                ItagItem(46, VIDEO, WEBM, "1080p"),
                ItagItem(171, AUDIO, WEBMA, 128),
                ItagItem(172, AUDIO, WEBMA, 256),
                ItagItem(599, AUDIO, M4A, 32),
                ItagItem(139, AUDIO, M4A, 48),
                ItagItem(140, AUDIO, M4A, 128),
                ItagItem(141, AUDIO, M4A, 256),
                ItagItem(600, AUDIO, WEBMA_OPUS, 35),
                ItagItem(249, AUDIO, WEBMA_OPUS, 50),
                ItagItem(250, AUDIO, WEBMA_OPUS, 70),
                ItagItem(251, AUDIO, WEBMA_OPUS, 160),
                ItagItem(160, VIDEO_ONLY, MPEG_4, "144p"),
                ItagItem(394, VIDEO_ONLY, MPEG_4, "144p"),
                ItagItem(133, VIDEO_ONLY, MPEG_4, "240p"),
                ItagItem(395, VIDEO_ONLY, MPEG_4, "240p"),
                ItagItem(134, VIDEO_ONLY, MPEG_4, "360p"),
                ItagItem(396, VIDEO_ONLY, MPEG_4, "360p"),
                ItagItem(135, VIDEO_ONLY, MPEG_4, "480p"),
                ItagItem(212, VIDEO_ONLY, MPEG_4, "480p"),
                ItagItem(397, VIDEO_ONLY, MPEG_4, "480p"),
                ItagItem(136, VIDEO_ONLY, MPEG_4, "720p"),
                ItagItem(398, VIDEO_ONLY, MPEG_4, "720p"),
                ItagItem(298, VIDEO_ONLY, MPEG_4, "720p60", 60),
                ItagItem(137, VIDEO_ONLY, MPEG_4, "1080p"),
                ItagItem(399, VIDEO_ONLY, MPEG_4, "1080p"),
                ItagItem(299, VIDEO_ONLY, MPEG_4, "1080p60", 60),
                ItagItem(400, VIDEO_ONLY, MPEG_4, "1440p"),
                ItagItem(266, VIDEO_ONLY, MPEG_4, "2160p"),
                ItagItem(401, VIDEO_ONLY, MPEG_4, "2160p"),
                ItagItem(278, VIDEO_ONLY, WEBM, "144p"),
                ItagItem(242, VIDEO_ONLY, WEBM, "240p"),
                ItagItem(243, VIDEO_ONLY, WEBM, "360p"),
                ItagItem(244, VIDEO_ONLY, WEBM, "480p"),
                ItagItem(245, VIDEO_ONLY, WEBM, "480p"),
                ItagItem(246, VIDEO_ONLY, WEBM, "480p"),
                ItagItem(247, VIDEO_ONLY, WEBM, "720p"),
                ItagItem(248, VIDEO_ONLY, WEBM, "1080p"),
                ItagItem(271, VIDEO_ONLY, WEBM, "1440p"),
                // #272 is either 3840x2160 (e.g. RtoitU2A-3E) or 7680x4320 (sLprVF6d7Ug)
                ItagItem(272, VIDEO_ONLY, WEBM, "2160p"),
                ItagItem(302, VIDEO_ONLY, WEBM, "720p60", 60),
                ItagItem(303, VIDEO_ONLY, WEBM, "1080p60", 60),
                ItagItem(308, VIDEO_ONLY, WEBM, "1440p60", 60),
                ItagItem(313, VIDEO_ONLY, WEBM, "2160p"),
                ItagItem(315, VIDEO_ONLY, WEBM, "2160p60", 60),
            )

        fun isSupported(itag: Int): Boolean = ITAG_LIST.any { it.id == itag }

        fun getItag(itagId: Int): ItagItem {
            val item = ITAG_LIST.firstOrNull { it.id == itagId } ?: throw ParsingException("itag $itagId is not supported")
            return ItagItem(item)
        }
    }
}
