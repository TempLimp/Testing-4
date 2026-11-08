package io.github.aedev.flow.player.renderer.subtitle

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.aedev.flow.utils.Log

/**
 * SubRip (.srt) captions, the sidecar files of downloads and device videos, as Media3's `SubripParser` reads them: each
 * block's timing, its lines joined as HTML (`b`, `i`, `u`, `font color` and entities, other tags dropped), and an
 * ASS `{\anN}` tag placing the cue on Media3's 3x3 grid; other `{...}` overrides are removed.
 */
class SubripParser : SubtitleParser {
    override fun parse(
        data: ByteArray,
        outputOptions: SubtitleParser.OutputOptions,
        output: (CuesWithTiming) -> Unit,
    ) {
        val lines = data.decodeToString().removePrefix("﻿").split(LINE_BREAK)
        val cues = mutableListOf<CuesWithTiming>()
        var index = 0
        while (index < lines.size) {
            while (index < lines.size && lines[index].isBlank()) index++
            if (index >= lines.size) break
            // The cue's number, then its timing; a block without a timing line is skipped.
            var timingLine = lines[index]
            if (!TIMING.containsMatchIn(timingLine)) {
                index++
                timingLine = lines.getOrNull(index) ?: break
            }
            val timing = TIMING.find(timingLine)
            if (timing == null) {
                Log.w(TAG, "Skipping invalid timing: $timingLine")
                while (index < lines.size && lines[index].isNotBlank()) index++
                continue
            }
            index++
            val text = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank()) text += lines[index++].trim()
            val start = parseTimecodeUs(timing.groupValues[1]) ?: continue
            val end = parseTimecodeUs(timing.groupValues[6]) ?: continue
            cues += CuesWithTiming(listOf(buildCue(text.joinToString("<br>"))), start, end - start)
        }
        if (outputOptions.startTimeUs == TIME_UNSET) {
            cues.forEach(output)
            return
        }
        val (fromStart, before) = cues.partition { it.startTimeUs >= outputOptions.startTimeUs }
        fromStart.forEach(output)
        if (outputOptions.outputAllCues) before.forEach(output)
    }

    private fun buildCue(markup: String): Cue {
        var alignment: String? = null
        val withoutTags =
            ASS_TAG.replace(markup) { match ->
                ALIGNMENT.find(match.value)?.let { alignment = it.value }
                ""
            }
        val text = fromHtml(withoutTags)
        val tag = alignment ?: return Cue(text)
        val positionAnchor =
            when (tag) {
                "{\\an1}", "{\\an4}", "{\\an7}" -> Cue.ANCHOR_TYPE_START
                "{\\an3}", "{\\an6}", "{\\an9}" -> Cue.ANCHOR_TYPE_END
                else -> Cue.ANCHOR_TYPE_MIDDLE
            }
        val lineAnchor =
            when (tag) {
                "{\\an1}", "{\\an2}", "{\\an3}" -> Cue.ANCHOR_TYPE_END
                "{\\an7}", "{\\an8}", "{\\an9}" -> Cue.ANCHOR_TYPE_START
                else -> Cue.ANCHOR_TYPE_MIDDLE
            }
        return Cue(
            text = text,
            position = fractionFor(positionAnchor),
            positionAnchor = positionAnchor,
            line = fractionFor(lineAnchor),
            lineType = Cue.LINE_TYPE_FRACTION,
            lineAnchor = lineAnchor,
        )
    }

    private fun fractionFor(anchor: Int): Float =
        when (anchor) {
            Cue.ANCHOR_TYPE_START -> START_FRACTION
            Cue.ANCHOR_TYPE_END -> END_FRACTION
            else -> MID_FRACTION
        }

    /** The part of Android's Html.fromHtml subtitles use. */
    private fun fromHtml(html: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val open = ArrayDeque<Pair<String, Int>>()
        val colors = ArrayDeque<Color?>()
        var pos = 0
        while (pos < html.length) {
            val c = html[pos]
            when {
                c == '<' -> {
                    val end = html.indexOf('>', pos + 1).let { if (it == -1) html.length else it }
                    val tag = html.substring(pos + 1, end).trim()
                    pos = end + 1
                    val closing = tag.startsWith("/")
                    val name = tag.removePrefix("/").substringBefore(' ').lowercase()
                    when {
                        name == "br" -> builder.append('\n')
                        closing -> {
                            val start = open.indexOfLast { it.first == name }.takeIf { it >= 0 }?.let { open.removeAt(it) } ?: continue
                            val style =
                                when (name) {
                                    "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
                                    "i", "em", "cite", "dfn" -> SpanStyle(fontStyle = FontStyle.Italic)
                                    "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
                                    "font" -> colors.removeLastOrNull()?.let { SpanStyle(color = it) }
                                    else -> null
                                }
                            style?.let { builder.addStyle(it, start.second, builder.length) }
                        }
                        name in STYLE_TAGS -> {
                            open.addLast(name to builder.length)
                            if (name == "font") colors.addLast(FONT_COLOR.find(tag)?.groupValues?.get(1)?.let(::parseAndroidColor)?.let { Color(it) })
                        }
                    }
                }
                c == '&' -> {
                    val semicolon = html.indexOf(';', pos + 1)
                    val entity = if (semicolon in (pos + 1)..(pos + 8)) html.substring(pos + 1, semicolon) else null
                    val decoded = entity?.let { ENTITIES[it] }
                    if (decoded != null) {
                        builder.append(decoded)
                        pos = semicolon + 1
                    } else {
                        builder.append(c)
                        pos++
                    }
                }
                else -> {
                    builder.append(c)
                    pos++
                }
            }
        }
        return builder.toAnnotatedString()
    }

    companion object {
        private const val TAG = "SubripParser"
        private const val START_FRACTION = 0.08f
        private const val END_FRACTION = 1 - START_FRACTION
        private const val MID_FRACTION = 0.5f
        private val LINE_BREAK = Regex("\r\n|\r|\n")
        private val TIMING = Regex("""\s*((?:(\d+):)?(\d+):(\d+)(?:[,.](\d+))?)\s*-->\s*((?:(\d+):)?(\d+):(\d+)(?:[,.](\d+))?)\s*""")
        private val ASS_TAG = Regex("""\{\\.*?\}""")
        private val ALIGNMENT = Regex("""\{\\an[1-9]\}""")
        private val FONT_COLOR = Regex("""color\s*=\s*["']?([#\w]+)""", RegexOption.IGNORE_CASE)
        private val STYLE_TAGS = setOf("b", "strong", "i", "em", "cite", "dfn", "u", "font")
        private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "nbsp" to " ", "quot" to "\"", "apos" to "'")

        /** `HH:MM:SS,mmm` (hours optional, `.` for `,` too) to microseconds. */
        internal fun parseTimecodeUs(value: String): Long? {
            val match = Regex("""(?:(\d+):)?(\d+):(\d+)(?:[,.](\d+))?""").matchEntire(value.trim()) ?: return null
            val (hours, minutes, seconds, fraction) = match.destructured
            val millis = fraction.padEnd(3, '0').take(3).toLongOrNull() ?: 0
            val total = (hours.toLongOrNull() ?: 0) * 3600 + minutes.toLong() * 60 + seconds.toLong()
            return (total * 1000 + millis) * 1000
        }
    }
}
