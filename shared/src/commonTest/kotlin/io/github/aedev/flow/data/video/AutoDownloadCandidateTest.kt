package io.github.aedev.flow.data.video

import io.github.aedev.flow.data.local.AutoDownloadMode
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import kotlin.test.Test

class AutoDownloadCandidateTest {
    private val queueable =
        AutoDownloadCandidate(
            mode = AutoDownloadMode.ALWAYS,
            onWifi = true,
            userOpened = true,
            isVod = true,
            isShort = false,
            isMusic = false,
            isLocal = false,
            existing = null,
        )

    @Test
    fun anOpenedVideoWithNoDownloadIsQueued() {
        assertTrue(queueable.shouldAutoDownload())
    }

    @Test
    fun offNeverQueues() {
        assertFalse(queueable.copy(mode = AutoDownloadMode.OFF).shouldAutoDownload())
        assertFalse(queueable.copy(mode = AutoDownloadMode.OFF, onWifi = false).shouldAutoDownload())
    }

    @Test
    fun wifiModeQueuesOnWifiOnly() {
        assertTrue(queueable.copy(mode = AutoDownloadMode.WIFI).shouldAutoDownload())
        assertFalse(queueable.copy(mode = AutoDownloadMode.WIFI, onWifi = false).shouldAutoDownload())
    }

    @Test
    fun alwaysQueuesOnMobileData() {
        assertTrue(queueable.copy(onWifi = false).shouldAutoDownload())
    }

    @Test
    fun aVideoThatOnlyFollowedAnotherNeverQueues() {
        assertFalse(queueable.copy(userOpened = false).shouldAutoDownload())
    }

    @Test
    fun liveUpcomingFailedAndAlreadyLocalResolutionsNeverQueue() {
        assertFalse(queueable.copy(isVod = false).shouldAutoDownload())
    }

    @Test
    fun shortsMusicAndDeviceFilesNeverQueue() {
        assertFalse(queueable.copy(isShort = true).shouldAutoDownload())
        assertFalse(queueable.copy(isMusic = true).shouldAutoDownload())
        assertFalse(queueable.copy(isLocal = true).shouldAutoDownload())
    }

    @Test
    fun anyExistingDownloadRowIsLeftAlone() {
        DownloadItemStatus.entries.forEach { status ->
            assertFalse("$status", queueable.copy(existing = status).shouldAutoDownload())
        }
    }

    @Test
    fun pausedFailedAndCancelledDownloadsAreNeverRestarted() {
        listOf(DownloadItemStatus.PAUSED, DownloadItemStatus.FAILED, DownloadItemStatus.CANCELLED).forEach { status ->
            assertFalse("$status", queueable.copy(existing = status).shouldAutoDownload())
        }
    }
}
