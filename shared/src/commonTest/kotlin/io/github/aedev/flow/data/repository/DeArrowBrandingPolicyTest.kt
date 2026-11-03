package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.DeArrowContent
import io.github.aedev.flow.data.model.DeArrowThumbnail
import io.github.aedev.flow.data.model.DeArrowTitle
import io.github.aedev.flow.testing.assertThat
import io.ktor.http.Url
import kotlin.test.Test

class DeArrowBrandingPolicyTest {
    private fun resolve(
        content: DeArrowContent,
        duration: Int = 600,
        useRandomFrame: Boolean = true,
        live: Boolean = false,
    ) = DeArrowBrandingPolicy.resolve(content, "abc123def45", duration, useRandomFrame, live)

    private fun frameTime(url: String?): Double? = url?.let { Url(it).parameters["time"] }?.toDouble()

    @Test
    fun aVideoNobodySubmittedForShowsTheFrameAtTheRandomTime() {
        val result = resolve(DeArrowContent(randomTime = 0.25, videoDuration = 400.0))

        assertThat(result?.title).isNull()
        assertThat(frameTime(result?.thumbnailUrl)).isEqualTo(100.0)
        assertThat(result?.thumbnailUrl?.let { Url(it).parameters["videoID"] }).isEqualTo("abc123def45")
    }

    @Test
    fun aMissingServerDurationFallsBackToTheCardS() {
        val result = resolve(DeArrowContent(randomTime = 0.5, videoDuration = null), duration = 300)

        assertThat(frameTime(result?.thumbnailUrl)).isEqualTo(150.0)
    }

    @Test
    fun noDurationAnywhereMeansNoFrame() {
        assertThat(resolve(DeArrowContent(randomTime = 0.5), duration = 0)).isNull()
    }

    @Test
    fun theRandomFrameCanBeSwitchedOff() {
        assertThat(resolve(DeArrowContent(randomTime = 0.5, videoDuration = 100.0), useRandomFrame = false)).isNull()
    }

    @Test
    fun liveAndUpcomingVideosGetNoRandomFrame() {
        assertThat(resolve(DeArrowContent(randomTime = 0.5, videoDuration = 100.0), live = true)).isNull()
    }

    @Test
    fun theServerSFirstSubmissionWins() {
        val content =
            DeArrowContent(
                titles = listOf(DeArrowTitle("Locked title", votes = 1, locked = true), DeArrowTitle("Popular title", votes = 9)),
                thumbnails =
                    listOf(
                        DeArrowThumbnail(timestamp = 3.5, votes = 2, locked = true),
                        DeArrowThumbnail(timestamp = 80.0, votes = 7),
                    ),
                randomTime = 0.5,
                videoDuration = 100.0,
            )

        val result = resolve(content)

        assertThat(result?.title).isEqualTo("Locked title")
        assertThat(frameTime(result?.thumbnailUrl)).isEqualTo(3.5)
    }

    @Test
    fun aWinningOriginalKeepsYoutubeSTitleAndThumbnail() {
        val content =
            DeArrowContent(
                titles = listOf(DeArrowTitle("Original", votes = 5, original = true), DeArrowTitle("Other", votes = 1)),
                thumbnails = listOf(DeArrowThumbnail(votes = 5, original = true), DeArrowThumbnail(timestamp = 10.0, votes = 1)),
                randomTime = 0.5,
                videoDuration = 100.0,
            )

        assertThat(resolve(content)).isNull()
    }

    @Test
    fun aDownvotedUnlockedSubmissionIsSkipped() {
        val content =
            DeArrowContent(
                titles = listOf(DeArrowTitle("Bad", votes = -2), DeArrowTitle("Good", votes = 0)),
                thumbnails = listOf(DeArrowThumbnail(timestamp = 5.0, votes = -1)),
                randomTime = 0.1,
                videoDuration = 100.0,
            )

        val result = resolve(content)

        assertThat(result?.title).isEqualTo("Good")
        assertThat(frameTime(result?.thumbnailUrl)).isWithin(1e-9).of(10.0)
    }

    @Test
    fun caseMarkersAreStrippedFromASubmittedTitle() {
        val result = resolve(DeArrowContent(titles = listOf(DeArrowTitle(">iPhone review", votes = 1))), useRandomFrame = false)

        assertThat(result?.title).isEqualTo("iPhone review")
        assertThat(result?.thumbnailUrl).isNull()
    }
}
