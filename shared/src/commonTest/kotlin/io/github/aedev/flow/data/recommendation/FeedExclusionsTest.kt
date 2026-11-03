/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

// The FeedExclusions case of upstream's FeedExclusionsTest (S06 pulled the class forward). S09 adds the other four,
// which test suppressVideo and keepingHiddenContent, with those functions.
class FeedExclusionsTest {
    @Test
    fun hidingChannelsAddsToTheExclusionsWithoutDroppingAny() {
        val base =
            FeedExclusions(
                suppressedVideoIds = setOf("v1"),
                blockedChannelIds = setOf("UCblocked"),
                blockedText = { title, _ -> title == "phonk" },
            )

        val widened = base.hidingChannels(setOf("UCsub"))

        assertThat(widened.hidesFromRecommendations(video("v2", "UCsub"))).isTrue()
        assertThat(widened.hidesFromRecommendations(video("v1", "UCother"))).isTrue()
        assertThat(widened.hidesFromRecommendations(video("v3", "UCblocked"))).isTrue()
        assertThat(widened.hidesFromRecommendations(video("v4", "UCother", title = "phonk"))).isTrue()
        assertThat(widened.hidesFromRecommendations(video("v5", "UCother"))).isFalse()
        assertThat(base.hidingChannels(emptySet())).isSameInstanceAs(base)
    }

    private fun video(
        id: String,
        channelId: String,
        title: String = "title-$id",
    ) = Video(
        id = id,
        title = title,
        channelName = channelId,
        channelId = channelId,
        thumbnailUrl = "",
        duration = 600,
        viewCount = 1,
        uploadDate = "",
    )
}