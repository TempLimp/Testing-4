package io.github.aedev.flow.player.musicvideo

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import kotlin.test.Test

class MusicVideoPlannerTest {
    private val song = MusicTrack("song", "Anti-Hero", "Taylor Swift", "", 201)
    private val video = song.copy(videoId = "video", duration = 310, isVideoSong = true)

    @Test
    fun videoModeSwapsASongForItsVideoAndShowsThePicture() {
        val change = MusicVideoPlanner.replacement(song, showsVideo = false, song = null, video = video, wantsVideo = true)

        assertThat(change).isEqualTo(MusicVideoPlanner.Replacement(video, showsVideo = true))
    }

    @Test
    fun videoModeLeavesASongWithoutAVideoAndAnEntryAlreadyShowingOneAlone() {
        assertThat(MusicVideoPlanner.replacement(song, showsVideo = false, song = null, video = null, wantsVideo = true)).isNull()
        assertThat(MusicVideoPlanner.replacement(video, showsVideo = true, song = song, video = null, wantsVideo = true)).isNull()
    }

    @Test
    fun songModePutsTheSongBackInPlaceOfItsVideo() {
        val change = MusicVideoPlanner.replacement(video, showsVideo = true, song = song, video = null, wantsVideo = false)

        assertThat(change).isEqualTo(MusicVideoPlanner.Replacement(song, showsVideo = false))
    }

    @Test
    fun songModeOnlyHidesThePictureOfATrackThatIsAVideoItself() {
        val musicVideo = song.copy(isVideoSong = true)

        assertThat(MusicVideoPlanner.replacement(musicVideo, showsVideo = true, song = null, video = null, wantsVideo = false))
            .isEqualTo(MusicVideoPlanner.Replacement(musicVideo, showsVideo = false))
        assertThat(MusicVideoPlanner.replacement(song, showsVideo = false, song = null, video = null, wantsVideo = false)).isNull()
    }

    @Test
    fun thePlaceInTheSongCarriesOverShortOfTheEndOfAShorterVersion() {
        assertThat(MusicVideoPlanner.positionWithin(90_000, durationSeconds = 310)).isEqualTo(90_000)
        assertThat(MusicVideoPlanner.positionWithin(300_000, durationSeconds = 201)).isEqualTo(199_000)
        assertThat(MusicVideoPlanner.positionWithin(300_000, durationSeconds = 0)).isEqualTo(300_000)
        assertThat(MusicVideoPlanner.positionWithin(-5, durationSeconds = 201)).isEqualTo(0)
    }
}
