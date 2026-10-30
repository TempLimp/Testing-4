package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import kotlin.test.Test

class OriginalTextPolicyTest {
    private val original = video(title = "XIAOMI 17T - ПРОСТОЙ, ПРИЯТНЫЙ ФЛАГМАН!", description = "Обзор смартфона")
    private val translated =
        original.copy(
            title = "XIAOMI 17T - A SIMPLE, PLEASANT FLAGSHIP!",
            description = "Smartphone review",
            viewCount = 9,
        )

    @Test
    fun offTheTranslatedTitleAListShowedStays() {
        assertThat(OriginalTextPolicy.streamTitle(original.title, translated.title, preferOriginal = false)).isEqualTo(translated.title)
    }

    @Test
    fun onThePlayersOriginalTitleWins() {
        assertThat(OriginalTextPolicy.streamTitle(original.title, translated.title, preferOriginal = true)).isEqualTo(original.title)
    }

    @Test
    fun eitherWayABlankTitleFallsBackToTheOther() {
        assertThat(OriginalTextPolicy.streamTitle(" ", translated.title, preferOriginal = true)).isEqualTo(translated.title)
        assertThat(OriginalTextPolicy.streamTitle(original.title, "", preferOriginal = false)).isEqualTo(original.title)
        assertThat(OriginalTextPolicy.streamTitle(null, null, preferOriginal = false)).isNull()
    }

    @Test
    fun thePlayersDescriptionReplacesTheKnownOneOnlyWhenWanted() {
        assertThat(OriginalTextPolicy.streamDescription("Обзор", "", preferOriginal = true)).isEqualTo("Обзор")
        assertThat(OriginalTextPolicy.streamDescription("Обзор", "", preferOriginal = false)).isEmpty()
        assertThat(OriginalTextPolicy.streamDescription("  ", "known", preferOriginal = true)).isEqualTo("known")
    }

    @Test
    fun offTheWatchPagesTranslationIsAppliedWhole() {
        assertThat(OriginalTextPolicy.keepOriginalText(translated, original, preferOriginal = false)).isEqualTo(translated)
    }

    @Test
    fun onTheWatchPageAddsItsCountsButKeepsTheOriginalText1230() {
        val kept = OriginalTextPolicy.keepOriginalText(translated, original, preferOriginal = true)

        assertThat(kept.title).isEqualTo(original.title)
        assertThat(kept.description).isEqualTo(original.description)
        assertThat(kept.viewCount).isEqualTo(9)
    }

    private fun video(
        title: String,
        description: String,
    ) = Video(
        id = "2BAEyzqxAME",
        title = title,
        channelName = "DimaViper",
        channelId = "UCs2DTZsK_6t6JD-m3yTapxg",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0,
        uploadDate = "",
        description = description,
    )
}
