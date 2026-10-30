/*
 * Ported from NewPipe Extractor v0.26.5, AudioStream.java
 * Copyright (C) 2016 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 */

package org.schabi.newpipe.extractor.stream

import io.github.aedev.flow.utils.Locale
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem

class AudioStream private constructor(
    builder: Builder,
) : Stream(builder.id!!, builder.content!!, builder.isUrl, builder.mediaFormat, builder.deliveryMethod!!, builder.manifestUrl) {
    override val itagItem: ItagItem? = builder.itagItem
    val averageBitrate: Int = builder.averageBitrate
    val audioTrackId: String? = builder.audioTrackId
    val audioTrackName: String? = builder.audioTrackName
    val audioLocale: Locale? = builder.audioLocale
    val audioTrackType: AudioTrackType? = builder.audioTrackType

    // The DASH fields are copied from the ItagItem when the stream is built, as in NewPipe.
    val itag: Int = itagItem?.id ?: ITAG_NOT_AVAILABLE_OR_NOT_APPLICABLE
    val bitrate: Int = itagItem?.bitrate ?: 0
    val initStart: Int = itagItem?.initStart ?: 0
    val initEnd: Int = itagItem?.initEnd ?: 0
    val indexStart: Int = itagItem?.indexStart ?: 0
    val indexEnd: Int = itagItem?.indexEnd ?: 0
    val quality: String? = itagItem?.quality
    val codec: String? = itagItem?.codec

    override fun equalStats(other: Stream?): Boolean =
        super.equalStats(other) &&
            other is AudioStream &&
            averageBitrate == other.averageBitrate &&
            audioTrackId == other.audioTrackId &&
            audioTrackType == other.audioTrackType &&
            audioLocale == other.audioLocale

    class Builder {
        internal var id: String? = null
        internal var content: String? = null
        internal var isUrl = false
        internal var deliveryMethod: DeliveryMethod? = DeliveryMethod.PROGRESSIVE_HTTP
        internal var mediaFormat: MediaFormat? = null
        internal var manifestUrl: String? = null
        internal var averageBitrate = UNKNOWN_BITRATE
        internal var audioTrackId: String? = null
        internal var audioTrackName: String? = null
        internal var audioLocale: Locale? = null
        internal var audioTrackType: AudioTrackType? = null
        internal var itagItem: ItagItem? = null

        fun setId(id: String) = apply { this.id = id }

        fun setContent(
            content: String,
            isUrl: Boolean,
        ) = apply {
            this.content = content
            this.isUrl = isUrl
        }

        fun setMediaFormat(mediaFormat: MediaFormat?) = apply { this.mediaFormat = mediaFormat }

        fun setDeliveryMethod(deliveryMethod: DeliveryMethod) = apply { this.deliveryMethod = deliveryMethod }

        fun setManifestUrl(manifestUrl: String?) = apply { this.manifestUrl = manifestUrl }

        fun setAverageBitrate(averageBitrate: Int) = apply { this.averageBitrate = averageBitrate }

        fun setAudioTrackId(audioTrackId: String?) = apply { this.audioTrackId = audioTrackId }

        fun setAudioTrackName(audioTrackName: String?) = apply { this.audioTrackName = audioTrackName }

        fun setAudioTrackType(audioTrackType: AudioTrackType?) = apply { this.audioTrackType = audioTrackType }

        fun setAudioLocale(audioLocale: Locale?) = apply { this.audioLocale = audioLocale }

        fun setItagItem(itagItem: ItagItem?) = apply { this.itagItem = itagItem }

        fun build(): AudioStream {
            checkNotNull(id) {
                "The identifier of the audio stream has been not set or is null. If you are not able to get an identifier, " +
                    "use the static constant ID_UNKNOWN of the Stream class."
            }
            checkNotNull(content) {
                "The content of the audio stream has been not set or is null. Please specify a non-null one with setContent."
            }
            checkNotNull(deliveryMethod) {
                "The delivery method of the audio stream has been set as null, which is not allowed. Pass a valid one " +
                    "instead with setDeliveryMethod."
            }
            return AudioStream(this)
        }
    }

    companion object {
        const val UNKNOWN_BITRATE = -1
    }
}
