package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.WatchedThreshold
import io.github.aedev.flow.data.local.dao.WatchProgress
import io.github.aedev.flow.testing.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** #979 (owner decision D1): the Shorts queues and shelves hide watched reels under their own setting. */
class SubscriptionWatchedVideosTest {
    private val history =
        listOf(
            WatchProgress("finished-reel", position = 30_000L, duration = 30_000L, timestamp = 0L),
            WatchProgress("started-reel", position = 1_500L, duration = 30_000L, timestamp = 0L),
        )

    private fun watched(
        hideWatchedShorts: Boolean,
        hideWatchedVideos: Boolean,
        threshold: WatchedThreshold = WatchedThreshold.ALMOST_FINISHED,
        rows: List<WatchProgress> = history,
    ): SubscriptionWatchedVideos {
        val viewHistory: ViewHistory = mockk(relaxed = true)
        every { viewHistory.getVideoWatchProgress() } returns flowOf(rows)
        val preferences: PlayerPreferences = mockk(relaxed = true)
        every { preferences.hideWatchedShorts } returns flowOf(hideWatchedShorts)
        every { preferences.hideWatchedVideosFromSubscriptions } returns flowOf(hideWatchedVideos)
        every { preferences.watchedThreshold } returns flowOf(threshold)
        val database: AppDatabase = mockk(relaxed = true)
        every { database.downloadDao().getVideoDownloads() } returns flowOf(emptyList())
        return SubscriptionWatchedVideos(viewHistory, preferences, database)
    }

    @Test
    fun finishedReelsAreHiddenWhileTheShortsSettingIsOn() =
        runTest {
            assertThat(watched(hideWatchedShorts = true, hideWatchedVideos = false).shortIds.first()).containsExactly("finished-reel")
        }

    @Test
    fun aReelTheBadgeShowsAsWatchedIsHiddenUnderAStricterVideoThreshold() =
        runTest {
            val rows = listOf(WatchProgress("reel-92", position = 27_600L, duration = 30_000L, timestamp = 0L))
            val watched = watched(hideWatchedShorts = true, hideWatchedVideos = true, threshold = WatchedThreshold.PERCENT_99, rows = rows)

            assertThat(watched.shortIds.first()).containsExactly("reel-92")
            assertThat(watched.ids.first()).isEmpty()
        }

    @Test
    fun switchingTheShortsSettingOffShowsWatchedReelsEvenWhenVideosAreHidden() =
        runTest {
            val watched = watched(hideWatchedShorts = false, hideWatchedVideos = true)

            assertThat(watched.shortIds.first()).isEmpty()
            assertThat(watched.ids.first()).containsExactly("finished-reel")
        }
}
