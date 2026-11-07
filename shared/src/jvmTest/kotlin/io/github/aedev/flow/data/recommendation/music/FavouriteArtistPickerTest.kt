package io.github.aedev.flow.data.recommendation.music

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavouriteArtistPickerTest {
    private val dispatcher = StandardTestDispatcher()
    private val store: FavouriteArtistsStore = mockk(relaxed = true)
    private val catalog: FavouriteArtistCatalog = mockk()
    private val chart = FavouriteArtist("UCchart", "Chart Star")
    private val found = FavouriteArtist("UCfound", "Found Band")

    @BeforeTest
    fun setUp() {
        mockkObject(PerformanceDispatcher)
        every { PerformanceDispatcher.networkIO } returns dispatcher
        every { store.artists } returns flowOf(emptyList())
        coEvery { catalog.popular() } returns listOf(chart)
        coEvery { catalog.search("found") } returns listOf(found)
    }

    @AfterTest
    fun tearDown() = unmockkAll()

    @Test
    fun theChartShowsUntilSomethingIsTypedThenTheSearchResults() =
        runTest(dispatcher) {
            val scope = TestScope(dispatcher)
            val picker = FavouriteArtistPicker(scope, store, catalog)
            val collector = scope.launch { picker.state.collect {} }
            advanceUntilIdle()
            assertThat(picker.state.value.candidates).containsExactly(chart)

            picker.onQueryChange("found")
            advanceTimeBy(1_000)
            advanceUntilIdle()
            assertThat(picker.state.first { it.candidates == listOf(found) }.searching).isTrue()
            collector.cancel()
        }

    @Test
    fun pickingAnArtistSavesIt() =
        runTest(dispatcher) {
            val picker = FavouriteArtistPicker(TestScope(dispatcher), store, catalog)

            picker.setFavourite(chart, favourite = true)
            advanceUntilIdle()

            coVerify { store.setFavourite(chart, true) }
        }
}
