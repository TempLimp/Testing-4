package io.github.aedev.flow.player

import io.github.aedev.flow.data.model.Video

/** What the media session shows for a video. Media3's MediaMetadata built from it is S11's (Now Playing). */
internal data class VideoSessionMetadata(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
)

internal fun Video.toVideoSessionMetadata(): VideoSessionMetadata =
    VideoSessionMetadata(
        mediaId = id,
        title = title,
        artist = channelName,
        artworkUrl = thumbnailUrl,
    )
