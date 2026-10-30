package io.github.aedev.flow.innertube

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * Every expected value is a token YouTube's own filter dialog shipped on 2026-09-14, read back out
 * of `header.searchHeaderRenderer.searchFilterButton…searchFilterOptionsDialogRenderer`. If one of
 * these fails, YouTube changed the wire format, not the builder.
 */
class YouTubeSearchParamsTest {
    private fun params(
        sortBy: YouTubeSearchParams.SortBy = YouTubeSearchParams.SortBy.RELEVANCE,
        contentType: YouTubeSearchParams.ContentType? = null,
        duration: YouTubeSearchParams.Duration? = null,
        uploadDate: YouTubeSearchParams.UploadDate? = null,
        features: Set<YouTubeSearchParams.Feature> = emptySet(),
    ) = YouTubeSearchParams.build(sortBy, contentType, duration, uploadDate, features)

    @Test
    fun anUnfilteredRelevanceSearchSendsNoParamsAtAll() {
        assertThat(params()).isNull()
    }

    @Test
    fun buildsTheTypeTokens() {
        assertThat(params(contentType = YouTubeSearchParams.ContentType.VIDEO)).isEqualTo("EgIQAQ%3D%3D")
        assertThat(params(contentType = YouTubeSearchParams.ContentType.SHORTS)).isEqualTo("EgIQCQ%3D%3D")
        assertThat(params(contentType = YouTubeSearchParams.ContentType.CHANNEL)).isEqualTo("EgIQAg%3D%3D")
        assertThat(params(contentType = YouTubeSearchParams.ContentType.PLAYLIST)).isEqualTo("EgIQAw%3D%3D")
        assertThat(params(contentType = YouTubeSearchParams.ContentType.MOVIE)).isEqualTo("EgIQBA%3D%3D")
    }

    @Test
    fun buildsTheDurationTokensYoutubeNowShips() {
        assertThat(params(duration = YouTubeSearchParams.Duration.UNDER_3_MINUTES)).isEqualTo("EgIYBA%3D%3D")
        assertThat(params(duration = YouTubeSearchParams.Duration.THREE_TO_20_MINUTES)).isEqualTo("EgIYBQ%3D%3D")
        assertThat(params(duration = YouTubeSearchParams.Duration.OVER_20_MINUTES)).isEqualTo("EgIYAg%3D%3D")
    }

    @Test
    fun buildsTheUploadDateTokens() {
        assertThat(params(uploadDate = YouTubeSearchParams.UploadDate.TODAY)).isEqualTo("EgIIAg%3D%3D")
        assertThat(params(uploadDate = YouTubeSearchParams.UploadDate.THIS_WEEK)).isEqualTo("EgIIAw%3D%3D")
        assertThat(params(uploadDate = YouTubeSearchParams.UploadDate.THIS_MONTH)).isEqualTo("EgIIBA%3D%3D")
        assertThat(params(uploadDate = YouTubeSearchParams.UploadDate.THIS_YEAR)).isEqualTo("EgIIBQ%3D%3D")
    }

    @Test
    fun buildsEveryFeatureToken() {
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.LIVE))).isEqualTo("EgJAAQ%3D%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.FOUR_K))).isEqualTo("EgJwAQ%3D%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.HD))).isEqualTo("EgIgAQ%3D%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.SUBTITLES))).isEqualTo("EgIoAQ%3D%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.CREATIVE_COMMONS))).isEqualTo("EgIwAQ%3D%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.THREE_SIXTY))).isEqualTo("EgJ4AQ%3D%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.VR180))).isEqualTo("EgPQAQE%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.THREE_D))).isEqualTo("EgI4AQ%3D%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.HDR))).isEqualTo("EgPIAQE%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.LOCATION))).isEqualTo("EgO4AQE%3D")
        assertThat(params(features = setOf(YouTubeSearchParams.Feature.PURCHASED))).isEqualTo("EgJIAQ%3D%3D")
    }

    @Test
    fun buildsThePopularitySortOnItsOwn() {
        assertThat(params(sortBy = YouTubeSearchParams.SortBy.VIEW_COUNT)).isEqualTo("CAM%3D")
    }

    @Test
    fun combinesASortWithFiltersNoSingleTokenCanExpress() {
        assertThat(
            params(
                sortBy = YouTubeSearchParams.SortBy.VIEW_COUNT,
                contentType = YouTubeSearchParams.ContentType.VIDEO,
                duration = YouTubeSearchParams.Duration.OVER_20_MINUTES,
                uploadDate = YouTubeSearchParams.UploadDate.THIS_YEAR,
            ),
        ).isEqualTo("CAMSBggFEAEYAg%3D%3D")
    }

    @Test
    fun ordersFeaturesByFieldNumberSoOneSelectionAlwaysEncodesTheSameWay() {
        val ordered =
            params(
                features =
                    setOf(
                        YouTubeSearchParams.Feature.FOUR_K,
                        YouTubeSearchParams.Feature.HD,
                        YouTubeSearchParams.Feature.SUBTITLES,
                    ),
            )
        val reversed =
            params(
                features =
                    setOf(
                        YouTubeSearchParams.Feature.SUBTITLES,
                        YouTubeSearchParams.Feature.HD,
                        YouTubeSearchParams.Feature.FOUR_K,
                    ),
            )

        assertThat(ordered).isEqualTo(reversed)
    }
}
