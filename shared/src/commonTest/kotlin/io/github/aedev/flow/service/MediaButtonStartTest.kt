package io.github.aedev.flow.service

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class MediaButtonStartTest {
    @Test
    fun aMediaButtonStartWithNothingLoadedIsDeclined() {
        assertThat(shouldDeclineMediaButtonStart(MediaKeys.ACTION_MEDIA_BUTTON, hasMediaItem = false)).isTrue()
    }

    @Test
    fun aMediaButtonStartWithSomethingLoadedGetsTheSession() {
        assertThat(shouldDeclineMediaButtonStart(MediaKeys.ACTION_MEDIA_BUTTON, hasMediaItem = true)).isFalse()
    }

    @Test
    fun anOrdinaryControllerAlwaysGetsTheSession() {
        assertThat(shouldDeclineMediaButtonStart(null, hasMediaItem = false)).isFalse()
    }

    @Test
    fun aPlayPressWithASavedQueueGetsTheSessionSoResumptionCanStartIt() {
        assertThat(shouldDeclineMediaButtonStart(MediaKeys.ACTION_MEDIA_BUTTON, hasMediaItem = false, resumesOnPlay = true)).isFalse()
    }

    @Test
    fun onlyTheKeysThatBeginPlaybackCanResume() {
        assertThat(isPlayKey(MediaKeys.KEYCODE_MEDIA_PLAY)).isTrue()
        assertThat(isPlayKey(MediaKeys.KEYCODE_MEDIA_PLAY_PAUSE)).isTrue()
        assertThat(isPlayKey(MediaKeys.KEYCODE_HEADSETHOOK)).isTrue()
        assertThat(isPlayKey(MediaKeys.KEYCODE_MEDIA_PAUSE)).isFalse()
        assertThat(isPlayKey(MediaKeys.KEYCODE_MEDIA_NEXT)).isFalse()
        assertThat(isPlayKey(null)).isFalse()
    }
}
