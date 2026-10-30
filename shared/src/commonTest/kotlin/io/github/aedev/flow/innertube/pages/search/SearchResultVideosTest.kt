package io.github.aedev.flow.innertube.pages.search

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.testing.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.time.Clock

/** The Home discovery lane ranks these videos, so every field its filters and the engine read is pinned here. */
class SearchResultVideosTest {
    private fun byline(
        name: String,
        channelId: String,
    ) = """{"runs":[{"text":"$name","navigationEndpoint":{"browseEndpoint":{"browseId":"$channelId"}}}]}"""

    private fun avatar(name: String) =
        """{"channelThumbnailWithLinkRenderer":{"thumbnail":{"thumbnails":[{"url":"https://yt3.ggpht.com/$name=s68","width":68,"height":68}]}}}"""

    private fun collaborator(
        name: String,
        channelId: String,
    ) = """
        {"listItemViewModel":{"title":{"content":"$name"},
          "leadingAccessory":{"avatarViewModel":{"image":{"sources":[{"url":"https://yt3.ggpht.com/$name=s68"}]}}},
          "rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"$channelId"}}}}}}}
        """.trimIndent()

    private val collabByline =
        """
        {"runs":[{"text":"Alpha and Beta","navigationEndpoint":{"showDialogCommand":{"panelLoadingStrategy":{"inlineContent":
          {"dialogViewModel":{"customContent":{"listViewModel":{"listItems":[
            ${collaborator("Alpha", "UCalpha")},${collaborator("Beta", "UCbeta")}
          ]}}}}}}}}]}
        """.trimIndent()

    private val page =
        """
        {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[
          {"itemSectionRenderer":{"contents":[
            {"videoRenderer":{"videoId":"plain","title":{"runs":[{"text":"Plain upload"}]},
              "ownerText":${byline("Solo", "UCsolo")},"channelThumbnailSupportedRenderers":${avatar("Solo")},
              "lengthText":{"simpleText":"12:34"},"viewCountText":{"simpleText":"1,234,567 views"},
              "publishedTimeText":{"simpleText":"3 days ago"}}},
            {"gridShelfViewModel":{"header":{"sectionHeaderViewModel":{"headline":{"content":"Shorts"}}},"contents":[
              {"shortsLockupViewModel":{"onTap":{"innertubeCommand":{"reelWatchEndpoint":{"videoId":"stripShort"}}},
                "overlayMetadata":{"primaryText":{"content":"Strip short"}}}}]}},
            {"videoRenderer":{"videoId":"collab","title":{"runs":[{"text":"Together"}]},
              "ownerText":$collabByline,"lengthText":{"simpleText":"8:00"},
              "viewCountText":{"simpleText":"10 views"},"publishedTimeText":{"simpleText":"2 weeks ago"}}},
            {"videoRenderer":{"videoId":"live","title":{"runs":[{"text":"On air"}]},
              "ownerText":${byline("Station", "UCstation")},
              "viewCountText":{"runs":[{"text":"1,024"},{"text":" watching"}]},
              "badges":[{"metadataBadgeRenderer":{"style":"BADGE_STYLE_TYPE_LIVE_NOW","label":"LIVE"}}]}},
            {"videoRenderer":{"videoId":"soon","title":{"runs":[{"text":"Premiere"}]},
              "ownerText":${byline("Studio", "UCstudio")},
              "upcomingEventData":{"startTime":"4102444800"},"viewCountText":{"simpleText":"12 waiting"}}},
            {"videoRenderer":{"videoId":"reel","title":{"runs":[{"text":"Quick one"}]},
              "ownerText":${byline("Solo", "UCsolo")},"lengthText":{"simpleText":"0:45"},
              "publishedTimeText":{"simpleText":"5 hours ago"},
              "navigationEndpoint":{"reelWatchEndpoint":{"videoId":"reel"}}}},
            {"videoRenderer":{"videoId":"overlayReel","title":{"runs":[{"text":"Marked by overlay"}]},
              "ownerText":${byline("Solo", "UCsolo")},
              "thumbnailOverlays":[{"thumbnailOverlayTimeStatusRenderer":{"style":"SHORTS"}}]}}
          ]}},
          {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-page"}}}}
        ]}}}}}
        """.trimIndent()

    private val results: SearchResultsPage =
        Json.parseToJsonElement(page).jsonObject.toSearchResultsPage()

    private fun video(id: String): Video = results.resultVideos().single { it.id == id }

    @Test
    fun keepsTheRankedResultsInOrderAndLeavesTheStripsOut() {
        assertThat(results.resultVideos().map { it.id })
            .containsExactly("plain", "collab", "live", "soon", "reel", "overlayReel")
            .inOrder()
        assertThat(results.continuation).isEqualTo("next-page")
    }

    @Test
    fun aPlainUploadCarriesItsChannelAvatarDurationViewsAndAParsedDate() {
        val video = video("plain")
        val now = Clock.System.now().toEpochMilliseconds()

        assertThat(video.title).isEqualTo("Plain upload")
        assertThat(video.channelName).isEqualTo("Solo")
        assertThat(video.channelId).isEqualTo("UCsolo")
        assertThat(video.channelThumbnailUrl).contains("yt3.ggpht.com/Solo")
        assertThat(video.duration).isEqualTo(754)
        assertThat(video.viewCount).isEqualTo(1_234_567L)
        assertThat(video.uploadDate).isEqualTo("3 days ago")
        assertThat(now - video.timestamp).isIn(2 * DAY_MS..4 * DAY_MS)
        assertThat(video.isShort || video.isLive || video.isUpcoming).isFalse()
    }

    @Test
    fun aCollaborationTakesTheLeadCollaboratorsChannelIdAndEveryAvatar() {
        val video = video("collab")

        assertThat(video.channelId).isEqualTo("UCalpha")
        assertThat(video.collaborators.map { it.channelId }).containsExactly("UCalpha", "UCbeta").inOrder()
        assertThat(video.channelThumbnailUrls).hasSize(2)
        assertThat(video.timestamp).isGreaterThan(0L)
    }

    @Test
    fun aLiveRowIsLiveWithNoDuration() {
        val video = video("live")

        assertThat(video.isLive).isTrue()
        assertThat(video.duration).isEqualTo(0)
        assertThat(video.channelId).isEqualTo("UCstation")
    }

    @Test
    fun anUpcomingRowIsDatedByItsStartAndHasNoViews() {
        val video = video("soon")

        assertThat(video.isUpcoming).isTrue()
        assertThat(video.isLive).isFalse()
        assertThat(video.timestamp).isEqualTo(4_102_444_800_000L)
        assertThat(video.viewCount).isEqualTo(0L)
    }

    @Test
    fun aShortServedAsAVideoRowComesBackAsAShortWithItsChannel() {
        val reel = video("reel")

        assertThat(reel.isShort).isTrue()
        assertThat(reel.channelId).isEqualTo("UCsolo")
        assertThat(reel.timestamp).isGreaterThan(0L)
        assertThat(video("overlayReel").isShort).isTrue()
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
