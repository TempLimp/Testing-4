package io.github.aedev.flow.data.music.model

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class MusicTitleKeyTest {
    @Test
    fun titlesMatchAcrossCaseAccentsBracketsFeaturesAndTheArtistPrefix() {
        assertThat(musicTitleKey("The Weeknd - BLINDING LIGHTS [4K]", listOf("The Weeknd"))).isEqualTo("blinding lights")
        assertThat(musicTitleKey("Levitating (feat. DaBaby)")).isEqualTo("levitating")
        assertThat(musicTitleKey("Anti-Hero ft. Bleachers")).isEqualTo("anti hero")
        assertThat(musicTitleKey("Café del Mar")).isEqualTo("cafe del mar")
    }

    @Test
    fun anArtistChannelsTopicSuffixIsNotPartOfTheName() {
        assertThat(musicArtistKey("Fairuz - Topic")).isEqualTo(musicArtistKey("FAIRUZ"))
    }
}
