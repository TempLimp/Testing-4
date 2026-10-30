package io.github.aedev.flow.innertube.pages.explore

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.pages.ChartsPage
import kotlin.test.Test

class MusicVideoChartTest {
    private fun playlist(
        id: String,
        title: String,
    ) = PlaylistItem(
        id = id,
        title = title,
        author = null,
        songCountText = null,
        thumbnail = null,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    private fun page(vararg sections: ChartsPage.ChartSection) =
        ChartsPage(sections = sections.toList(), countryCode = null, continuation = null)

    private fun playlists(vararg items: PlaylistItem) =
        ChartsPage.ChartSection("Video charts", items.toList(), ChartsPage.ChartType.PLAYLISTS)

    private fun video(id: String) =
        Video(id = id, title = id, channelName = "", channelId = "", thumbnailUrl = "", duration = 0, viewCount = 0, uploadDate = "")

    @Test
    fun theTrendingChartIsPickedByItsIdWhateverItsLocalizedTitle() {
        val chart =
            page(
                playlists(
                    playlist("PL4fGSI1pDJn4yCNzulPkUbxgr4pl0gmI-", "Top 100 Live Performances - United States"),
                    playlist("OLAK5uy_nMa6example", "人気上昇中の曲 - 日本"),
                    playlist("PL4fGSI1pDJn61unMfmrUSz68RT8IFFnks", "Daily Top Music Videos - United States"),
                ),
            ).trendingVideoChart()

        assertThat(chart?.id).isEqualTo("OLAK5uy_nMa6example")
    }

    @Test
    fun theGlobalChartsHaveNoTrendingListSoTheFirstVideoChartStandsIn() {
        val chart =
            page(
                playlists(
                    playlist("PL4fGSI1pDJn6t3TXLGiiJdD-sZbrG3tG0", "Daily Top Music Videos - Global"),
                    playlist("PL4fGSI1pDJn5kI81J1fYWK5eZRl1zJ5kM", "Top 100 Music Videos Global"),
                ),
            ).trendingVideoChart()

        assertThat(chart?.id).isEqualTo("PL4fGSI1pDJn6t3TXLGiiJdD-sZbrG3tG0")
    }

    @Test
    fun sectionsThatAreNotPlaylistsAreIgnored() {
        val artists =
            ChartsPage.ChartSection(
                "Top artists",
                listOf(ArtistItem(id = "UC1", title = "Artist", thumbnail = null, shuffleEndpoint = null, radioEndpoint = null)),
                ChartsPage.ChartType.ARTISTS,
            )

        assertThat(page(artists).trendingVideoChart()).isNull()
    }

    @Test
    fun entriesAreRankedInPlaylistOrderAndDuplicatesDropped() {
        val ranked = listOf(video("a"), video("b"), video("a"), video("c")).ranked()

        assertThat(ranked.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(ranked.map { it.badges.first() }).containsExactly("#1", "#2", "#3").inOrder()
    }
}
