package io.github.aedev.flow.data.video.storage

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

// The DownloadLocation case of upstream's DownloadLocationTest (S06 pulled the class forward). S18 adds the seven
// others, which test resolveDownloadDestination and documentIdToPath, with those functions.
class DownloadLocationTest {
    @Test
    fun musicFollowsTheVideoLocationUntilItHasItsOwn() {
        val video = DownloadLocation(path = "/storage/emulated/0/Videos")
        val music = DownloadLocation(path = "/storage/emulated/0/Songs")
        assertEquals(video, DownloadLocation.forDownload(isMusic = true, video = video, music = DownloadLocation.DEFAULT))
        assertEquals(music, DownloadLocation.forDownload(isMusic = true, video = video, music = music))
        assertEquals(video, DownloadLocation.forDownload(isMusic = false, video = video, music = music))
    }
}