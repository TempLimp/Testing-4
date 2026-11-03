package io.github.aedev.flow.data.repository

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.requireFixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

/**
 * The player path reuses the raw watch response it already cached rather than issuing a second
 * /next, so this decode is the hinge: if it silently fails the callers fall back to a network
 * request and the dedup quietly stops working.
 */
class WatchMetadataDecodeTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture() =
        json.parseToJsonElement(
            requireFixture("watch_next_related_lockups.json"),
        )

    @Test
    fun aCachedWatchResponseDecodesIntoTheTypedModel() {
        val decoded = decodeWatchMetadata(fixture())

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.relatedVideos().map { it.videoId })
            .containsExactly("DdNbdhWmX04", "i_nj_vkD03g")
            .inOrder()
    }

    @Test
    fun theDecodedModelCarriesTheChannelIdsTheRelatedLaneFiltersOn() {
        val decoded = decodeWatchMetadata(fixture())!!

        assertThat(decoded.relatedVideos().mapNotNull { it.channelId() }).hasSize(2)
    }

    @Test
    fun aPayloadThatIsNotAWatchResponseDecodesToNullInsteadOfThrowing() {
        assertThat(decodeWatchMetadata(JsonPrimitive("not a watch response"))).isNull()
    }
}
