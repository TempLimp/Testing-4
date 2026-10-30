package io.github.aedev.flow.innertube.pages.channel

import kotlinx.serialization.json.Json
import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

/**
 * The tab list is the whole adaptivity contract: the screen renders one tab per descriptor, so a tab
 * dropped here disappears from the app and a tab invented here renders empty.
 */
class ChannelTabsParserTest {
    private fun tabs(vararg entries: String) =
        Json
            .parseToJsonElement(
                """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[${entries.joinToString(",")}]}}}""",
            ).toChannelTabs()

    private fun tab(
        title: String,
        url: String?,
        params: String? = null,
        selected: Boolean = false,
    ): String {
        val endpoint =
            url
                ?.let {
                    val browse = params?.let { p -> ""","browseEndpoint":{"browseId":"UCx","params":"$p"}""" }.orEmpty()
                    ""","endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"$it"}}$browse}"""
                }.orEmpty()
        return """{"tabRenderer":{"title":"$title","selected":$selected$endpoint}}"""
    }

    @Test
    fun readsAllNineTabsOfALargeChannelInResponseOrder() {
        val resolved =
            tabs(
                tab("Home", "/@LinusTechTips", "EghmZWF0dXJlZPIGBAoCMgA%3D", selected = true),
                tab("Videos", "/@LinusTechTips/videos", "EgZ2aWRlb3PyBgQKAjoA"),
                tab("Shorts", "/@LinusTechTips/shorts"),
                tab("Live", "/@LinusTechTips/streams"),
                tab("Shows", "/@LinusTechTips/shows"),
                tab("Podcasts", "/@LinusTechTips/podcasts"),
                tab("Playlists", "/@LinusTechTips/playlists"),
                tab("Posts", "/@LinusTechTips/community"),
                tab("Store", "/@LinusTechTips/store"),
            )

        assertEquals(
            listOf(
                ChannelTabKind.Home,
                ChannelTabKind.Videos,
                ChannelTabKind.Shorts,
                ChannelTabKind.Live,
                ChannelTabKind.Shows,
                ChannelTabKind.Podcasts,
                ChannelTabKind.Playlists,
                ChannelTabKind.Posts,
                ChannelTabKind.Store,
            ),
            resolved.map { it.kind },
        )
        assertTrue(resolved.first().selected)
    }

    @Test
    fun aSmallerChannelYieldsOnlyTheTabsItActuallyHas() {
        val resolved =
            tabs(
                tab("Home", "/@Hyperplexed"),
                tab("Videos", "/@Hyperplexed/videos"),
                tab("Shorts", "/@Hyperplexed/shorts"),
                tab("Playlists", "/@Hyperplexed/playlists"),
                tab("Posts", "/@Hyperplexed/community"),
            )

        assertEquals(5, resolved.size)
        assertTrue(resolved.none { it.kind == ChannelTabKind.Live })
        assertTrue(resolved.none { it.kind == ChannelTabKind.Podcasts })
        assertTrue(resolved.none { it.kind == ChannelTabKind.Store })
    }

    @Test
    fun resolvesTheKindFromTheUrlSoATranslatedTitleStillMaps() {
        val resolved =
            tabs(
                tab("Accueil", "/@chaine"),
                tab("Vidéos", "/@chaine/videos"),
                tab("En direct", "/@chaine/streams"),
                tab("Playlists", "/@chaine/playlists"),
            )

        assertEquals(
            listOf(ChannelTabKind.Home, ChannelTabKind.Videos, ChannelTabKind.Live, ChannelTabKind.Playlists),
            resolved.map { it.kind },
        )
        assertEquals("Vidéos", resolved[1].title)
    }

    @Test
    fun anUnrecognisedTabIsKeptAsUnknownRatherThanDropped() {
        val resolved = tabs(tab("Videos", "/@x/videos"), tab("Membership", "/@x/membership", "EgltZW1iZXJzaGlw"))

        assertEquals(2, resolved.size)
        assertEquals(ChannelTabKind.Unknown, resolved[1].kind)
        assertEquals("Membership", resolved[1].title)
        assertEquals("EgltZW1iZXJzaGlw", resolved[1].params)
    }

    @Test
    fun takesTheParamsTokenFromTheResponseRatherThanAConstant() {
        val resolved = tabs(tab("Videos", "/@x/videos", "TOKEN_FROM_SERVER"))

        assertEquals("TOKEN_FROM_SERVER", resolved.single().params)
    }

    @Test
    fun theLandingTabIsHomeEvenWithoutAnEndpoint() {
        val resolved = tabs(tab("Home", null, selected = true), tab("Videos", "/@x/videos"))

        assertEquals(ChannelTabKind.Home, resolved.first().kind)
        assertNull(resolved.first().params)
    }

    @Test
    fun readsAnExpandableTabRenderer() {
        val resolved =
            Json
                .parseToJsonElement(
                    """
                    {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[
                      {"expandableTabRenderer":{"title":"Search",
                        "endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/@x/search"}}}}}
                    ]}}}
                    """.trimIndent(),
                ).toChannelTabs()

        assertEquals(ChannelTabKind.Search, resolved.single().kind)
    }

    @Test
    fun aCoursesTabHasItsOwnKind1299() {
        assertEquals(ChannelTabKind.Courses, "/@Telusko/courses".toChannelTabKind())
    }

    @Test
    fun aChannelIdLandingUrlStillResolvesToHome() {
        assertEquals(ChannelTabKind.Home, "/channel/UCXuqSBlHAE6Xw-yeJA0Tunw".toChannelTabKind())
    }
}
