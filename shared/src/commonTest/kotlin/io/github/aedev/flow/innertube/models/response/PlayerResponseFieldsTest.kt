package io.github.aedev.flow.innertube.models.response

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.requireFixture
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Decoded from a captured VISIONOS `/player` response. These fields were added from a live probe,
 * so the fixture is the probe's own output — a hand-written one would only prove the model agrees
 * with itself.
 */
class PlayerResponseFieldsTest {
    // Mirrors the InnerTube client's own Json: explicitNulls=false is what lets a nullable field
    // with no default (PlayabilityStatus.reason) be absent on a clean OK response.
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private fun response(name: String): PlayerResponse = json.decodeFromString(requireFixture(name))

    private fun vod() = response("player_visionos_vod.json")

    @Test
    fun storyboardSpecAndLevelAreRead() {
        val storyboards = vod().storyboards?.playerStoryboardSpecRenderer

        assertThat(storyboards?.spec).isNotNull()
        assertThat(storyboards!!.spec!!).contains("i.ytimg.com/sb/")
        assertThat(storyboards.recommendedLevel).isEqualTo(2)
    }

    @Test
    fun keywordsAreReadForTheRecommendationEngine() {
        val keywords = vod().videoDetails?.keywords.orEmpty()

        assertThat(keywords).isNotEmpty()
        assertThat(keywords).contains("linus tech tips")
    }

    @Test
    fun loudnessNormalisationConfigIsRead() {
        val audio = vod().playerConfig?.audioConfig

        assertThat(audio?.loudnessTargetLkfs).isEqualTo(-14.0)
        assertThat(audio?.trackAbsoluteLoudnessLkfs).isEqualTo(-17.0)
        assertThat(audio?.enablePerFormatLoudness).isTrue()
        assertThat(audio?.loudnessNormalizationConfig?.minimumLoudnessTargetLkfs).isEqualTo(-31.0)
    }

    @Test
    fun anSdrLadderReportsNoHdrFormat() {
        val hdr =
            vod()
                .streamingData
                ?.adaptiveFormats
                .orEmpty()
                .filter { it.colorInfo?.isHdr == true }

        assertThat(hdr).isEmpty()
    }

    @Test
    fun microformatCarriesTheCategoryAndExactLikeCountWhenTheClientReturnsIt() {
        val micro = response("player_web_vod.json").microformat?.playerMicroformatRenderer

        assertThat(micro?.category).isEqualTo("Science & Technology")
        assertThat(micro?.likeCount).isEqualTo("20886")
        assertThat(micro?.publishDate).startsWith("2026-09-15")
    }

    @Test
    fun aVisionosResponseSimplyHasNoMicroformat() {
        assertThat(vod().microformat).isNull()
    }
}
