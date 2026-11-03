package io.github.aedev.flow.data.video

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class AutoDownloadArmTest {
    private val arm = AutoDownloadArm()
    private val video = video("vid_a")

    @Test
    fun anOpenedVideoResolvingAsAVideoIsHandedOverOnce() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isEqualTo(video)
        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun aVideoThatOnlyFollowedAnotherIsNeverHandedOver() {
        arm.arm(video, userOpened = false)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun aStepFromAnotherLoadOfTheSameVideoIsIgnored() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 2, AutoDownloadResolution.VIDEO)).isNull()
        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isEqualTo(video)
    }

    @Test
    fun nothingIsHandedOverBeforeTheLoadStarts() {
        arm.arm(video, userOpened = true)

        assertThat(arm.consume("vid_a", 0, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun openingAnotherVideoCancelsTheFirst() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)
        arm.arm(video("vid_b"), userOpened = true)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun aLoadForAnotherVideoCancelsTheArm() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_b", token = 4)
        arm.onLoadStarted("vid_a", token = 5)

        assertThat(arm.consume("vid_a", 5, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun movingOnToTheNextVideoCancelsTheArm() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)
        arm.arm(video("vid_b"), userOpened = false)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.VIDEO)).isNull()
    }

    @Test
    fun aFailedLoadKeepsTheArmForItsRetry() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.FAILED)).isNull()
        arm.onLoadStarted("vid_a", token = 4)
        assertThat(arm.consume("vid_a", 4, AutoDownloadResolution.VIDEO)).isEqualTo(video)
    }

    @Test
    fun aLiveUpcomingOrLocalResolutionSpendsTheArmWithoutADownload() {
        arm.arm(video, userOpened = true)
        arm.onLoadStarted("vid_a", token = 3)

        assertThat(arm.consume("vid_a", 3, AutoDownloadResolution.NOT_DOWNLOADABLE)).isNull()
        arm.onLoadStarted("vid_a", token = 4)
        assertThat(arm.consume("vid_a", 4, AutoDownloadResolution.VIDEO)).isNull()
    }

    private fun video(id: String) =
        Video(
            id = id,
            title = "Title $id",
            channelName = "Channel",
            channelId = "UC_channel",
            thumbnailUrl = "",
            duration = 300,
            viewCount = 0,
            uploadDate = "",
        )
}
