package io.github.aedev.flow.player.audio

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class VolumeBoostGainTest {
    @Test
    fun noGainIsAskedForAtOrBelowUnity() {
        assertThat(boostGainMillibels(0f)).isEqualTo(0)
        assertThat(boostGainMillibels(0.5f)).isEqualTo(0)
        assertThat(boostGainMillibels(1f)).isEqualTo(0)
    }

    @Test
    fun aBoostAboveUnityBecomesAmplifierGainInHundredthsOfADecibel() {
        // Doubling the amplitude is +6.02 dB.
        assertThat(boostGainMillibels(2f)).isEqualTo(602)
        assertThat(boostGainMillibels(1.5f)).isEqualTo(352)
    }

    @Test
    fun gainRisesWithTheBoostAndIsBounded() {
        assertThat(boostGainMillibels(1.2f)).isGreaterThan(boostGainMillibels(1.1f))
        assertThat(boostGainMillibels(100f)).isAtMost(2000)
    }
}
