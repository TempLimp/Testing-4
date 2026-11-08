package io.github.aedev.flow.player.renderer.subtitle

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import io.github.aedev.flow.utils.Log

/**
 * A caption on screen, with the fields of Media3's `androidx.media3.common.text.Cue` that Flow's parsers set.
 * Upstream hands its cues to Media3's SubtitleView; on iOS both engines play without a text renderer, so the
 * player manager decodes captions itself (this package) and the player screen draws the cues it publishes.
 * [text] keeps the spans as Compose styles (bold, italic, underline, colours) where Android uses spans.
 */
data class Cue(
    val text: AnnotatedString?,
    val textAlignment: TextAlign? = null,
    val line: Float = DIMEN_UNSET,
    val lineType: Int = TYPE_UNSET,
    val lineAnchor: Int = TYPE_UNSET,
    val position: Float = DIMEN_UNSET,
    val positionAnchor: Int = TYPE_UNSET,
    val size: Float = DIMEN_UNSET,
) {
    companion object {
        // Media3's values.
        const val DIMEN_UNSET = -Float.MAX_VALUE
        const val TYPE_UNSET = Int.MIN_VALUE
        const val ANCHOR_TYPE_START = 0
        const val ANCHOR_TYPE_MIDDLE = 1
        const val ANCHOR_TYPE_END = 2
        const val LINE_TYPE_FRACTION = 0
        const val LINE_TYPE_NUMBER = 1
    }
}

/** Media3's `CuesWithTiming`: [cues] shown from [startTimeUs] for [durationUs] ([TIME_UNSET] for no end). */
data class CuesWithTiming(
    val cues: List<Cue>,
    val startTimeUs: Long,
    val durationUs: Long,
) {
    val endTimeUs: Long get() = if (startTimeUs == TIME_UNSET || durationUs == TIME_UNSET) TIME_UNSET else startTimeUs + durationUs
}

/** Media3's C.TIME_UNSET. */
const val TIME_UNSET = Long.MIN_VALUE + 1

/** Media3's `SubtitleParser`: a whole caption file in, its timed cues out. */
interface SubtitleParser {
    /** Media3's OutputOptions: cues before [startTimeUs] are dropped unless [outputAllCues]. */
    data class OutputOptions(
        val startTimeUs: Long = TIME_UNSET,
        val outputAllCues: Boolean = false,
    ) {
        companion object {
            fun allCues() = OutputOptions()
        }
    }

    fun parse(
        data: ByteArray,
        outputOptions: OutputOptions,
        output: (CuesWithTiming) -> Unit,
    )

    fun reset() {}

    /** Media3's `parseToLegacySubtitle`: every cue, laid out as a [Subtitle] timeline. */
    fun parseToLegacySubtitle(data: ByteArray): Subtitle {
        val list = mutableListOf<CuesWithTiming>()
        parse(data, OutputOptions.allCues()) { list += it }
        return CuesWithTimingSubtitle(list)
    }
}

/** Media3's `Subtitle`: a timeline of event times and the cues shown between them. */
interface Subtitle {
    /** The index of the first event after [timeUs], or -1 (Media3's C.INDEX_UNSET) when none. */
    fun getNextEventTimeIndex(timeUs: Long): Int

    fun getEventTimeCount(): Int

    fun getEventTime(index: Int): Long

    fun getCues(timeUs: Long): List<Cue>
}

/**
 * Media3's `CuesWithTimingSubtitle`, the timeline `parseToLegacySubtitle` builds: each cue list starts an event
 * and its end (when it has one) starts an empty one. A list that starts before the previous one ended truncates
 * it, as Media3 does ("unsupported overlapping cues"); parsers whose cues overlap merge them before this.
 */
class CuesWithTimingSubtitle(
    cuesWithTimingList: List<CuesWithTiming>,
) : Subtitle {
    private val eventCues: List<List<Cue>>
    private val eventTimesUs: LongArray

    init {
        if (cuesWithTimingList.size == 1) {
            val only = cuesWithTimingList.single()
            val startTimeUs = normalizeUnsetStartTimeToZero(only.startTimeUs)
            if (only.durationUs == TIME_UNSET) {
                eventCues = listOf(only.cues)
                eventTimesUs = longArrayOf(startTimeUs)
            } else {
                eventCues = listOf(only.cues, emptyList())
                eventTimesUs = longArrayOf(startTimeUs, startTimeUs + only.durationUs)
            }
        } else {
            val times = LongArray(cuesWithTimingList.size * 2) { Long.MAX_VALUE }
            val cues = ArrayList<List<Cue>>()
            var eventIndex = 0
            for (cuesWithTiming in cuesWithTimingList.sortedBy { normalizeUnsetStartTimeToZero(it.startTimeUs) }) {
                val startTimeUs = normalizeUnsetStartTimeToZero(cuesWithTiming.startTimeUs)
                val endTimeUs = startTimeUs + cuesWithTiming.durationUs
                if (eventIndex == 0 || times[eventIndex - 1] < startTimeUs) {
                    times[eventIndex++] = startTimeUs
                    cues += cuesWithTiming.cues
                } else if (times[eventIndex - 1] == startTimeUs && cues[eventIndex - 1].isEmpty()) {
                    cues[eventIndex - 1] = cuesWithTiming.cues
                } else {
                    Log.w(TAG, "Truncating unsupported overlapping cues.")
                    times[eventIndex - 1] = startTimeUs
                    cues[eventIndex - 1] = cuesWithTiming.cues
                }
                if (cuesWithTiming.durationUs != TIME_UNSET) {
                    times[eventIndex++] = endTimeUs
                    cues += emptyList<Cue>()
                }
            }
            eventCues = cues
            eventTimesUs = times
        }
    }

    override fun getNextEventTimeIndex(timeUs: Long): Int {
        // Media3's Util.binarySearchCeil(times, timeUs, inclusive = false, stayInBounds = false).
        val index = eventTimesUs.binarySearchCeil(timeUs)
        return if (index < eventCues.size) index else -1
    }

    override fun getEventTimeCount(): Int = eventCues.size

    override fun getEventTime(index: Int): Long = eventTimesUs[index]

    override fun getCues(timeUs: Long): List<Cue> {
        // Media3's Util.binarySearchFloor(times, timeUs, inclusive = true, stayInBounds = false).
        val index = eventTimesUs.binarySearchFloorInclusive(timeUs)
        return if (index == -1) emptyList() else eventCues[index]
    }

    private companion object {
        const val TAG = "CuesWithTimingSubtitle"

        fun normalizeUnsetStartTimeToZero(startTimeUs: Long): Long = if (startTimeUs == TIME_UNSET) 0 else startTimeUs

        /** The first index whose time is strictly greater than [value]; size when none. */
        fun LongArray.binarySearchCeil(value: Long): Int {
            var low = 0
            var high = size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (this[mid] <= value) low = mid + 1 else high = mid
            }
            return low
        }

        /** The first index whose time equals [value], else the last one below it; -1 when none (Media3's floor). */
        fun LongArray.binarySearchFloorInclusive(value: Long): Int {
            var low = 0
            var high = size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (this[mid] < value) low = mid + 1 else high = mid
            }
            return if (low < size && this[low] == value) low else low - 1
        }
    }
}
