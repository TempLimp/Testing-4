package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.data.model.RichTextTarget
import kotlinx.serialization.json.Json
import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class RichTextTargetsTest {
    private fun targetsOf(vararg commands: String): List<RichTextTarget> {
        val runs =
            commands.mapIndexed { index, command ->
                """{"startIndex": $index, "length": 1, "onTap": {"innertubeCommand": $command}}"""
            }
        val text = "x".repeat(commands.size)
        val json = """{"content": "$text", "commandRuns": [${runs.joinToString()}]}"""
        return requireNotNull(Json.parseToJsonElement(json).toRichText(ownVideoId = "ownVideo123")).spans.map { it.target }
    }

    private fun browse(id: String) = """{"browseEndpoint": {"browseId": "$id"}}"""

    @Test
    fun aBrowseLinkOpensWhatItsIdNames() {
        assertEquals(
            listOf(
                RichTextTarget.Channel("UCXuqSBlHAE6Xw-yeJA0Tunw"),
                RichTextTarget.Url("https://www.youtube.com/browse/VLPLabcdef"),
                RichTextTarget.Url("https://www.youtube.com/browse/MPREb_abc123"),
                RichTextTarget.Channel("FEunknown"),
            ),
            targetsOf(browse("UCXuqSBlHAE6Xw-yeJA0Tunw"), browse("VLPLabcdef"), browse("MPREb_abc123"), browse("FEunknown")),
        )
    }

    @Test
    fun aHashtagBrowseLinkStaysAHashtag() {
        assertEquals(
            listOf(RichTextTarget.Hashtag("queen")),
            targetsOf("""{"browseEndpoint": {"browseId": "FEhashtag", "canonicalBaseUrl": "/hashtag/queen"}}"""),
        )
    }

    // Upstream's third case, aLinkToAnotherVideoCarriesItsWatchLink, renders through utils/RichTextRendering,
    // which moves to S13 with the composables that use it; the case moves with it.
}
