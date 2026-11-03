package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.innertube.pages.reel.ReelOverlay
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test

class ShortsMetadataRepositoryTest {
    private val fetches = AtomicInt(0)
    private var overlay: ReelOverlay? = ReelOverlay(title = "A reel", likeCount = 2_772L, commentCount = 62L)
    private val repository =
        ShortsMetadataRepository {
            fetches.incrementAndFetch()
            overlay
        }

    @Test
    fun aReelIsFetchedOncePerHalfHour() =
        runBlocking {
            assertEquals(2_772L, repository.overlayFor("reel1234567", nowMillis = 0L)?.likeCount)
            assertEquals(2_772L, repository.overlayFor("reel1234567", nowMillis = 29 * 60_000L)?.likeCount)
            assertEquals(1, fetches.load())

            repository.overlayFor("reel1234567", nowMillis = 31 * 60_000L)
            assertEquals(2, fetches.load())
        }

    @Test
    fun anUnreadableOverlayIsNotRemembered() =
        runBlocking {
            overlay = null
            assertNull(repository.overlayFor("reel1234567", nowMillis = 0L))
            assertNull(repository.overlayFor("reel1234567", nowMillis = 1L))
            assertEquals(2, fetches.load())
        }
}
