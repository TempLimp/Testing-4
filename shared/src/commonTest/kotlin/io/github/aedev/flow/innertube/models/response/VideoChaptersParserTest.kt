package io.github.aedev.flow.innertube.models.response

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.requireFixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test

/**
 * The fixture is the chapters panel of a live `/next` response, trimmed to that one panel. The
 * video is the eight-chapter one the schema probe was run against.
 */
class VideoChaptersParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture() =
        json.parseToJsonElement(requireFixture("watch_next_chapters.json"))

    @Test
    fun everyChapterInThePanelIsRead() {
        val chapters = VideoChaptersParser.parse(fixture())

        assertThat(chapters).hasSize(8)
        assertThat(chapters.first().title).isEqualTo("Intro")
    }

    @Test
    fun theStartComesFromTheEndpointsSecondsNotTheDisplayedTime() {
        val chapters = VideoChaptersParser.parse(fixture())

        assertThat(chapters.first().startTimeSeconds).isEqualTo(0)
        assertThat(chapters[1].startTimeSeconds).isEqualTo(37)
        assertThat(chapters[1].title).isEqualTo("Pro - Cost per Gig")
    }

    @Test
    fun chaptersRunInOrderAndCarryAFrameEach() {
        val chapters = VideoChaptersParser.parse(fixture())

        assertThat(chapters.map { it.startTimeSeconds }).isInOrder()
        assertThat(chapters.all { it.thumbnailUrl?.isNotBlank() == true }).isTrue()
    }

    @Test
    fun aWatchResponseWithNoChapterPanelYieldsNothing() {
        val noPanel =
            json.parseToJsonElement(
                """{"engagementPanels":[{"engagementPanelSectionListRenderer":
                {"panelIdentifier":"engagement-panel-comments-section","content":{}}}]}""",
            )

        assertThat(VideoChaptersParser.parse(noPanel)).isEmpty()
    }

    @Test
    fun aMalformedPayloadYieldsNothingInsteadOfThrowing() {
        assertThat(VideoChaptersParser.parse(null)).isEmpty()
        assertThat(VideoChaptersParser.parse(JsonNull)).isEmpty()
        assertThat(VideoChaptersParser.parse(json.parseToJsonElement("""{"engagementPanels":"nope"}"""))).isEmpty()
        assertThat(VideoChaptersParser.parse(json.parseToJsonElement("""[1,2,3]"""))).isEmpty()
    }

    @Test
    fun anItemMissingItsTitleOrItsStartIsDroppedRatherThanFailingTheList() {
        val partial =
            json.parseToJsonElement(
                """
                {"engagementPanels":[{"engagementPanelSectionListRenderer":{
                "panelIdentifier":"engagement-panel-macro-markers-description-chapters",
                "content":{"macroMarkersListRenderer":{"contents":[
                {"macroMarkersListItemRenderer":{"title":{"simpleText":"Good"},
                 "onTap":{"watchEndpoint":{"startTimeSeconds":0}}}},
                {"macroMarkersListItemRenderer":{"onTap":{"watchEndpoint":{"startTimeSeconds":10}}}},
                {"macroMarkersListItemRenderer":{"title":{"simpleText":"No start"}}},
                {"macroMarkersListItemRenderer":{"title":{"simpleText":"Also good"},
                 "onTap":{"watchEndpoint":{"startTimeSeconds":20}}}}]}}}}]}
                """.trimIndent(),
            )

        val chapters = VideoChaptersParser.parse(partial)

        assertThat(chapters.map { it.title }).containsExactly("Good", "Also good").inOrder()
    }

    @Test
    fun aTitleGivenAsRunsIsJoined() {
        val runs =
            json.parseToJsonElement(
                """
                {"engagementPanels":[{"engagementPanelSectionListRenderer":{
                "panelIdentifier":"engagement-panel-macro-markers-description-chapters",
                "content":{"macroMarkersListRenderer":{"contents":[
                {"macroMarkersListItemRenderer":{"title":{"runs":[{"text":"Part "},{"text":"one"}]},
                 "onTap":{"watchEndpoint":{"startTimeSeconds":5}}}}]}}}}]}
                """.trimIndent(),
            )

        assertThat(VideoChaptersParser.parse(runs).single().title).isEqualTo("Part one")
    }
}
