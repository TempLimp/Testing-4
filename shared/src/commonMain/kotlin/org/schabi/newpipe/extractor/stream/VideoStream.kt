/*
 * Ported from NewPipe Extractor v0.26.5, VideoStream.java
 * Copyright (C) 2016 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 */

package org.schabi.newpipe.extractor.stream

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem

class VideoStream private constructor(
    id: String,
    content: String,
    isUrl: Boolean,
    format: MediaFormat?,
    deliveryMethod: DeliveryMethod,
    val resolution: String,
    val isVideoOnly: Boolean,
    manifestUrl: String?,
    override val itagItem: ItagItem?,
) : Stream(id, content, isUrl, format, deliveryMethod, manifestUrl) {
    // The DASH fields are copied from the ItagItem when the stream is built, as in NewPipe.
    val itag: Int = itagItem?.id ?: ITAG_NOT_AVAILABLE_OR_NOT_APPLICABLE
    val bitrate: Int = itagItem?.bitrate ?: 0
    val initStart: Int = itagItem?.initStart ?: 0
    val initEnd: Int = itagItem?.initEnd ?: 0
    val indexStart: Int = itagItem?.indexStart ?: 0
    val indexEnd: Int = itagItem?.indexEnd ?: 0
    val width: Int = itagItem?.width ?: 0
    val height: Int = itagItem?.height ?: 0
    val fps: Int = itagItem?.fps ?: 0
    val quality: String? = itagItem?.quality
    val codec: String? = itagItem?.codec

    override fun equalStats(other: Stream?): Boolean =
        super.equalStats(other) &&
            other is VideoStream &&
            resolution == other.resolution &&
            isVideoOnly == other.isVideoOnly

    class Builder {
        private var id: String? = null
        private var content: String? = null
        private var isUrl = false
        private var deliveryMethod: DeliveryMethod? = DeliveryMethod.PROGRESSIVE_HTTP
        private var mediaFormat: MediaFormat? = null
        private var manifestUrl: String? = null
        private var isVideoOnly: Boolean? = null
        private var resolution: String? = null
        private var itagItem: ItagItem? = null

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

        fun setIsVideoOnly(isVideoOnly: Boolean) = apply { this.isVideoOnly = isVideoOnly }

        fun setResolution(resolution: String) = apply { this.resolution = resolution }

        fun setItagItem(itagItem: ItagItem?) = apply { this.itagItem = itagItem }

        fun build(): VideoStream {
            val id =
                id ?: throw IllegalStateException(
                    "The identifier of the video stream has been not set or is null. If you are not able to get an " +
                        "identifier, use the static constant ID_UNKNOWN of the Stream class.",
                )
            val content =
                content ?: throw IllegalStateException(
                    "The content of the video stream has been not set or is null. Please specify a non-null one with setContent.",
                )
            val deliveryMethod =
                deliveryMethod ?: throw IllegalStateException(
                    "The delivery method of the video stream has been set as null, which is not allowed. Pass a valid one " +
                        "instead with setDeliveryMethod.",
                )
            val isVideoOnly =
                isVideoOnly ?: throw IllegalStateException(
                    "The video stream has been not set as a video-only stream or as a video stream with embedded audio. " +
                        "Please specify this information with setIsVideoOnly.",
                )
            val resolution =
                resolution ?: throw IllegalStateException(
                    "The resolution of the video stream has been not set. Please specify it with setResolution (use an " +
                        "empty string if you are not able to get it).",
                )
            return VideoStream(id, content, isUrl, mediaFormat, deliveryMethod, resolution, isVideoOnly, manifestUrl, itagItem)
        }
    }

    companion object {
        const val RESOLUTION_UNKNOWN = ""
    }
}
