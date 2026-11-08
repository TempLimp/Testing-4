package io.github.aedev.flow.player.renderer.subtitle

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import io.github.aedev.flow.utils.Log

/**
 * Parses YouTube srv3 captions into [Cue]s, keeping per-run styling (bold/italic/underline/colors) and window
 * position that the plain vtt conversion of the same track drops.
 *
 * Android's spans become Compose span styles on the cue's [AnnotatedString]; the layout fields are Media3's.
 */
class Srv3SubtitleParser : SubtitleParser {
    override fun parse(
        data: ByteArray,
        outputOptions: SubtitleParser.OutputOptions,
        output: (CuesWithTiming) -> Unit,
    ) {
        val xml = data.decodeToString()
        val paragraphs =
            try {
                parseSrv3Document(xml)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse srv3 document (${data.size}B), dropping captions", e)
                return
            }
        val cues = paragraphs.map { it.toCuesWithTiming() }
        if (outputOptions.startTimeUs == TIME_UNSET) {
            cues.forEach(output)
            return
        }
        val (fromStartTime, beforeStartTime) = cues.partition { it.startTimeUs >= outputOptions.startTimeUs }
        fromStartTime.forEach(output)
        if (outputOptions.outputAllCues) beforeStartTime.forEach(output)
    }

    companion object {
        private const val TAG = "Srv3SubtitleParser"
        const val MIME_TYPE = "application/x-youtube-srv3"
    }
}

private fun Srv3Paragraph.toCuesWithTiming(): CuesWithTiming {
    val builder = AnnotatedString.Builder()
    for (run in runs) {
        val start = builder.length
        builder.append(run.text)
        run.pen.applySpansTo(builder, start, builder.length)
    }
    val text = builder.toAnnotatedString().trimmedKeepingSpans()
    val startTimeUs = startMs * 1000L
    val durationUs = durationMs.coerceAtLeast(0L) * 1000L
    if (text.isBlank()) return CuesWithTiming(emptyList(), startTimeUs, durationUs)
    return CuesWithTiming(listOf(Cue(text = text).applyWindow(window)), startTimeUs, durationUs)
}

private fun Srv3Pen.applySpansTo(
    builder: AnnotatedString.Builder,
    start: Int,
    end: Int,
) {
    if (start >= end) return
    if (bold || italic) {
        builder.addStyle(
            SpanStyle(
                fontWeight = if (bold) FontWeight.Bold else null,
                fontStyle = if (italic) FontStyle.Italic else null,
            ),
            start,
            end,
        )
    }
    if (underline) builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
    foregroundColor?.toColorOrNull(foregroundOpacity)?.let { builder.addStyle(SpanStyle(color = it), start, end) }
    backgroundColor?.toColorOrNull(backgroundOpacity)?.let { builder.addStyle(SpanStyle(background = it), start, end) }
}

/** srv3 opacities run 0-254; Android's Color.parseColor reads the colour, its alpha is replaced. */
private fun String.toColorOrNull(opacity: Int?): Color? {
    val rgb = parseAndroidColor(this) ?: return null
    val alpha = (opacity ?: MAX_PEN_OPACITY).coerceIn(0, MAX_PEN_OPACITY) * 255 / MAX_PEN_OPACITY
    return Color(((alpha shl 24) or (rgb and 0x00FFFFFF)).toLong() and 0xFFFFFFFFL)
}

private const val MAX_PEN_OPACITY = 254

/** android.graphics.Color.parseColor's rules: `#RRGGBB`, `#AARRGGBB` or one of its colour names; null otherwise. */
internal fun parseAndroidColor(value: String): Int? {
    if (value.startsWith("#")) {
        val hex = value.substring(1)
        if (hex.length != 6 && hex.length != 8) return null
        val parsed = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) (parsed or 0xFF000000L).toInt() else parsed.toInt()
    }
    return ANDROID_COLOR_NAMES[value.lowercase()]
}

private val ANDROID_COLOR_NAMES =
    mapOf(
        "black" to 0xFF000000.toInt(),
        "darkgray" to 0xFF444444.toInt(),
        "gray" to 0xFF888888.toInt(),
        "lightgray" to 0xFFCCCCCC.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
        "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF00FF00.toInt(),
        "blue" to 0xFF0000FF.toInt(),
        "yellow" to 0xFFFFFF00.toInt(),
        "cyan" to 0xFF00FFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt(),
        "aqua" to 0xFF00FFFF.toInt(),
        "fuchsia" to 0xFFFF00FF.toInt(),
        "darkgrey" to 0xFF444444.toInt(),
        "grey" to 0xFF888888.toInt(),
        "lightgrey" to 0xFFCCCCCC.toInt(),
        "lime" to 0xFF00FF00.toInt(),
        "maroon" to 0xFF800000.toInt(),
        "navy" to 0xFF000080.toInt(),
        "olive" to 0xFF808000.toInt(),
        "purple" to 0xFF800080.toInt(),
        "silver" to 0xFFC0C0C0.toInt(),
        "teal" to 0xFF008080.toInt(),
    )

private fun Cue.applyWindow(window: Srv3Window): Cue {
    val aligned =
        copy(
            textAlignment =
                when (window.justify) {
                    Srv3Window.JUSTIFY_LEFT -> TextAlign.Start
                    Srv3Window.JUSTIFY_RIGHT -> TextAlign.End
                    else -> TextAlign.Center
                },
        )
    if (!window.positioned) return aligned
    val anchor = window.anchorPoint.coerceIn(0, 8)
    val row = anchor / 3 // 0 = top, 1 = middle, 2 = bottom
    val column = anchor % 3 // 0 = left, 1 = center, 2 = right
    return aligned.copy(
        line = window.verticalPercent.coerceIn(0f, 100f) / 100f,
        lineType = Cue.LINE_TYPE_FRACTION,
        lineAnchor = anchorType(row),
        position = window.horizontalPercent.coerceIn(0f, 100f) / 100f,
        positionAnchor = anchorType(column),
    )
}

private fun anchorType(index: Int): Int =
    when (index) {
        0 -> Cue.ANCHOR_TYPE_START
        1 -> Cue.ANCHOR_TYPE_MIDDLE
        else -> Cue.ANCHOR_TYPE_END
    }

private fun AnnotatedString.trimmedKeepingSpans(): AnnotatedString {
    var start = 0
    var end = length
    while (start < end && this[start].isWhitespace()) start++
    while (end > start && this[end - 1].isWhitespace()) end--
    return subSequence(start, end)
}
