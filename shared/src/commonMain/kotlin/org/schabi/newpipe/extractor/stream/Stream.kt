/*
 * Ported from NewPipe Extractor v0.26.5: Stream.java, DeliveryMethod.java, AudioTrackType.java and
 * StreamType.java (org.schabi.newpipe.extractor.stream).
 * Copyright (C) 2016 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * NewPipe Extractor is free software under the GNU General Public License v3 or later.
 *
 * Upstream Flow builds these from InnerTube formats and reads them as properties; the Java getters
 * become Kotlin properties of the same names (`getContent()` is `content`).
 */

package org.schabi.newpipe.extractor.stream

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem

abstract class Stream(
    val id: String,
    val content: String,
    val isUrl: Boolean,
    val format: MediaFormat?,
    val deliveryMethod: DeliveryMethod,
    val manifestUrl: String?,
) {
    @Deprecated("Use content instead.")
    val url: String?
        get() = if (isUrl) content else null

    val formatId: Int
        get() = format?.id ?: FORMAT_ID_UNKNOWN

    open fun equalStats(other: Stream?): Boolean =
        other != null &&
            format != null &&
            other.format != null &&
            format.id == other.format.id &&
            deliveryMethod == other.deliveryMethod &&
            isUrl == other.isUrl

    abstract val itagItem: ItagItem?

    companion object {
        const val FORMAT_ID_UNKNOWN = -1
        const val ID_UNKNOWN = " "
        const val ITAG_NOT_AVAILABLE_OR_NOT_APPLICABLE = -1

        fun containSimilarStream(
            stream: Stream,
            streamList: List<Stream>?,
        ): Boolean = !streamList.isNullOrEmpty() && streamList.any { stream.equalStats(it) }
    }
}

enum class DeliveryMethod {
    PROGRESSIVE_HTTP,
    DASH,
    HLS,
    SS,
    TORRENT,
}

enum class AudioTrackType {
    ORIGINAL,
    DUBBED,
    DESCRIPTIVE,
    SECONDARY,
}

enum class StreamType {
    NONE,
    VIDEO_STREAM,
    AUDIO_STREAM,
    LIVE_STREAM,
    AUDIO_LIVE_STREAM,
    POST_LIVE_STREAM,
    POST_LIVE_AUDIO_STREAM,
}
