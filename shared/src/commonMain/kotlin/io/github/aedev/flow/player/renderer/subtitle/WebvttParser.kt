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
 * WebVTT captions as Media3's `WebvttParser` reads them, since upstream hands YouTube's `fmt=vtt` tracks (every
 * auto-generated one, and offline downloads) to Media3: the cue settings (`line`, `position`, `align`, `size`) laid
 * out by its rules, the `b`, `i`, `u`, `c`, `v` and `lang` tags, inline timestamps dropped, `&lt; &gt; &amp;
 * &nbsp;` entities, and the colour and weight properties of `::cue` STYLE blocks. Cues that overlap are merged into
 * one list per interval, and those with no `line` stacked up from the bottom in start order (lines -1, -2, ...), as
 * Media3's WebvttSubtitle does. Ruby text is dropped (its base text stays); CSS fonts and sizes are not applied,
 * since the player screen draws captions in the user's own size and font.
 */
class WebvttParser : SubtitleParser {
    override fun parse(
        data: ByteArray,
        outputOptions: SubtitleParser.OutputOptions,
        output: (CuesWithTiming) -> Unit,
    ) {
        val lines = data.decodeToString().removePrefix("\uFEFF").split(LINE_BREAK)
        var index = 0
        val header = lines.getOrNull(index++) ?: return
        if (!header.startsWith("WEBVTT")) {
            Log.w(TAG, "Not a WebVTT file: '${header.take(20)}'")
            return
        }
        // The header's own lines run to the first blank line.
        while (index < lines.size && lines[index].isNotEmpty()) index++

        val styles = mutableListOf<CssRule>()
        val cueInfos = mutableListOf<CueInfo>()
        while (index < lines.size) {
            while (index < lines.size && lines[index].isBlank()) index++
            if (index >= lines.size) break
            val block = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotEmpty()) block += lines[index++]
            val first = block.first()
            when {
                first.startsWith("NOTE") -> Unit
                first.startsWith("STYLE") -> styles += parseCss(block.drop(1).joinToString("\n"))
                first.startsWith("REGION") -> Unit
                else -> parseCue(block, styles)?.let { cueInfos += it }
            }
        }
        WebvttSubtitle(cueInfos).toCuesWithTiming(outputOptions, output)
    }

    internal class CueInfo(
        val cue: Cue,
        val startTimeUs: Long,
        val endTimeUs: Long,
    )

    private fun parseCue(
        block: List<String>,
        styles: List<CssRule>,
    ): CueInfo? {
        var timingIndex = 0
        var id: String? = null
        if (!TIMING.containsMatchIn(block[0])) {
            id = block[0]
            timingIndex = 1
        }
        val timing = block.getOrNull(timingIndex)?.let { TIMING.find(it) } ?: return null
        val startTimeUs = parseTimestampUs(timing.groupValues[1]) ?: return null
        val endTimeUs = parseTimestampUs(timing.groupValues[2]) ?: return null
        val settings = parseSettings(timing.groupValues[3])
        val text = block.drop(timingIndex + 1).joinToString("\n") { it.trim() }
        return CueInfo(
            cue = settings.toCue(parseCueText(id, text, styles)),
            startTimeUs = startTimeUs,
            endTimeUs = endTimeUs,
        )
    }

    private class Settings {
        var textAlignment = TEXT_ALIGNMENT_CENTER
        var line = Cue.DIMEN_UNSET
        var lineType = Cue.LINE_TYPE_NUMBER
        var lineAnchor = Cue.ANCHOR_TYPE_START
        var position = Cue.DIMEN_UNSET
        var positionAnchor = Cue.TYPE_UNSET
        var size = 1.0f

        fun toCue(text: AnnotatedString): Cue {
            val resolvedPosition = if (position != Cue.DIMEN_UNSET) position else derivePosition(textAlignment)
            val resolvedAnchor = if (positionAnchor != Cue.TYPE_UNSET) positionAnchor else derivePositionAnchor(textAlignment)
            return Cue(
                text = text,
                textAlignment =
                    when (textAlignment) {
                        TEXT_ALIGNMENT_START, TEXT_ALIGNMENT_LEFT -> TextAlign.Start
                        TEXT_ALIGNMENT_END, TEXT_ALIGNMENT_RIGHT -> TextAlign.End
                        else -> TextAlign.Center
                    },
                line = computeLine(line, lineType),
                lineType = lineType,
                lineAnchor = lineAnchor,
                position = resolvedPosition,
                positionAnchor = resolvedAnchor,
                size = minOf(size, deriveMaxSize(resolvedAnchor, resolvedPosition)),
            )
        }
    }

    private fun parseSettings(raw: String): Settings {
        val settings = Settings()
        for (setting in raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }) {
            val name = setting.substringBefore(':', "")
            val value = setting.substringAfter(':', "")
            if (name.isEmpty() || value.isEmpty()) continue
            try {
                when (name) {
                    "line" -> {
                        val (number, anchor) = value.splitAnchor()
                        anchor?.let { settings.lineAnchor = parseAnchor(it) }
                        if (number.endsWith("%")) {
                            settings.line = parsePercentage(number)
                            settings.lineType = Cue.LINE_TYPE_FRACTION
                        } else {
                            settings.line = number.toInt().toFloat()
                            settings.lineType = Cue.LINE_TYPE_NUMBER
                        }
                    }
                    "align" -> settings.textAlignment = parseTextAlignment(value)
                    "position" -> {
                        val (number, anchor) = value.splitAnchor()
                        anchor?.let { settings.positionAnchor = parsePositionAnchor(it) }
                        settings.position = parsePercentage(number)
                    }
                    "size" -> settings.size = parsePercentage(value)
                    "region", "vertical" -> Unit
                    else -> Log.w(TAG, "Unknown cue setting $name:$value")
                }
            } catch (e: NumberFormatException) {
                Log.w(TAG, "Skipping bad cue setting: $setting")
            }
        }
        return settings
    }

    private fun String.splitAnchor(): Pair<String, String?> =
        indexOf(',').let { comma -> if (comma == -1) this to null else substring(0, comma) to substring(comma + 1) }

    private fun parseAnchor(value: String): Int =
        when (value) {
            "start" -> Cue.ANCHOR_TYPE_START
            "center", "middle" -> Cue.ANCHOR_TYPE_MIDDLE
            "end" -> Cue.ANCHOR_TYPE_END
            else -> Cue.TYPE_UNSET.also { Log.w(TAG, "Invalid anchor value: $value") }
        }

    private fun parsePositionAnchor(value: String): Int =
        when (value) {
            "line-left", "start" -> Cue.ANCHOR_TYPE_START
            "center", "middle" -> Cue.ANCHOR_TYPE_MIDDLE
            "line-right", "end" -> Cue.ANCHOR_TYPE_END
            else -> Cue.TYPE_UNSET.also { Log.w(TAG, "Invalid anchor value: $value") }
        }

    private fun parseTextAlignment(value: String): Int =
        when (value) {
            "start" -> TEXT_ALIGNMENT_START
            "left" -> TEXT_ALIGNMENT_LEFT
            "center", "middle" -> TEXT_ALIGNMENT_CENTER
            "end" -> TEXT_ALIGNMENT_END
            "right" -> TEXT_ALIGNMENT_RIGHT
            else -> TEXT_ALIGNMENT_CENTER.also { Log.w(TAG, "Invalid alignment value: $value") }
        }

    // ----- cue text -----

    private class StartTag(
        val name: String,
        val position: Int,
        val voice: String,
        val classes: List<String>,
    )

    private fun parseCueText(
        id: String?,
        markup: String,
        styles: List<CssRule>,
    ): AnnotatedString {
        val text = StringBuilder()
        val spans = mutableListOf<AnnotatedString.Range<SpanStyle>>()
        val open = ArrayDeque<StartTag>()
        // Ruby annotations (<rt>) are left out; their base text stays.
        var rubyTextDepth = 0

        fun close(start: StartTag) {
            if (start.name == TAG_RUBY_TEXT) rubyTextDepth--
            spans += stylesFor(id, start.name, start.classes, start.voice, styles).map { AnnotatedString.Range(it, start.position, text.length) }
        }
        var pos = 0
        while (pos < markup.length) {
            when (val c = markup[pos]) {
                '<' -> {
                    val end = markup.indexOf('>', pos + 1).let { if (it == -1) markup.length else it }
                    val tag = markup.substring(pos + 1, end)
                    pos = end + 1
                    if (tag.isEmpty()) continue
                    if (tag.startsWith("/")) {
                        val name = tag.substring(1).substringBefore(' ').substringBefore('.')
                        if (name !in SUPPORTED_TAGS) continue
                        while (open.isNotEmpty()) {
                            val start = open.removeLast()
                            close(start)
                            if (start.name == name) break
                        }
                    } else if (!tag.first().isDigit()) {
                        val head = tag.substringBefore(' ').substringBefore('\t')
                        val name = head.substringBefore('.')
                        if (name !in SUPPORTED_TAGS) continue
                        val voice = if (name == TAG_VOICE) tag.substringAfter(' ', "").trim() else ""
                        val classes = head.split('.').drop(1).filter { it.isNotEmpty() }
                        open.addLast(StartTag(name, text.length, voice, classes))
                        if (name == TAG_RUBY_TEXT) rubyTextDepth++
                    }
                    // A tag that starts with a digit is an inline timestamp: dropped, as Media3 drops it.
                }
                '&' -> {
                    val semicolon = markup.indexOf(';', pos + 1)
                    val space = markup.indexOf(' ', pos + 1)
                    val end =
                        when {
                            semicolon == -1 -> -1
                            space == -1 -> semicolon
                            else -> minOf(semicolon, space)
                        }
                    if (end != -1 && end == semicolon) {
                        val entity = markup.substring(pos + 1, end)
                        val decoded =
                            when (entity) {
                                "lt" -> '<'
                                "gt" -> '>'
                                "nbsp" -> ' '
                                "amp" -> '&'
                                else -> null.also { Log.w(TAG, "ignoring unsupported entity: '&$entity;'") }
                            }
                        if (decoded != null && rubyTextDepth == 0) text.append(decoded)
                        pos = end + 1
                    } else {
                        if (rubyTextDepth == 0) text.append(c)
                        pos++
                    }
                }
                else -> {
                    if (rubyTextDepth == 0) text.append(c)
                    pos++
                }
            }
        }
        while (open.isNotEmpty()) close(open.removeLast())
        // Then the whole cue, as a tag with no name: Media3 applies it last, so its styles lie over the tags'.
        spans += stylesFor(id, "", emptyList(), "", styles).map { AnnotatedString.Range(it, 0, text.length) }
        return AnnotatedString(text.toString(), spanStyles = spans.filter { it.start < it.end })
    }

    /** A tag's own style, then the STYLE rules that match it, least specific first (Media3's order). */
    private fun stylesFor(
        id: String?,
        tag: String,
        classes: List<String>,
        voice: String,
        styles: List<CssRule>,
    ): List<SpanStyle> {
        val own =
            when (tag) {
                TAG_BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                TAG_ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                TAG_UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                else -> null
            }
        val css =
            styles
                .map { it to it.score(id, tag, classes, voice) }
                .filter { it.second > 0 }
                .sortedBy { it.second }
                .map { it.first.style }
        return listOfNotNull(own) + css
    }

    // ----- STYLE blocks -----

    internal class CssRule(
        private val targetId: String,
        private val targetTag: String,
        private val targetClasses: List<String>,
        private val targetVoice: String,
        val style: SpanStyle,
    ) {
        /** Media3's WebvttCssStyle.getSpecificityScore: 0 when the rule does not apply; [tag] "" is the whole cue. */
        fun score(
            id: String?,
            tag: String,
            classes: List<String>,
            voice: String,
        ): Int {
            if (targetId.isEmpty() && targetTag.isEmpty() && targetClasses.isEmpty() && targetVoice.isEmpty()) {
                return if (tag.isEmpty()) 1 else 0
            }
            var score = 0
            score = updateScore(score, targetId, id, 0x40000000)
            score = updateScore(score, targetTag, tag, 2)
            score = updateScore(score, targetVoice, voice, 4)
            return if (score == -1 || !classes.containsAll(targetClasses)) 0 else score + targetClasses.size * 4
        }

        private fun updateScore(
            current: Int,
            target: String,
            actual: String?,
            weight: Int,
        ): Int = if (target.isEmpty() || current == -1) current else if (target == actual) current + weight else -1
    }

    private fun parseCss(css: String): List<CssRule> =
        CSS_RULE.findAll(css).mapNotNull { match ->
            val selector = match.groupValues[1].trim()
            val style = parseDeclarations(match.groupValues[2]) ?: return@mapNotNull null
            parseSelector(selector, style)
        }.toList()

    private fun parseSelector(
        selector: String,
        style: SpanStyle,
    ): CssRule? {
        if (selector == "::cue") return CssRule("", "", emptyList(), "", style)
        val inner = CUE_SELECTOR.matchEntire(selector)?.groupValues?.get(1)?.trim() ?: return null
        if (inner.startsWith("#")) return CssRule(inner.substring(1), "", emptyList(), "", style)
        val voice = VOICE_SELECTOR.find(inner)?.groupValues?.get(1).orEmpty()
        val head = inner.substringBefore('[')
        val parts = head.split('.')
        return CssRule("", parts.first(), parts.drop(1).filter { it.isNotEmpty() }, voice, style)
    }

    private fun parseDeclarations(block: String): SpanStyle? {
        var style = SpanStyle()
        var any = false
        for (declaration in block.split(';')) {
            val name = declaration.substringBefore(':', "").trim().lowercase()
            val value = declaration.substringAfter(':', "").trim().removeSuffix("!important").trim()
            if (name.isEmpty() || value.isEmpty()) continue
            val next =
                when (name) {
                    "color" -> parseCssColor(value)?.let { style.copy(color = it) }
                    "background-color" -> parseCssColor(value)?.let { style.copy(background = it) }
                    "font-weight" -> if (value == "bold") style.copy(fontWeight = FontWeight.Bold) else null
                    "font-style" -> if (value == "italic") style.copy(fontStyle = FontStyle.Italic) else null
                    "text-decoration" -> if (value == "underline") style.copy(textDecoration = TextDecoration.Underline) else null
                    else -> null
                }
            if (next != null) {
                style = next
                any = true
            }
        }
        return style.takeIf { any }
    }

    companion object {
        private const val TAG = "WebvttParser"
        const val MIME_TYPE = "text/vtt"

        private val LINE_BREAK = Regex("\r\n|\r|\n")
        private val WHITESPACE = Regex("[ \t]+")
        private val TIMING = Regex("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$")
        private val CSS_RULE = Regex("([^{}]+)\\{([^}]*)\\}")
        private val CUE_SELECTOR = Regex("::cue\\((.*)\\)")
        private val VOICE_SELECTOR = Regex("\\[voice=\"?([^\"\\]]*)\"?\\]")

        private const val TAG_BOLD = "b"
        private const val TAG_ITALIC = "i"
        private const val TAG_UNDERLINE = "u"
        private const val TAG_CLASS = "c"
        private const val TAG_VOICE = "v"
        private const val TAG_LANG = "lang"
        private const val TAG_RUBY = "ruby"
        private const val TAG_RUBY_TEXT = "rt"
        private val SUPPORTED_TAGS = setOf(TAG_BOLD, TAG_ITALIC, TAG_UNDERLINE, TAG_CLASS, TAG_VOICE, TAG_LANG, TAG_RUBY, TAG_RUBY_TEXT)

        private const val TEXT_ALIGNMENT_START = 1
        private const val TEXT_ALIGNMENT_CENTER = 2
        private const val TEXT_ALIGNMENT_END = 3
        private const val TEXT_ALIGNMENT_LEFT = 4
        private const val TEXT_ALIGNMENT_RIGHT = 5
        private const val DEFAULT_POSITION = 0.5f

        /** `[hh:]mm:ss.ttt` to microseconds. */
        internal fun parseTimestampUs(value: String): Long? {
            val seconds = value.substringBefore('.')
            val millis = value.substringAfter('.', "").takeIf { it.length == 3 }?.toLongOrNull() ?: return null
            var total = 0L
            for (part in seconds.split(':')) total = total * 60 + (part.toLongOrNull() ?: return null)
            return (total * 1000 + millis) * 1000
        }

        private fun parsePercentage(value: String): Float {
            if (!value.endsWith("%")) throw NumberFormatException("Percentages must end with %")
            return value.dropLast(1).toFloat() / 100f
        }

        private fun derivePosition(textAlignment: Int): Float =
            when (textAlignment) {
                TEXT_ALIGNMENT_LEFT -> 0f
                TEXT_ALIGNMENT_RIGHT -> 1f
                else -> DEFAULT_POSITION
            }

        private fun derivePositionAnchor(textAlignment: Int): Int =
            when (textAlignment) {
                TEXT_ALIGNMENT_LEFT, TEXT_ALIGNMENT_START -> Cue.ANCHOR_TYPE_START
                TEXT_ALIGNMENT_RIGHT, TEXT_ALIGNMENT_END -> Cue.ANCHOR_TYPE_END
                else -> Cue.ANCHOR_TYPE_MIDDLE
            }

        private fun deriveMaxSize(
            positionAnchor: Int,
            position: Float,
        ): Float =
            when (positionAnchor) {
                Cue.ANCHOR_TYPE_START -> 1f - position
                Cue.ANCHOR_TYPE_END -> position
                else -> if (position <= 0.5f) position * 2 else (1f - position) * 2
            }

        /** Steps 1-3 of https://www.w3.org/TR/webvtt1/#cue-computed-line; WebvttSubtitle stacks the rest. */
        private fun computeLine(
            line: Float,
            lineType: Int,
        ): Float =
            when {
                line != Cue.DIMEN_UNSET && lineType == Cue.LINE_TYPE_FRACTION && (line < 0f || line > 1f) -> 1f
                line != Cue.DIMEN_UNSET -> line
                lineType == Cue.LINE_TYPE_FRACTION -> 1f
                else -> Cue.DIMEN_UNSET
            }

        /** CSS colours as Media3's ColorParser reads them: `#rgb`, `#rrggbb`, `#rrggbbaa`, `rgb()`, `rgba()`, names. */
        internal fun parseCssColor(value: String): Color? {
            val v = value.trim().lowercase().replace(" ", "")
            if (v.startsWith("#")) {
                val hex = v.substring(1)
                val n = hex.toLongOrNull(16) ?: return null
                return when (hex.length) {
                    3 -> {
                        val r = (n shr 8 and 0xF) * 17
                        val g = (n shr 4 and 0xF) * 17
                        val b = (n and 0xF) * 17
                        Color(0xFF000000L or (r shl 16) or (g shl 8) or b)
                    }
                    6 -> Color(0xFF000000L or n)
                    8 -> Color(((n and 0xFF) shl 24) or (n shr 8))
                    else -> null
                }
            }
            RGB.matchEntire(v)?.let { m ->
                val (r, g, b) = m.destructured
                return Color(r.toInt(), g.toInt(), b.toInt())
            }
            RGBA.matchEntire(v)?.let { m ->
                val (r, g, b, a) = m.destructured
                return Color(r.toInt(), g.toInt(), b.toInt(), (a.toFloat() * 255).toInt().coerceIn(0, 255))
            }
            // shortcut: CSS 2.1's named colours and transparent; Media3 knows all of CSS 3's. Add the rest if a track needs them.
            return CSS_NAMES[v]?.let { if (it == 0L) Color.Transparent else Color(0xFF000000L or it) }
        }

        private val RGB = Regex("rgb\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)")
        private val RGBA = Regex("rgba\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3}),([0-9.]+)\\)")
        private val CSS_NAMES =
            mapOf(
                "transparent" to 0L,
                "black" to 0x000000L,
                "silver" to 0xC0C0C0L,
                "gray" to 0x808080L,
                "white" to 0xFFFFFFL,
                "maroon" to 0x800000L,
                "red" to 0xFF0000L,
                "purple" to 0x800080L,
                "fuchsia" to 0xFF00FFL,
                "magenta" to 0xFF00FFL,
                "green" to 0x008000L,
                "lime" to 0x00FF00L,
                "olive" to 0x808000L,
                "yellow" to 0xFFFF00L,
                "navy" to 0x000080L,
                "blue" to 0x0000FFL,
                "teal" to 0x008080L,
                "aqua" to 0x00FFFFL,
                "cyan" to 0x00FFFFL,
                "orange" to 0xFFA500L,
            )
    }
}

/**
 * Media3's WebvttSubtitle: the cues showing at a time, those without a line stacked from the bottom in start
 * order. [toCuesWithTiming] is Media3's LegacySubtitleUtil: one non-overlapping list per interval between events.
 */
internal class WebvttSubtitle(
    private val cueInfos: List<WebvttParser.CueInfo>,
) {
    private val sortedTimesUs: LongArray =
        cueInfos.flatMap { listOf(it.startTimeUs, it.endTimeUs) }.distinct().sorted().toLongArray()

    fun getCues(timeUs: Long): List<Cue> {
        val current = mutableListOf<Cue>()
        val unsetLine = mutableListOf<WebvttParser.CueInfo>()
        for (info in cueInfos) {
            if (info.startTimeUs <= timeUs && timeUs < info.endTimeUs) {
                if (info.cue.line == Cue.DIMEN_UNSET) unsetLine += info else current += info.cue
            }
        }
        unsetLine.sortedBy { it.startTimeUs }.forEachIndexed { i, info ->
            current += info.cue.copy(line = (-1 - i).toFloat(), lineType = Cue.LINE_TYPE_NUMBER)
        }
        return current
    }

    fun toCuesWithTiming(
        outputOptions: SubtitleParser.OutputOptions,
        output: (CuesWithTiming) -> Unit,
    ) {
        val startFrom =
            if (outputOptions.startTimeUs == TIME_UNSET) 0 else sortedTimesUs.indexOfFirst { it >= outputOptions.startTimeUs }.let { if (it == -1) sortedTimesUs.size else it }
        // Media3 first emits the interval the start time falls in, from the start time, then the rest.
        if (outputOptions.startTimeUs != TIME_UNSET && startFrom > 0 && startFrom <= sortedTimesUs.size) {
            val cues = getCues(outputOptions.startTimeUs)
            val end = sortedTimesUs.getOrNull(startFrom)
            if (cues.isNotEmpty() && end != null && end > outputOptions.startTimeUs) {
                output(CuesWithTiming(cues, outputOptions.startTimeUs, end - outputOptions.startTimeUs))
            }
        }
        for (i in startFrom until sortedTimesUs.size - 1) outputInterval(i, output)
        if (outputOptions.outputAllCues && startFrom > 0) {
            for (i in 0 until startFrom - 1) outputInterval(i, output)
            if (outputOptions.startTimeUs != TIME_UNSET) {
                val from = sortedTimesUs[startFrom - 1]
                val cues = getCues(from)
                if (cues.isNotEmpty() && outputOptions.startTimeUs > from) {
                    output(CuesWithTiming(cues, from, outputOptions.startTimeUs - from))
                }
            }
        }
    }

    private fun outputInterval(
        i: Int,
        output: (CuesWithTiming) -> Unit,
    ) {
        val start = sortedTimesUs[i]
        val cues = getCues(start)
        if (cues.isEmpty()) return
        output(CuesWithTiming(cues, start, sortedTimesUs[i + 1] - start))
    }
}
