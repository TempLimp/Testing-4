package io.github.aedev.flow.data.scrobble

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.recommendation.music.FavouriteArtist
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistCatalog
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class TasteImportTest {
    private val scrobbler: Scrobbler = mockk()
    private val catalog: FavouriteArtistCatalog = mockk()
    private val favourites: FavouriteArtistsStore = mockk(relaxed = true)
    private val import = TasteImport(scrobbler, catalog, favourites)

    @BeforeTest
    fun setUp() {
        mockkObject(PerformanceDispatcher)
        every { PerformanceDispatcher.networkIO } returns Dispatchers.Unconfined
    }

    @AfterTest
    fun tearDown() = unmockkAll()

    @Test
    fun onlyExactNameMatchesAreAddedAndExistingPicksAreNotCountedAgain() =
        runBlocking {
            coEvery { scrobbler.topArtists(ScrobbleService.LASTFM, any()) } returns
                Result.success(listOf("Beyoncé", "Unknown Band", "Picked"))
            coEvery { catalog.search("Beyoncé") } returns listOf(FavouriteArtist("UCb", "BEYONCE"))
            coEvery { catalog.search("Unknown Band") } returns listOf(FavouriteArtist("UCx", "Unknown Band Tribute"))
            coEvery { catalog.search("Picked") } returns listOf(FavouriteArtist("UCp", "Picked"))
            coEvery { favourites.currentIds() } returns setOf("UCp")

            val added = import.importTopArtists(ScrobbleService.LASTFM).getOrThrow()

            assertThat(added).isEqualTo(1)
            coVerify(exactly = 1) { favourites.setFavourite(FavouriteArtist("UCb", "BEYONCE"), true) }
            coVerify(exactly = 0) { favourites.setFavourite(FavouriteArtist("UCp", "Picked"), any()) }
        }

    @Test
    fun aFailedReadReportsTheFailure() =
        runBlocking {
            coEvery { scrobbler.topArtists(any(), any()) } returns Result.failure(IllegalStateException())

            assertThat(import.importTopArtists(ScrobbleService.LISTENBRAINZ).isFailure).isTrue()
        }

    @Test
    fun namesMatchRegardlessOfCaseAccentsAndSpacing() {
        assertThat("  Sigur  Rós ".matchKey()).isEqualTo("sigur ros")
    }
}
