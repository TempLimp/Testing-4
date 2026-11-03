package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class MusicAudioQualityTest {
    @Test
    fun autoStreamsHighQualityOnWifiAndMediumOnMobileData() {
        assertEquals(MusicAudioQuality.HIGH, MusicAudioQuality.AUTO.resolve(onWifi = true))
        assertEquals(MusicAudioQuality.MEDIUM, MusicAudioQuality.AUTO.resolve(onWifi = false))
    }

    @Test
    fun aFixedChoiceIgnoresTheNetwork() {
        listOf(MusicAudioQuality.HIGH, MusicAudioQuality.MEDIUM, MusicAudioQuality.LOW).forEach { quality ->
            assertEquals(quality, quality.resolve(onWifi = true))
            assertEquals(quality, quality.resolve(onWifi = false))
        }
    }
}
