package io.github.aedev.flow.player

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FeedExclusions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PlayerRelatedVideosPolicyTest {
    @Test
    fun fallbackSuppliesRelatedVideosWhenPrimaryMetadataIsEmpty() {
        val fallback = listOf(video("related"))

        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = "playing",
                primary = emptyList(),
                fallback = fallback,
                current = emptyList(),
            )

        assertThat(selected).containsExactlyElementsIn(fallback)
    }

    @Test
    fun emptyEnrichmentNeverClearsCurrentRelatedVideos() {
        val current = listOf(video("current"))

        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = "playing",
                primary = emptyList(),
                fallback = emptyList(),
                current = current,
            )

        assertThat(selected).containsExactlyElementsIn(current)
    }

    @Test
    fun selectionRemovesPlayingVideoBlankIdsAndDuplicates() {
        val duplicate = video("related")

        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = "playing",
                primary = listOf(video("playing"), video(""), duplicate, duplicate.copy(title = "duplicate")),
                fallback = emptyList(),
                current = emptyList(),
            )

        assertThat(selected).containsExactly(duplicate)
    }

    @Test
    fun sanitizingDisplayCandidatesKeepsLiveAndUpcomingVideos() {
        val live = video("live").copy(isLive = true)
        val upcoming = video("upcoming").copy(isUpcoming = true)

        val selected = PlayerRelatedVideosPolicy.sanitize("playing", listOf(live, upcoming))

        assertThat(selected).containsExactly(live, upcoming).inOrder()
    }

    @Test
    fun sanitizingDropsReelsWhenShortsAreDisabled() {
        val reel = video("reel").copy(isShort = true)
        val shortMusicVideo = video("music").copy(duration = 45)

        val selected = PlayerRelatedVideosPolicy.sanitize("playing", listOf(reel, shortMusicVideo), shortsEnabled = false)

        assertThat(selected).containsExactly(shortMusicVideo)
    }

    @Test
    fun sanitizingKeepsReelsWhenShortsAreEnabled() {
        val reel = video("reel").copy(isShort = true)

        val selected = PlayerRelatedVideosPolicy.sanitize("playing", listOf(reel), shortsEnabled = true)

        assertThat(selected).containsExactly(reel)
    }

    @Test
    fun aBlockedCreatorIsDroppedFromTheRelatedList() {
        val candidates = listOf(video("keep", channelId = "wanted"), video("drop", channelId = "blocked"))

        val sanitized =
            PlayerRelatedVideosPolicy.sanitize(
                videoId = "playing",
                candidates = candidates,
                exclusions = FeedExclusions(blockedChannelIds = setOf("blocked")),
            )

        assertThat(sanitized.map { it.id }).containsExactly("keep")
    }

    @Test
    fun aCandidateWithNoChannelIdSurvivesRatherThanBeingDroppedBlindly() {
        val candidates = listOf(video("unknown", channelId = ""))

        val sanitized =
            PlayerRelatedVideosPolicy.sanitize(
                videoId = "playing",
                candidates = candidates,
                exclusions = FeedExclusions(blockedChannelIds = setOf("blocked")),
            )

        assertThat(sanitized.map { it.id }).containsExactly("unknown")
    }

    @Test
    fun aSourceMadeEmptyByBlockingFallsThroughToTheNextOne() {
        val primary = listOf(video("blocked-only", channelId = "blocked"))
        val fallback = listOf(video("wanted", channelId = "wanted"))

        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = "playing",
                primary = primary,
                fallback = fallback,
                current = emptyList(),
                exclusions = FeedExclusions(blockedChannelIds = setOf("blocked")),
            )

        assertThat(selected.map { it.id }).containsExactly("wanted")
    }

    // #1031: autoplay and the queue read this list, so a video marked not interested must leave it.
    @Test
    fun aVideoMarkedNotInterestedAndASuppressedChannelAreDropped() {
        val candidates =
            listOf(
                video("keep", channelId = "wanted"),
                video("not-interested", channelId = "wanted"),
                video("suppressed", channelId = "cooling-off"),
            )

        val sanitized =
            PlayerRelatedVideosPolicy.sanitize(
                videoId = "playing",
                candidates = candidates,
                exclusions =
                    FeedExclusions(
                        suppressedVideoIds = setOf("not-interested"),
                        suppressedChannelIds = setOf("cooling-off"),
                    ),
            )

        assertThat(sanitized.map { it.id }).containsExactly("keep")
    }

    @Test
    fun aBlockedTopicInTheTitleDropsTheCandidate() {
        val sanitized =
            PlayerRelatedVideosPolicy.sanitize(
                videoId = "playing",
                candidates = listOf(video("keep"), video("topic")),
                exclusions = FeedExclusions(blockedText = { title, _ -> title == "topic" }),
            )

        assertThat(sanitized.map { it.id }).containsExactly("keep")
    }

    @Test
    fun backgroundAutoplayDropsWhatTheLiveSourceHides() =
        runTest {
            val source =
                FeedExclusionsSource {
                    FeedExclusions(suppressedVideoIds = setOf("not-interested"), blockedChannelIds = setOf("blocked"))
                }
            val candidates =
                listOf(video("keep"), video("not-interested"), video("from-blocked", channelId = "blocked"))

            val sanitized = PlayerRelatedVideosPolicy.sanitizeHidden("playing", candidates, source)

            assertThat(sanitized.map { it.id }).containsExactly("keep")
        }

    @Test
    fun backgroundAutoplayWithoutASourceKeepsEveryCandidate() =
        runTest {
            val candidates = listOf(video("a"), video("b", channelId = "blocked"))

            val sanitized = PlayerRelatedVideosPolicy.sanitizeHidden("playing", candidates, source = null)

            assertThat(sanitized).containsExactlyElementsIn(candidates).inOrder()
        }

    private fun video(
        id: String,
        channelId: String = "channel-id",
    ) = Video(
        id = id,
        title = id,
        channelName = "channel",
        channelId = channelId,
        thumbnailUrl = "thumbnail",
        duration = 60,
        viewCount = 1L,
        uploadDate = "today",
    )
}
