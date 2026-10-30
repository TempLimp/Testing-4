package io.github.aedev.flow.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SponsorBlockSegment(
    @SerialName("category") val category: String,
    @SerialName("segment") val segment: List<Float>, // [start, end]
    @SerialName("UUID") val uuid: String,
    @SerialName("actionType") val actionType: String // "skip", "mute", etc. (usually skip)
) {
    val startTime: Float get() = if (segment.isNotEmpty()) segment[0] else 0f
    val endTime: Float get() = if (segment.size > 1) segment[1] else 0f
}
