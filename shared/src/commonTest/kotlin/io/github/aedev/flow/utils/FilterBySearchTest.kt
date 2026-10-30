package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import kotlin.test.Test

class FilterBySearchTest {
    private fun video(
        id: String,
        title: String,
        channel: String,
    ) = Video(
        id = id,
        title = title,
        channelName = channel,
        channelId = "",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0,
        uploadDate = "",
    )

    private val videos =
        listOf(
            video("1", "Café au lait recipe", "Kitchen"),
            video("2", "Lo-fi beats to study to", "Lofi Girl"),
            video("3", "Morning coffee routine", "Kitchen"),
        )

    @Test
    fun aBlankQueryKeepsTheWholePlaylist() {
        assertThat(videos.filterBySearch("  ") { "${it.title} ${it.channelName}" }).isEqualTo(videos)
    }

    @Test
    fun caseAndAccentsAreIgnored() {
        assertThat(videos.filterBySearch("CAFE") { "${it.title} ${it.channelName}" }.map { it.id }).containsExactly("1")
    }

    @Test
    fun theChannelNameMatchesToo() {
        assertThat(videos.filterBySearch("kitchen") { "${it.title} ${it.channelName}" }.map { it.id }).containsExactly("1", "3").inOrder()
    }

    @Test
    fun everyWordMustMatchSomewhere() {
        assertThat(videos.filterBySearch("kitchen coffee") { "${it.title} ${it.channelName}" }.map { it.id }).containsExactly("3")
        assertThat(videos.filterBySearch("kitchen beats") { "${it.title} ${it.channelName}" }).isEmpty()
    }
}
