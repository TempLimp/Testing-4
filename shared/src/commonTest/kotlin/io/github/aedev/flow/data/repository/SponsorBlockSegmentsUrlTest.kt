package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockCategories
import io.github.aedev.flow.testing.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class SponsorBlockSegmentsUrlTest {
    private val url = SponsorBlockRepository.segmentsUrl("dQw4w9WgXcQ")

    private fun jsonList(parameter: String): List<String> =
        Json
            .parseToJsonElement(requireNotNull(url.parameters[parameter]))
            .jsonArray
            .map { it.jsonPrimitive.content }

    @Test
    fun theLookupAsksForEveryCategoryTheSettingsOffer() {
        assertThat(jsonList("categories")).containsExactlyElementsIn(SponsorBlockCategories.all)
    }

    @Test
    fun fillerAndPreviewAreFetched() {
        assertThat(jsonList("categories")).containsAtLeast("filler", "preview")
    }

    @Test
    fun highlightsAndChaptersAreNotFetched() {
        assertThat(jsonList("categories")).containsNoneOf("poi_highlight", "chapter")
    }

    @Test
    fun theLookupAsksForWholeVideoLabelsAlongsideSkipAndMuteRanges() {
        assertThat(jsonList("actionTypes")).containsExactly("skip", "mute", "full")
    }

    @Test
    fun theVideoIdIsSentAsIs() {
        assertThat(url.parameters["videoID"]).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun wholeVideoCategoriesCannotBeSubmittedAsARange() {
        assertThat(SponsorBlockCategories.submittable).doesNotContain(SponsorBlockCategories.EXCLUSIVE_ACCESS)
        assertThat(SponsorBlockCategories.submittable).contains(SponsorBlockCategories.FILLER)
    }

    @Test
    fun onlyFillerAndPreviewDefaultToShowingWithoutSkipping() {
        val ignoredByDefault = SponsorBlockCategories.all.filter { SponsorBlockCategories.defaultAction(it) == SponsorBlockAction.IGNORE }

        assertThat(ignoredByDefault).containsExactly("filler", "preview")
    }
}
