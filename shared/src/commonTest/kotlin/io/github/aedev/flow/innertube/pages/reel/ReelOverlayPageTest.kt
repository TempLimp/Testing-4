package io.github.aedev.flow.innertube.pages.reel

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNotNull
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Ignore
import kotlin.test.Test

class ReelOverlayPageTest {
    private fun parse(raw: String) = Json.parseToJsonElement(raw).jsonObject.toReelOverlay()

    @Test
    fun readsTheLegacyRendererOverlay() {
        val overlay =
            parse(
                """
                { "overlay": { "reelPlayerOverlayRenderer": {
                    "likeButton": { "likeButtonRenderer": { "likeCount": 2772, "likeCountText": { "runs": [ { "text": "2.7K" } ] }, "likeStatus": "INDIFFERENT" } },
                    "reelPlayerHeaderSupportedRenderers": { "reelPlayerHeaderRenderer": {
                      "reelTitleText": { "runs": [ { "text": "Custom PC" } ] },
                      "timestampText": { "runs": [ { "text": "15 hours ago" } ] },
                      "channelNavigationEndpoint": { "browseEndpoint": { "browseId": "UCXuqSBlHAE6Xw-yeJA0Tunw" } },
                      "channelTitleText": { "runs": [ { "text": "@LinusTechTips" } ] },
                      "channelThumbnail": { "thumbnails": [ { "url": "https://yt3.test/s48", "width": 48, "height": 48 }, { "url": "https://yt3.test/s176", "width": 176, "height": 176 } ] }
                    } },
                    "viewCommentsButton": { "buttonRenderer": { "text": { "runs": [ { "text": "62" } ] } } },
                    "subscribeButtonRenderer": { "channelId": "UCXuqSBlHAE6Xw-yeJA0Tunw", "subscribed": false },
                    "pivotButton": { "pivotButtonRenderer": { "contentDescription": { "runs": [ { "text": "See more videos using this sound" } ] }, "thumbnail": { "thumbnails": [ { "url": "https://yt3.test/sound", "width": 48, "height": 48 } ] } } }
                } } }
                """.trimIndent(),
            )

        assertNotNull(overlay)
        assertEquals("Custom PC", overlay!!.title)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay.channelId)
        assertEquals("@LinusTechTips", overlay.channelName)
        assertEquals("https://yt3.test/s176", overlay.channelAvatarUrl)
        assertEquals(2772L, overlay.likeCount)
        assertEquals("2.7K", overlay.likeCountText)
        assertEquals(62L, overlay.commentCount)
        assertEquals("15 hours ago", overlay.relativeTimestamp)
        assertEquals("See more videos using this sound", overlay.soundTitle)
        assertEquals("https://yt3.test/sound", overlay.soundThumbnailUrl)
        assertEquals(false, overlay.isSubscribed)
    }

    @Test
    fun readsTheWebViewModelOverlay() {
        val overlay =
            parse(
                """
                { "overlay": { "reelPlayerOverlayRenderer": {
                    "reelPlayerHeaderSupportedRenderers": { "reelPlayerHeaderRenderer": { "timestampText": { "simpleText": "2 days ago" } } },
                    "playerOverlay": { "reelPlayerOverlayViewModel": {
                      "metapanel": { "reelMetapanelViewModel": { "metadataItems": [
                        { "reelChannelBarViewModel": {
                            "channelName": { "content": "@LinusTechTips", "commandRuns": [ { "onTap": { "innertubeCommand": { "browseEndpoint": { "browseId": "UCXuqSBlHAE6Xw-yeJA0Tunw", "canonicalBaseUrl": "/@LinusTechTips/shorts" } } } } ] },
                            "decoratedAvatarViewModel": { "decoratedAvatarViewModel": { "avatar": { "avatarViewModel": { "image": { "sources": [ { "url": "https://yt3.test/avatar", "width": 48, "height": 48 } ] } } } } },
                            "subscribeStateEntityStoreKey": "EhhVQ1h1cVNCbEhBRTZYdy15ZUpBMFR1bncgMygB"
                        } },
                        { "shortsVideoTitleViewModel": { "text": { "content": "Steam Frame Wireless" } } },
                        { "reelCarouselViewModel": { "buttonViewModels": [ { "reelCarouselButtonViewModel": { "buttonViewModel": { "buttonViewModel": { "onTap": { "innertubeCommand": { "watchEndpoint": { "videoId": "UYpEQKCWfM4" } } } } } } } ] } }
                      ] } },
                      "actionBar": { "reelActionBarViewModel": { "buttonViewModels": [
                        { "likeButtonViewModel": { "toggleButtonViewModel": { "toggleButtonViewModel": {
                            "defaultButtonViewModel": { "buttonViewModel": { "iconName": "EMPTY_HEART", "title": "2.7K" } },
                            "toggledButtonViewModel": { "buttonViewModel": { "iconName": "FULL_HEART", "title": "2.7K", "accessibilityText": "2,746 likes" } }
                        } } } },
                        { "buttonViewModel": { "iconName": "SHORTS_COMMENT", "title": "62", "accessibilityText": "View 62 comments" } },
                        { "buttonViewModel": { "iconName": "SHORTS_SHARE", "title": "Share" } },
                        { "pivotButtonViewModel": { "soundAttributionTitle": { "content": "Original Sound" }, "thumbnail": { "sources": [ { "url": "https://yt3.test/sound", "width": 48, "height": 48 } ] } } }
                      ] } }
                    } }
                } },
                  "frameworkUpdates": { "entityBatchUpdate": { "mutations": [ { "payload": { "subscriptionStateEntity": { "key": "k", "subscribed": false } } } ] } } }
                """.trimIndent(),
            )

        assertNotNull(overlay)
        assertEquals("Steam Frame Wireless", overlay!!.title)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay.channelId)
        assertEquals("@LinusTechTips", overlay.channelName)
        assertEquals("https://yt3.test/avatar", overlay.channelAvatarUrl)
        assertEquals(2746L, overlay.likeCount)
        assertEquals("2.7K", overlay.likeCountText)
        assertEquals(62L, overlay.commentCount)
        assertEquals("62", overlay.commentCountText)
        assertEquals("2 days ago", overlay.relativeTimestamp)
        assertEquals("Original Sound", overlay.soundTitle)
        assertEquals("UYpEQKCWfM4", overlay.companionVideoId)
        assertEquals(false, overlay.isSubscribed)
    }

    @Test
    fun aCollaborationRecoversTheChannelIdFromTheSubscribeEntityKey() {
        val overlay =
            parse(
                """
                { "overlay": { "reelPlayerOverlayRenderer": { "playerOverlay": { "reelPlayerOverlayViewModel": {
                    "metapanel": { "reelMetapanelViewModel": { "metadataItems": [
                      { "reelChannelBarViewModel": {
                          "channelName": { "content": "@LinusTechTips and 2 more", "commandRuns": [ { "onTap": { "innertubeCommand": { "showDialogCommand": {} } } } ] },
                          "avatarStackViewModel": { "avatarStackViewModel": { "avatars": [ { "avatarViewModel": { "image": { "sources": [ { "url": "https://yt3.test/first", "width": 48 } ] } } } ] } },
                          "subscribeStateEntityStoreKey": "EhhVQ1h1cVNCbEhBRTZYdy15ZUpBMFR1bncgMygB"
                      } }
                    ] } }
                } } } } }
                """.trimIndent(),
            )

        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay?.channelId)
        assertEquals("@LinusTechTips and 2 more", overlay?.channelName)
        assertEquals("https://yt3.test/first", overlay?.channelAvatarUrl)
    }

    private fun carousel(vararg buttons: String) =
        parse(
            """
            { "overlay": { "reelPlayerOverlayRenderer": { "playerOverlay": { "reelPlayerOverlayViewModel": {
                "metapanel": { "reelMetapanelViewModel": { "metadataItems": [
                  { "shortsVideoTitleViewModel": { "text": { "content": "A reel" } } },
                  { "reelCarouselViewModel": { "buttonViewModels": [ ${buttons.joinToString()} ] } }
                ] } }
            } } } } }
            """.trimIndent(),
        )

    private fun carouselButton(
        command: String,
        extra: String = "",
    ) = """{ "reelCarouselButtonViewModel": { "buttonViewModel": { "buttonViewModel": {
          "onTap": { "innertubeCommand": $command }$extra } } } }"""

    private val soundPivot =
        carouselButton(
            """{ "showEngagementPanelEndpoint": { "identifier": { "tag": "engagement-panel-shorts-audio-pivot" } } }""",
            """, "titleFormatted": { "content": "Original sound" }""",
        )

    @Test
    fun theLinkedVideoCarriesItsTitle() {
        val overlay =
            carousel(
                soundPivot,
                carouselButton(
                    """{ "watchEndpoint": { "videoId": "UYpEQKCWfM4" } }""",
                    """, "accessibilityId": "id.reel_multi_format_link", "accessibilityText": "I Finally got Sponsored by Red Bull",
                    "titleFormatted": { "content": "I Finally got Sponsored by Red Bull" }""",
                ),
            )

        assertEquals("UYpEQKCWfM4", overlay?.companionVideoId)
        assertEquals("I Finally got Sponsored by Red Bull", overlay?.companionVideoTitle)
    }

    @Test
    fun theLinkedVideoTitleFallsBackToTheAccessibilityText() {
        val overlay =
            carousel(
                carouselButton(
                    """{ "watchEndpoint": { "videoId": "JUZwh0qNTHQ" } }""",
                    """, "accessibilityText": "Full video" """,
                ),
            )

        assertEquals("JUZwh0qNTHQ", overlay?.companionVideoId)
        assertEquals("Full video", overlay?.companionVideoTitle)
    }

    @Test
    fun aLinkToAnotherReelIsNotALinkedVideo() {
        val overlay =
            carousel(
                soundPivot,
                carouselButton(
                    """{ "reelWatchEndpoint": { "videoId": "pW3pyniVQ6Y" } }""",
                    """, "titleFormatted": { "content": "Another reel" }""",
                ),
            )

        assertNotNull(overlay)
        assertNull(overlay!!.companionVideoId)
        assertNull(overlay.companionVideoTitle)
    }

    @Test
    fun anElementsOverlayIsUnreadable() {
        val overlay =
            parse(
                """
                { "overlay": { "reelPlayerOverlayRenderer": {
                    "style": "REEL_PLAYER_OVERLAY_STYLE_SHORTS_RIGHT_SIDE",
                    "playerOverlay": { "elementRenderer": { "newElement": {} } }
                } } }
                """.trimIndent(),
            )

        assertNull(overlay)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun webCollaborationCapture() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_WEB_COLLAB).toReelOverlay()

        assertNotNull(overlay)
        assertEquals("Custom PC for DisguisedToast", overlay!!.title)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay.channelId)
        assertTrue(overlay.channelName.orEmpty().startsWith("@LinusTechTips"))
        assertTrue((overlay.likeCount ?: 0) > 2_000)
        assertTrue((overlay.commentCount ?: 0) >= 62)
        assertEquals("Original Sound", overlay.soundTitle)
        assertNotNull(overlay.relativeTimestamp)
        assertNotNull(overlay.companionVideoId)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun webSingleCreatorCapture() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_WEB_SINGLE).toReelOverlay()

        assertNotNull(overlay)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay!!.channelId)
        assertEquals("@LinusTechTips", overlay.channelName)
        assertNotNull(overlay.channelAvatarUrl)
        assertNotNull(overlay.title)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun mwebCapture() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_MWEB).toReelOverlay()

        assertNotNull(overlay)
        assertEquals("Custom PC for DisguisedToast", overlay!!.title)
        assertTrue((overlay.likeCount ?: 0) > 2_000)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun visionosCaptureIsTheLegacyShapeWithAnExactLikeCount() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_VISIONOS).toReelOverlay()

        assertNotNull(overlay)
        assertEquals("Custom PC for DisguisedToast", overlay!!.title)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay.channelId)
        assertTrue((overlay.likeCount ?: 0) > 2_000)
        assertTrue((overlay.commentCount ?: 0) >= 62)
        assertNotNull(overlay.channelAvatarUrl)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun androidVrCaptureAddsTheSoundPivot() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_ANDROID_VR).toReelOverlay()

        assertNotNull(overlay?.soundTitle)
        assertNotNull(overlay?.soundThumbnailUrl)
    }

    @Ignore // shorts/ fixtures are gitignored upstream, so upstream skips this test too

    @Test
    fun androidCaptureIsAnElementsTemplateAndYieldsNothing() {
        assertNull(ReelFixture(ReelFixture.ITEM_WATCH_ANDROID_ELEMENTS).toReelOverlay())
    }
}
