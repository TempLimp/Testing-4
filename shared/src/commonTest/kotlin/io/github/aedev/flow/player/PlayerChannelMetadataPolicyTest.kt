package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class PlayerChannelMetadataPolicyTest {
    @Test
    fun fetchedAvatarReplacesEmbeddedFallback() {
        val avatar = PlayerChannelMetadataPolicy.selectAvatarUrl(
            fetchedAvatarUrl = "fetched",
            embeddedAvatarUrl = "embedded",
            currentAvatarUrl = "current"
        )

        assertThat(avatar).isEqualTo("fetched")
    }

    @Test
    fun emptyFetchNeverClearsExistingAvatar() {
        val avatar = PlayerChannelMetadataPolicy.selectAvatarUrl(
            fetchedAvatarUrl = "",
            embeddedAvatarUrl = null,
            currentAvatarUrl = "current"
        )

        assertThat(avatar).isEqualTo("current")
    }

    @Test
    fun channelReferencesPreferUploaderURLAndRemoveDuplicates() {
        val references = PlayerChannelMetadataPolicy.channelReferences(
            uploaderUrl = " https://youtube.com/channel/UC123 ",
            channelId = "https://youtube.com/channel/UC123"
        )

        assertThat(references).containsExactly("https://youtube.com/channel/UC123")
    }
}
