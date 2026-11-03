package org.schabi.newpipe.extractor.channel

import org.schabi.newpipe.extractor.Image

/**
 * The fields of NewPipe's `ChannelInfo` that upstream reads, under NewPipe's package so its call sites stay as
 * they are. NewPipe's extractor does not run on iOS: `YouTubeRepository.getChannelInfo` fills this from the
 * InnerTube channel browse NewPipe itself parses (header, `channelMetadataRenderer`, `microformatDataRenderer`).
 */
class ChannelInfo(
    val id: String?,
    val name: String?,
    val avatars: List<Image>,
    val description: String?,
    val tags: List<String>?,
)
