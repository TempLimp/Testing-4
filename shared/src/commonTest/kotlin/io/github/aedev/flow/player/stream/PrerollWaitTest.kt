package io.github.aedev.flow.player.stream

import io.github.aedev.flow.testing.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test

class PrerollWaitTest {
    private fun placements(json: String): JsonArray = Json.parseToJsonElement(json) as JsonArray

    private fun placement(
        kind: String,
        skipOffset: String?,
    ): String =
        """
        {"adPlacementRenderer":{"config":{"adPlacementConfig":{"kind":"$kind"}},
        "renderer":{"instreamVideoAdRenderer":{${skipOffset?.let { "\"skipOffsetMilliseconds\":$it" }.orEmpty()}}}}}
        """.trimIndent()

    @Test
    fun aStartAdHoldsTheStreamsForMostOfItsSkipOffset() {
        assertThat(PrerollWait.of(placements("[${placement("AD_PLACEMENT_KIND_START", "5000")}]"))).isEqualTo(4_000L)
    }

    @Test
    fun aSkipOffsetSentAsAStringIsReadTheSame() {
        assertThat(PrerollWait.of(placements("[${placement("AD_PLACEMENT_KIND_START", "\"5000\"")}]"))).isEqualTo(4_000L)
    }

    @Test
    fun midRollAndEndAdsDoNotHoldTheStartOfTheVideo() {
        val json = "[${placement("AD_PLACEMENT_KIND_MILLISECONDS", "5000")},${placement("AD_PLACEMENT_KIND_END", "5000")}]"

        assertThat(PrerollWait.of(placements(json))).isEqualTo(0L)
    }

    @Test
    fun aStartAdWithNoSkipOffsetWaitsTheLongestAndNothingWaitsLonger() {
        assertThat(PrerollWait.of(placements("[${placement("AD_PLACEMENT_KIND_START", null)}]"))).isEqualTo(PrerollWait.MAX_WAIT_MS)
        assertThat(PrerollWait.of(placements("[${placement("AD_PLACEMENT_KIND_START", "60000")}]"))).isEqualTo(PrerollWait.MAX_WAIT_MS)
    }

    @Test
    fun noAdsOrAShapeItDoesNotKnowMeansNoWait() {
        assertThat(PrerollWait.of(null)).isEqualTo(0L)
        assertThat(PrerollWait.of(placements("[1,\"x\",{}]"))).isEqualTo(0L)
    }
}
