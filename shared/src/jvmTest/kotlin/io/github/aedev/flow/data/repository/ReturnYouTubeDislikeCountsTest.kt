package io.github.aedev.flow.data.repository

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.ReturnYouTubeDislikeResponse
import io.github.aedev.flow.testing.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Pins the "one fetch per cause" contract on the Return YouTube Dislike lookup: the player asks for
 * it from the stream load and again from the live metadata refresh, and the second ask must join
 * the first rather than open a second request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnYouTubeDislikeCountsTest {
    private val repository =
        YouTubeRepository(
            playerPreferences = mockk(relaxed = true),
        )

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun aSecondCallForTheSameIdWhileOneIsInFlightJoinsIt() =
        runTest {
            val gate = CompletableDeferred<ReturnYouTubeDislikeResponse>()
            mockkObject(YouTube)
            coEvery { YouTube.returnYouTubeDislike("vid_a") } coAnswers { Result.success(gate.await()) }

            val first = async { repository.returnYouTubeDislikeCounts("vid_a") }
            val second = async { repository.returnYouTubeDislikeCounts("vid_a") }
            runCurrent()
            gate.complete(ReturnYouTubeDislikeResponse(likes = 12, dislikes = 34))

            assertThat(first.await()).isEqualTo(
                YouTubeRepository.ReturnYouTubeDislikeCounts(likes = 12L, dislikes = 34L),
            )
            assertThat(second.await()).isEqualTo(first.await())
            coVerify(exactly = 1) { YouTube.returnYouTubeDislike("vid_a") }
        }

    @Test
    fun aFailedLookupAndAMissingCountBothReadAsNull() =
        runTest {
            mockkObject(YouTube)
            coEvery { YouTube.returnYouTubeDislike("vid_fail") } returns Result.failure(IllegalStateException("offline"))
            coEvery { YouTube.returnYouTubeDislike("vid_partial") } returns
                Result.success(ReturnYouTubeDislikeResponse(likes = -1, dislikes = null))

            assertThat(repository.returnYouTubeDislikeCounts("vid_fail")).isNull()
            assertThat(repository.returnYouTubeDislikeCounts("vid_partial")).isEqualTo(
                YouTubeRepository.ReturnYouTubeDislikeCounts(likes = null, dislikes = null),
            )
        }
}
