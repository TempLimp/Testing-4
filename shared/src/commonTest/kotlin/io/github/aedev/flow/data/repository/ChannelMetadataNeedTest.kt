package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class ChannelMetadataNeedTest {
    private fun video(
        channelId: String = "UCkVfrGwV-iG9bSsgCbrNPxQ",
        avatar: String = "https://yt3.ggpht.com/avatar=s176",
    ) = Video(
        id = "v",
        title = "t",
        channelName = "Better Stack",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 60,
        viewCount = 1,
        uploadDate = "",
        channelThumbnailUrl = avatar,
    )

    @Test
    fun aRealChannelWithARealAvatarNeedsNothing() {
        assertThat(video().needsChannelMetadata()).isFalse()
    }

    @Test
    fun aChannelPageSavedAsTheAvatarCountsAsMissing() {
        assertThat(video(avatar = "https://www.youtube.com/channel/UCkVfrGwV-iG9bSsgCbrNPxQ").needsChannelMetadata()).isTrue()
    }

    @Test
    fun aBlankAvatarOrAPlaceholderChannelCountsAsMissing() {
        assertThat(video(avatar = "").needsChannelMetadata()).isTrue()
        assertThat(video(channelId = "local").needsChannelMetadata()).isTrue()
    }
}
