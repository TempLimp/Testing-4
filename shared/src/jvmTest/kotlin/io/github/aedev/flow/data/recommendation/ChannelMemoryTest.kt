/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.mockk.mockk
import kotlin.test.Test

class ChannelMemoryTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private fun watch(
        channel: String,
        id: String,
        at: Long = now - day,
        percent: Int = 90,
        lengthMinutes: Long = 10,
        isShort: Boolean = false,
    ) = VideoHistoryEntry(
        videoId = id,
        position = lengthMinutes * minute * percent / 100,
        duration = lengthMinutes * minute,
        timestamp = at,
        title = id,
        thumbnailUrl = "",
        channelName = "Channel $channel",
        channelId = id(channel),
        isShort = isShort,
    )

    private fun like(
        channel: String,
        at: Long = now - day,
    ) = LikedVideoInfo(videoId = "liked-$channel-$at", title = "", thumbnail = "", channelName = "", likedAt = at, channelId = id(channel))

    /** Real channel ids start with UC; "local" and "" are the placeholders downloads carry. */
    private fun id(channel: String) = if (channel == "local" || channel.isEmpty()) channel else "UC$channel"

    private fun List<RememberedChannel>.ids() = map { it.channelId.removePrefix("UC") }

    private fun remembered(
        history: List<VideoHistoryEntry>,
        likes: List<LikedVideoInfo> = emptyList(),
        state: ChannelMemoryState = ChannelMemoryState(),
        exclusions: ChannelMemoryExclusions = ChannelMemoryExclusions(),
        at: Long = now,
    ) = ChannelMemory.remembered(ChannelMemory.engagements(history, likes, state), state, exclusions, at)

    @Test
    fun twoRealWatchesOrOneWatchAndALikeQualifyAChannel() {
        val history =
            listOf(watch("A", "a1"), watch("A", "a2"), watch("B", "b1"), watch("C", "c1"))

        assertThat(remembered(history, likes = listOf(like("B"))).ids()).containsExactly("A", "B")
    }

    @Test
    fun placeholderChannelsOfDownloadedFilesAreNeverRemembered() {
        val history = listOf(watch("local", "f1"), watch("local", "f2"), watch("", "f3"), watch("", "f4"))

        assertThat(remembered(history)).isEmpty()
    }

    @Test
    fun halfWatchedVideosAndShortsAreNotRealWatches() {
        val history =
            listOf(
                watch("A", "a1", percent = 30),
                watch("A", "a2", percent = 30),
                watch("B", "b1", percent = 40, lengthMinutes = 2),
                watch("B", "b2", percent = 40, lengthMinutes = 2),
                watch("C", "c1", isShort = true),
                watch("C", "c2", isShort = true),
                watch("D", "d1", percent = 40, lengthMinutes = 20),
                watch("D", "d2", percent = 40, lengthMinutes = 20),
            )

        assertThat(remembered(history).ids()).containsExactly("D")
    }

    @Test
    fun theScoreHalvesEveryThirtyDaysSinceTheLastRealWatch() {
        val fresh = ChannelEngagement("A", "A", realWatches = 2, watchedSeconds = 0, likes = 0, lastRealWatchAt = now)

        assertThat(ChannelMemory.score(fresh.copy(lastRealWatchAt = now - 30 * day), now))
            .isWithin(1e-9)
            .of(ChannelMemory.score(fresh, now) / 2)
    }

    @Test
    fun channelsGoneQuietFallBelowTheFloor() {
        val history = listOf(watch("A", "a1", at = now - 120 * day), watch("A", "a2", at = now - 120 * day))

        assertThat(remembered(history)).isEmpty()
    }

    @Test
    fun onlyTheTwentyBestChannelsAreKept() {
        val history = (1..30).flatMap { c -> (1..2 + c % 5).map { watch("C$c", "v$c-$it") } }

        val kept = remembered(history)

        assertThat(kept).hasSize(ChannelMemoryParams.MAX_CHANNELS)
        assertThat(kept.map { it.score }).isInOrder(Comparator.reverseOrder<Double>())
    }

    @Test
    fun subscribedAndBlockedChannelsAreNeverRemembered() {
        val history = listOf("A", "B", "C").flatMap { c -> listOf(watch(c, "${c}1"), watch(c, "${c}2")) }

        val kept = remembered(history, exclusions = ChannelMemoryExclusions(subscribed = setOf(id("A")), blocked = setOf(id("B"))))

        assertThat(kept.ids()).containsExactly("C")
    }

    @Test
    fun aRejectionExcludesTheChannelUntilTheViewerWatchesItAgain() {
        val history = listOf(watch("A", "a1", at = now - 3 * day), watch("A", "a2", at = now - 3 * day))
        val rejectedAfter = ChannelMemory.recordRejection(ChannelMemoryState(), id("A"), "A", now - day)
        val rejectedBefore = ChannelMemory.recordRejection(ChannelMemoryState(), id("A"), "A", now - 5 * day)

        assertThat(remembered(history, state = rejectedAfter)).isEmpty()
        assertThat(remembered(history, state = rejectedBefore).ids()).containsExactly("A")
    }

    @Test
    fun aRecentNotInterestedOnTheChannelExcludesItLikeARejection() {
        val history = listOf(watch("A", "a1", at = now - 3 * day), watch("A", "a2", at = now - 3 * day))

        val recent = ChannelMemoryExclusions(notInterestedAt = mapOf(id("A") to now - day))
        val expired = ChannelMemoryExclusions(notInterestedAt = mapOf(id("A") to now - 20 * day))

        assertThat(remembered(history, exclusions = recent)).isEmpty()
        assertThat(remembered(history, exclusions = expired).ids()).containsExactly("A")
    }

    @Test
    fun aRemovedChannelComesBackOnlyThroughNewWatches() {
        val old = listOf(watch("A", "a1", at = now - 5 * day), watch("A", "a2", at = now - 5 * day))
        val forgotten = ChannelMemory.forget(ChannelMemoryState(), id("A"), now - 2 * day)

        assertThat(remembered(old, state = forgotten)).isEmpty()
        assertThat(remembered(old + watch("A", "a3") + watch("A", "a4"), state = forgotten)).isNotEmpty()
    }

    @Test
    fun clearingTheMemoryForgetsEveryWatchBeforeIt() {
        val history = listOf("A", "B").flatMap { c -> listOf(watch(c, "${c}1"), watch(c, "${c}2")) }

        assertThat(remembered(history, state = ChannelMemory.clear(ChannelMemoryState(), now))).isEmpty()
    }

    @Test
    fun atMostTenStaleChannelsAreRefreshedPerLoad() {
        val channels = (1..15).map { RememberedChannel("C$it", "C$it", score = 20.0 - it) }
        val state =
            ChannelMemoryState(
                entries =
                    mapOf(
                        "C1" to ChannelMemoryEntry(lastFetchedAt = now - 2 * 60 * minute),
                        "C2" to ChannelMemoryEntry(lastFetchedAt = now - 7 * 60 * minute),
                    ),
            )

        val queue = ChannelMemory.refreshQueue(channels, state, now)

        assertThat(queue).hasSize(ChannelMemoryParams.MAX_REFRESH_PER_LOAD)
        assertThat(queue.map { it.channelId }).doesNotContain("C1")
        assertThat(queue.first().channelId).isEqualTo("C2")
    }

    private fun upload(
        id: String,
        ageDays: Long,
        isShort: Boolean = false,
        membersOnly: Boolean = false,
    ) = Video(
        id = id,
        title = id,
        channelName = "A",
        channelId = "A",
        thumbnailUrl = "",
        duration = if (isShort) 30 else 600,
        viewCount = 10,
        uploadDate = "$ageDays days ago",
        timestamp = now - ageDays * day,
        isShort = isShort,
        membersOnlyText = if (membersOnly) "Members only" else null,
    )

    @Test
    fun onlyRecentPublicLongFormUploadsAreKept() {
        val videos =
            listOf(upload("new", 1), upload("old", 20), upload("reel", 1, isShort = true), upload("paid", 1, membersOnly = true))

        val state = ChannelMemory.recordUploads(ChannelMemoryState(), "A", "A", videos, now)
        val uploads = ChannelMemory.uploads(listOf(RememberedChannel("A", "A", 5.0)), state, now)

        assertThat(uploads.map { it.id }).containsExactly("new")
        assertThat(state.entries.getValue("A").lastFetchedAt).isEqualTo(now)
    }

    @Test
    fun channelMemorySurvivesASaveAndLoadOfTheBrain() {
        val storage = NeuroStorage()
        val state =
            ChannelMemory
                .recordUploads(ChannelMemoryState(clearedAt = 5L), "A", "Channel A", listOf(upload("new", 1)), now)
                .let { ChannelMemory.recordRejection(it, "B", "Channel B", now) }
        val brain = UserBrain(channelMemory = state)

        val restored = with(storage) { brain.toSerializable() }.toUserBrain()

        assertThat(restored.channelMemory).isEqualTo(state)
    }
}
