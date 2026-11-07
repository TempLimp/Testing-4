@file:OptIn(ExperimentalXmlUtilApi::class)

package io.github.aedev.flow.data.lyrics

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.roundToLong

object TTMLParser {
    private const val TTML_PARAMETER_NS = "http://www.w3.org/ns/ttml#parameter"
    private const val TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata"

    data class ParsedLine(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val syllables: List<ParsedSyllable>,
        // Set for spans with role="x-bg".
        val isBackground: Boolean = false,
    )

    data class ParsedSyllable(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val hasTrailingSpace: Boolean,
    )

    private data class DomSpan(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val hasTrailingSpace: Boolean,
    )

    /**
     * Robust TTML parser based on Metrolist's implementation.
     *
     * Keeps word timing, background lines, agent tags, global offsets, nested spans,
     * and tracks whose <p> timing is only present on child spans.
     */
    fun parseTTMLToLyricsEntries(xmlData: String): List<LyricsEntry> {
        if (xmlData.isBlank()) return emptyList()

        return try {
            val root = XmlTree.parse(xmlData) ?: return emptyList()
            val offsetMs = readGlobalOffsetMs(root)
            val result = mutableListOf<LyricsEntry>()
            findChild(root, "body")?.let { walkDom(it, result, offsetMs, null) }
            result.sorted()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun readGlobalOffsetMs(root: XmlElement): Long {
        val audio =
            findChild(findChild(root, "head"), "metadata")
                ?.let { findChild(it, "audio") }
        return audio?.getAttribute("lyricOffset")?.toDoubleOrNull()?.let { (it * 1000).toLong() }
            ?: 0L
    }

    private fun walkDom(
        element: XmlElement,
        result: MutableList<LyricsEntry>,
        offsetMs: Long,
        parentAgent: String?,
    ) {
        var agent = parentAgent
        when (element.localName) {
            "div" -> {
                element.ttmlAttr("agent").takeIf { it.isNotBlank() }?.let { agent = it }
            }

            "p" -> {
                parseParagraph(element, result, offsetMs, agent)
                return
            }
        }

        element.children.forEach { child ->
            if (child is XmlElement) walkDom(child, result, offsetMs, agent)
        }
    }

    private fun parseParagraph(
        p: XmlElement,
        result: MutableList<LyricsEntry>,
        offsetMs: Long,
        divAgent: String?,
    ) {
        val beginAttr = p.timingAttr("begin").ifEmpty { findFirstSpanBegin(p).orEmpty() }
        if (beginAttr.isBlank()) return

        val startMs = parseTime(beginAttr) + offsetMs
        val agent = p.ttmlAttr("agent").ifEmpty { divAgent }
        val isBackground = p.ttmlAttr("role") == "x-bg"
        val spans = mutableListOf<DomSpan>()
        val backgroundLines = mutableListOf<LyricsEntry>()
        val translations = mutableListOf<String>()
        val romanizations = mutableListOf<String>()

        p.children.forEachIndexed { index, child ->
            if (child is XmlElement && child.localName == "span") {
                val next = p.children.getOrNull(index + 1)
                when (child.ttmlAttr("role")) {
                    "x-bg" -> {
                        if (isBackground) {
                            parseWordSpan(child, offsetMs, spans, next)
                        } else {
                            parseBackgroundSpan(child, startMs, offsetMs)?.let { backgroundLines += it }
                        }
                    }

                    "x-translation" -> {
                        child.textContent
                            .trim()
                            .takeIf { it.isNotBlank() }
                            ?.let { translations += it }
                    }

                    "x-roman" -> {
                        child.textContent
                            .trim()
                            .takeIf { it.isNotBlank() }
                            ?.let { romanizations += it }
                    }

                    else -> {
                        parseWordSpan(child, offsetMs, spans, next)
                    }
                }
            }
        }

        val words = mergeSpansIntoWords(spans)
        val text =
            if (words.isNotEmpty()) {
                buildLineText(words)
            } else {
                directText(p).trim()
            }

        if (text.isNotBlank()) {
            result +=
                LyricsEntry(
                    time = startMs,
                    text = text,
                    words = words.takeIf { it.isNotEmpty() },
                    agent = agent,
                    isBackground = isBackground,
                    translation = translations.joinToString("\n").ifBlank { null },
                    romanization = romanizations.joinToString("\n").ifBlank { null },
                )
        }
        result += backgroundLines
    }

    private fun parseBackgroundSpan(
        span: XmlElement,
        parentStartMs: Long,
        offsetMs: Long,
    ): LyricsEntry? {
        val startMs =
            span
                .timingAttr("begin")
                .takeIf { it.isNotBlank() }
                ?.let { parseTime(it) + offsetMs }
                ?: parentStartMs
        val spans = mutableListOf<DomSpan>()
        val translations = mutableListOf<String>()
        val romanizations = mutableListOf<String>()

        var hasChildSpans = false
        span.children.forEachIndexed { index, child ->
            if (child is XmlElement && child.localName == "span") {
                hasChildSpans = true
                val role = child.ttmlAttr("role")
                val content =
                    child.textContent
                        .trim()
                        .takeIf { it.isNotBlank() }
                when (role) {
                    "x-translation" -> content?.let { translations += it }
                    "x-roman" -> content?.let { romanizations += it }
                    else -> parseWordSpan(child, offsetMs, spans, span.children.getOrNull(index + 1))
                }
            }
        }

        val words = mergeSpansIntoWords(spans)
        val text =
            when {
                words.isNotEmpty() -> buildLineText(words)
                !hasChildSpans -> span.textContent.trim()
                else -> directText(span).trim()
            }
        if (text.isBlank()) return null

        return LyricsEntry(
            time = startMs,
            text = text,
            words = words.takeIf { it.isNotEmpty() },
            agent = "bg",
            isBackground = true,
            translation = translations.joinToString("\n").ifBlank { null },
            romanization = romanizations.joinToString("\n").ifBlank { null },
        )
    }

    private fun parseWordSpan(
        span: XmlElement,
        offsetMs: Long,
        spans: MutableList<DomSpan>,
        next: XmlNode?,
    ) {
        val begin = span.timingAttr("begin")
        val end = span.timingAttr("end")
        val text = span.textContent
        if (begin.isBlank() || end.isBlank()) return

        val hasTrailingSpace =
            (text.lastOrNull()?.isWhitespace() == true) ||
                (next is XmlText && next.text.firstOrNull()?.isWhitespace() == true)
        spans +=
            DomSpan(
                text = text,
                startTimeMs = parseTime(begin) + offsetMs,
                endTimeMs = parseTime(end) + offsetMs,
                hasTrailingSpace = hasTrailingSpace,
            )
    }

    private fun mergeSpansIntoWords(spans: List<DomSpan>): List<WordTimestamp> {
        if (spans.isEmpty()) return emptyList()

        val words = mutableListOf<WordTimestamp>()
        var text = StringBuilder(spans.first().text)
        var start = spans.first().startTimeMs
        var end = spans.first().endTimeMs

        for (i in 1 until spans.size) {
            val previous = spans[i - 1]
            val current = spans[i]
            if (previous.hasTrailingSpace && !previous.text.endsWith('-')) {
                text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                    words += WordTimestamp(it, start, end)
                }
                text = StringBuilder(current.text)
                start = current.startTimeMs
            } else {
                text.append(current.text)
            }
            end = current.endTimeMs
        }

        text.toString().trim().takeIf { it.isNotEmpty() }?.let {
            words += WordTimestamp(it, start, end)
        }
        return words
    }

    private fun buildLineText(words: List<WordTimestamp>): String =
        buildString {
            words.forEachIndexed { index, word ->
                append(word.text)
                if (!word.text.endsWith('-') && index < words.lastIndex) append(' ')
            }
        }.trim()

    private fun directText(element: XmlElement): String {
        val sb = StringBuilder()
        element.children.forEach { child ->
            if (child is XmlText) {
                sb.append(child.text)
            } else if (child is XmlElement) {
                val role = child.ttmlAttr("role")
                if (child.localName == "span" && role != "x-bg" && role != "x-translation" && role != "x-roman") {
                    sb.append(child.textContent)
                }
            }
        }
        return sb.toString()
    }

    private fun findFirstSpanBegin(p: XmlElement): String? {
        var best: String? = null
        var bestMs = Long.MAX_VALUE
        p.children.forEach { child ->
            if (child is XmlElement && child.localName == "span") {
                val begin = child.timingAttr("begin")
                if (begin.isNotBlank()) {
                    val ms = parseTime(begin)
                    if (ms < bestMs) {
                        bestMs = ms
                        best = begin
                    }
                }
            }
        }
        return best
    }

    private fun findChild(
        parent: XmlElement?,
        localName: String,
    ): XmlElement? = parent?.children?.firstOrNull { it is XmlElement && it.localName == localName } as XmlElement?

    private fun XmlElement.ttmlAttr(localName: String): String {
        getAttribute("ttm:$localName").takeIf { it.isNotEmpty() }?.let { return it }
        getAttribute(localName).takeIf { it.isNotEmpty() }?.let { return it }
        return getAttributeNS(TTML_METADATA_NS, localName)
    }

    private fun XmlElement.timingAttr(localName: String): String {
        getAttribute(localName).takeIf { it.isNotEmpty() }?.let { return it }
        return getAttributeNS(TTML_PARAMETER_NS, localName)
    }

    fun toLRC(lines: List<ParsedLine>): String {
        return buildString {
            lines.forEach { line ->
                val finalWords = mutableListOf<ParsedSyllable>()
                var currentWordText = ""
                var currentWordStart = -1L
                var currentWordEnd = -1L

                line.syllables.forEach { syl ->
                    if (currentWordStart == -1L) {
                        currentWordStart = syl.startTimeMs
                    }
                    currentWordText += syl.text
                    currentWordEnd = syl.endTimeMs

                    if (syl.hasTrailingSpace) {
                        finalWords.add(
                            ParsedSyllable(
                                currentWordText,
                                currentWordStart,
                                currentWordEnd,
                                true,
                            ),
                        )
                        currentWordText = ""
                        currentWordStart = -1L
                    }
                }

                if (currentWordText.isNotEmpty()) {
                    finalWords.add(
                        ParsedSyllable(
                            currentWordText,
                            currentWordStart,
                            currentWordEnd,
                            false,
                        ),
                    )
                }

                val fullLineText = finalWords.joinToString(separator = " ") { it.text }.trim()
                if (fullLineText.isEmpty()) return@forEach

                val mm = line.startTimeMs / 60000
                val ss = (line.startTimeMs % 60000) / 1000
                val ms = line.startTimeMs % 1000
                val lineTimeStr = "[${mm.zeroPad(2)}:${ss.zeroPad(2)}.${ms.zeroPad(3)}]"

                appendLine("$lineTimeStr $fullLineText")

                val wordsStr =
                    finalWords.joinToString("|") { w ->
                        val wStartSec = w.startTimeMs / 1000.0
                        val wEndSec = w.endTimeMs / 1000.0
                        "${w.text}:$wStartSec:$wEndSec"
                    }
                appendLine("<$wordsStr>")
            }
        }
    }

    fun parseTTML(xmlData: String): List<ParsedLine> {
        val result = mutableListOf<ParsedLine>()
        if (xmlData.isBlank()) return result

        try {
            // android.util.Xml.newPullParser(): namespace-aware, so names are local names.
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            parser.setInput(xmlData)

            var eventType = parser.eventType
            var inBody = false
            var inDiv = false

            var currentLine: MutableList<ParsedSyllable>? = null
            var currentLineStart = -1L
            var currentLineEnd = -1L
            var currentLineIsBackground = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "body" -> {
                                inBody = true
                            }

                            "div" -> {
                                if (inBody) inDiv = true
                            }

                            "p" -> {
                                if (inBody && inDiv) {
                                    currentLine = mutableListOf()
                                    val bgnStr = parser.getAttributeValue(null, "begin")
                                    val endStr = parser.getAttributeValue(null, "end")
                                    val roleStr = parser.getAttributeValue(null, "role")

                                    currentLineStart = parseTime(bgnStr)
                                    currentLineEnd = parseTime(endStr)
                                    currentLineIsBackground = (roleStr == "x-bg")
                                }
                            }

                            "span" -> {
                                if (currentLine != null) {
                                    val bgnStr = parser.getAttributeValue(null, "begin")
                                    val endStr = parser.getAttributeValue(null, "end")
                                    val text = parser.nextText() // typically <span ...>text</span>

                                    var hasSpace = false
                                    var cleanText = text
                                    if (text.endsWith(" ")) {
                                        hasSpace = true
                                        cleanText = text.dropLast(1)
                                    }

                                    if (cleanText.isNotEmpty() || hasSpace) {
                                        val sStart = parseTime(bgnStr)
                                        val sEnd = parseTime(endStr)
                                        currentLine.add(
                                            ParsedSyllable(
                                                cleanText,
                                                sStart,
                                                sEnd,
                                                hasTrailingSpace = hasSpace,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "body" -> {
                                inBody = false
                            }

                            "div" -> {
                                inDiv = false
                            }

                            "p" -> {
                                if (currentLine != null) {
                                    if (currentLine.isNotEmpty()) {
                                        result.add(
                                            ParsedLine(
                                                startTimeMs = currentLineStart,
                                                endTimeMs = currentLineEnd,
                                                syllables = currentLine,
                                                isBackground = currentLineIsBackground,
                                            ),
                                        )
                                    }
                                    currentLine = null
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * TTML offset times ("9.446s", "9446ms"), clock times ("0:01:02.345") and the shapes Apple
     * Music TTML actually uses: bare seconds ("9.446") and minutes with seconds ("1:02.345").
     */
    private fun parseTime(timeStr: String?): Long {
        val time = timeStr?.trim().orEmpty()
        if (time.isEmpty()) return 0L
        val seconds =
            when {
                time.endsWith("ms") -> {
                    time.removeSuffix("ms").toDoubleOrNull()?.div(1000)
                }

                time.endsWith("s") -> {
                    time.removeSuffix("s").toDoubleOrNull()
                }

                ':' in time -> {
                    val parts = time.split(':').map { it.toDoubleOrNull() ?: return 0L }
                    if (parts.size > 3) return 0L
                    parts.fold(0.0) { total, part -> total * 60 + part }
                }

                else -> {
                    time.toDoubleOrNull()
                }
            } ?: return 0L
        return (seconds * 1000).roundToLong()
    }
}

/**
 * The part of a W3C DOM the TTML walk reads (children in order, text, attributes by qualified name or namespace),
 * built with xmlutil's KtXmlReader, the kXML2 port behind the S07 pull-parser shim. Upstream builds a DOM with
 * javax.xml's DocumentBuilder, external entities off; KtXmlReader never loads external entities or DTDs.
 */
internal sealed interface XmlNode

internal class XmlText(
    val text: String,
) : XmlNode

internal class XmlElement(
    val localName: String,
    private val attributes: List<Attribute>,
) : XmlNode {
    class Attribute(
        val namespace: String,
        val qualifiedName: String,
        val localName: String,
        val value: String,
    )

    val children = mutableListOf<XmlNode>()

    val textContent: String
        get() =
            buildString {
                fun collect(node: XmlNode) {
                    when (node) {
                        is XmlText -> append(node.text)
                        is XmlElement -> node.children.forEach(::collect)
                    }
                }
                children.forEach(::collect)
            }

    /** DOM's getAttribute: by qualified name, or "" when absent. */
    fun getAttribute(name: String): String = attributes.firstOrNull { it.qualifiedName == name }?.value.orEmpty()

    /** DOM's getAttributeNS: by namespace and local name, or "" when absent. */
    fun getAttributeNS(
        namespace: String,
        localName: String,
    ): String = attributes.firstOrNull { it.namespace == namespace && it.localName == localName }?.value.orEmpty()
}

internal object XmlTree {
    /** The document element, or null for a document without one. Malformed XML throws. */
    fun parse(xml: String): XmlElement? {
        val reader = KtXmlReader(xml, relaxed = false, expandEntities = true)
        val stack = ArrayDeque<XmlElement>()
        var root: XmlElement? = null
        val text = StringBuilder()

        fun flushText() {
            if (text.isNotEmpty()) {
                stack.lastOrNull()?.children?.add(XmlText(text.toString()))
                text.clear()
            }
        }

        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    flushText()
                    val element =
                        XmlElement(
                            localName = reader.localName,
                            attributes =
                                List(reader.attributeCount) { i ->
                                    val prefix = reader.getAttributePrefix(i)
                                    val local = reader.getAttributeLocalName(i)
                                    XmlElement.Attribute(
                                        namespace = reader.getAttributeNamespace(i),
                                        qualifiedName = if (prefix.isEmpty()) local else "$prefix:$local",
                                        localName = local,
                                        value = reader.getAttributeValue(i),
                                    )
                                },
                        )
                    stack.lastOrNull()?.children?.add(element) ?: run { root = element }
                    stack.addLast(element)
                }

                EventType.END_ELEMENT -> {
                    flushText()
                    stack.removeLast()
                }

                EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE -> {
                    if (stack.isNotEmpty()) text.append(reader.text)
                }

                else -> Unit
            }
        }
        return root
    }
}
