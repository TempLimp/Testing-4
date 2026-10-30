package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SectionListRenderer
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.pages.InnerTubeJson.ALBUM
import io.github.aedev.flow.innertube.pages.InnerTubeJson.PLAYLIST
import io.github.aedev.flow.innertube.pages.InnerTubeJson.UNKNOWN
import io.github.aedev.flow.innertube.pages.InnerTubeJson.bareBrowseRow
import io.github.aedev.flow.innertube.pages.InnerTubeJson.bareSongRow
import io.github.aedev.flow.innertube.pages.InnerTubeJson.itemSection
import io.github.aedev.flow.innertube.pages.InnerTubeJson.musicShelf
import io.github.aedev.flow.innertube.pages.InnerTubeJson.run
import io.github.aedev.flow.innertube.pages.InnerTubeJson.separator
import kotlinx.serialization.json.Json
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

/**
 * YouTube Music sometimes answers search with rows that leave out `playlistItemData`, the music
 * video type and the play overlay, and types artists and profiles alike as UNKNOWN (#1379).
 */
class SearchReducedLayoutTest {
    private val song = bareSongRow("NIma5XOxBq0", "Softcore", listOf(run("Song"), separator(), run("The Neighbourhood")))
    private val artist =
        bareBrowseRow(
            "UCWJOI5Z5hO1v9mI4CsRlyBg",
            UNKNOWN,
            "The Neighbourhood",
            listOf(run("Artist"), separator(), run("5.99M subscribers")),
            playlistId = "RDAOZ0cOGJpu9D",
        )
    private val profile = bareBrowseRow("UCyWJwbVZGJEI_Lu", UNKNOWN, "Softcore", listOf(run("Profile")))
    private val album =
        bareBrowseRow(
            "MPREb_WFTQ0WDl1n",
            ALBUM,
            "Softcore",
            listOf(run("Album"), separator(), run("AtomTM"), separator(), run("1994")),
            playlistId = "OLAK5uy_m9_cnU",
        )
    private val playlist =
        bareBrowseRow(
            "VLPLX4j5WdRpmtSM",
            PLAYLIST,
            "Softcore",
            listOf(run("Playlist"), separator(), run("Olivia"), separator(), run("92 songs")),
            playlistId = "PLX4j5WdRpmtSM",
            withMix = false,
        )

    @Test
    fun theReducedSummaryKeepsSongsArtistsAlbumsAndPlaylistsAndLeavesOutProfiles() {
        val page =
            SearchSummaryPage.fromSearchResponse(
                InnerTubeJson.search(listOf(song, artist, profile, album, playlist).map(::itemSection)),
            )
        val items = page.summaries.flatMap { it.items }

        assertEquals(listOf("NIma5XOxBq0"), items.filterIsInstance<SongItem>().map { it.id })
        assertEquals(
            "The Neighbourhood",
            items
                .filterIsInstance<SongItem>()
                .single()
                .artists
                .single()
                .name,
        )
        assertEquals(listOf("UCWJOI5Z5hO1v9mI4CsRlyBg"), items.filterIsInstance<ArtistItem>().map { it.id })
        assertEquals("OLAK5uy_m9_cnU", items.filterIsInstance<AlbumItem>().single().playlistId)
        val kept = items.filterIsInstance<PlaylistItem>().single()
        assertEquals("PLX4j5WdRpmtSM", kept.id)
        assertEquals("PLX4j5WdRpmtSM", kept.playEndpoint?.playlistId)
        assertNull(kept.radioEndpoint)
        assertTrue(items.none { it.id == "UCyWJwbVZGJEI_Lu" })
    }

    @Test
    fun theSongsFilterKeepsBareRows() {
        val shelf = InnerTubeJson.search(listOf(musicShelf("Songs", listOf(song))))
        val rows =
            shelf.contents
                ?.tabbedSearchResultsRenderer
                ?.tabs
                ?.single()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.single()
                ?.musicShelfRenderer
                ?.contents
                .orEmpty()
                .mapNotNull { it.musicResponsiveListItemRenderer }

        assertEquals(listOf("NIma5XOxBq0"), rows.mapNotNull(SearchPage::toYTItem).map { it.id })
    }

    @Test
    fun playlistItemDataStillWinsWhenItIsThere() {
        val withItemData =
            song.replaceFirst(
                "{\"musicResponsiveListItemRenderer\":{",
                "{\"musicResponsiveListItemRenderer\":{\"playlistItemData\":{\"videoId\":\"fromItemData\"},",
            )
        val page = SearchSummaryPage.fromSearchResponse(InnerTubeJson.search(listOf(itemSection(withItemData))))

        assertEquals(
            "fromItemData",
            page.summaries
                .single()
                .items
                .single()
                .id,
        )
    }

    private fun filtered(row: String) = SearchPage.toYTItem(InnerTubeJson.decodeRow(row))

    @Test
    fun anEpisodePlaysLikeASongWithItsShowAsTheArtist() {
        val item = filtered(InnerTubeJson.episodeRow("sJ097VJSZK4", "WTF episode", "Nov 20, 2020", "WTF")) as SongItem

        assertEquals("sJ097VJSZK4", item.id)
        assertEquals(listOf("WTF"), item.artists.map { it.name })
        assertEquals("MUSIC_VIDEO_TYPE_PODCAST_EPISODE", item.musicVideoType)
    }

    @Test
    fun aPodcastOpensAsThePlaylistOfItsEpisodes() {
        val item = filtered(InnerTubeJson.podcastRow("MPSPPLE9uSpEn0Z8", "PLE9uSpEn0Z8", "Softcore Chaos", "Ella")) as PlaylistItem

        assertEquals("PLE9uSpEn0Z8", item.id)
        assertEquals("Ella", item.author?.name)
        assertEquals("PLE9uSpEn0Z8", item.playEndpoint?.playlistId)
    }

    @Test
    fun aProfileIsKeptAsAnArtistWithoutPlayActions() {
        val item =
            filtered(
                bareBrowseRow("UC9Qgl4YBSc_jg81", InnerTubeJson.USER, "Softcore", listOf(run("Profile"), separator(), run("@softcore"))),
            ) as ArtistItem

        assertEquals("UC9Qgl4YBSc_jg81", item.id)
        assertTrue(item.isProfile)
        assertNull(item.shuffleEndpoint)
    }

    @Test
    fun theSummaryKeepsTheChipParamsYouTubeOffers() {
        val response =
            InnerTubeJson.searchWithChips(
                listOf(itemSection(song)),
                chipParams = listOf("EgWKAQIIAWoQ", "EgWKAQJQAWoQ"),
            )

        assertEquals(listOf("EgWKAQIIAWoQ", "EgWKAQJQAWoQ"), SearchSummaryPage.fromSearchResponse(response).filterParams)
    }

    @Test
    fun aChipBarWithoutIsSelectedStillParsesAsOnAPodcastPage() {
        val header =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }.decodeFromString(
                SectionListRenderer.Header.serializer(),
                """{"chipCloudRenderer":{"chips":[{"chipCloudChipRenderer":{"text":{"runs":[{"text":"All"}]},"navigationEndpoint":{}}}]}}""",
            )

        assertFalse(
            header.chipCloudRenderer
                ?.chips
                ?.single()
                ?.chipCloudChipRenderer
                ?.isSelected ?: true,
        )
    }
}
