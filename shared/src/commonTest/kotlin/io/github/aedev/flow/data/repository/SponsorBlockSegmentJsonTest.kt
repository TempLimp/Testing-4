package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

/**
 * The stored-payload half of [SponsorBlockRepository]: the download store writes segments with
 * [SponsorBlockRepository.serializeSegments] and offline playback reads them back with
 * [SponsorBlockRepository.parseSegments], so the two have to agree.
 */
class SponsorBlockSegmentJsonTest {
    private val repository = SponsorBlockRepository()

    @Test
    fun aSerializedListReadsBackUnchanged() {
        val segments =
            listOf(
                SponsorBlockSegment(
                    category = "sponsor",
                    segment = listOf(1.5f, 12.25f),
                    uuid = "uuid-1",
                    actionType = "skip",
                ),
                SponsorBlockSegment(
                    category = "intro",
                    segment = listOf(0f, 4f),
                    uuid = "uuid-2",
                    actionType = "skip",
                ),
            )

        assertThat(repository.parseSegments(repository.serializeSegments(segments))).isEqualTo(segments)
    }

    @Test
    fun anEmptyListRoundTripsAsAnEmptyListRatherThanNull() {
        assertThat(repository.parseSegments(repository.serializeSegments(emptyList()))).isEmpty()
    }

    @Test
    fun absentAndUnreadablePayloadsReadAsNull() {
        assertThat(repository.parseSegments(null)).isNull()
        assertThat(repository.parseSegments("")).isNull()
        assertThat(repository.parseSegments("   ")).isNull()
        assertThat(repository.parseSegments("{not json")).isNull()
    }
}
