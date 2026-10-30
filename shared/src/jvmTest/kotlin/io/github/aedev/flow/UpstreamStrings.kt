package io.github.aedev.flow

import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Upstream's res dir; jvm tests run from shared/, and CI checks the submodule out for this job. */
internal val upstreamRes = File("../upstream/app/src/main/res")

/** Every upstream `values*` dir that has strings, default first. */
internal fun upstreamStringDirs(): List<File> =
    upstreamRes
        .listFiles { dir -> dir.name.startsWith("values") && File(dir, "strings.xml").isFile }
        .orEmpty()
        .sortedBy { if (it.name == "values") "" else it.name }

/** One `strings.xml` as aapt2 compiles it: strings, arrays (`name[]`) and plural items (`name#quantity`). */
internal fun loadUpstreamStrings(file: File): Map<String, Any> {
    val root =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    val entries = linkedMapOf<String, Any>()
    root.elements().forEach { element ->
        val name = element.getAttribute("name")
        when (element.tagName) {
            "string" -> entries[name] = aapt2String(element.text())
            "string-array" -> entries["$name[]"] = element.elements().map { aapt2String(it.text()) }
            "plurals" -> element.elements().forEach { entries["$name#${it.getAttribute("quantity")}"] = aapt2String(it.text()) }
            else -> error("${file.path}: unexpected <${element.tagName}>")
        }
    }
    return entries
}

private fun Element.elements(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

private fun Element.text(): String {
    check(elements().isEmpty()) { "<$tagName name=${getAttribute("name")}> has markup" }
    return textContent
}

private const val XML_SPACE = " \t\n\r\u000B\u000C"

/**
 * aapt2's ResourceParser::FlattenXmlSubtree and StringBuilder: trim the raw text, collapse whitespace
 * outside double quotes, drop the quotes, resolve backslash escapes. Written apart from
 * tools/import_resources.py so the two check each other.
 */
internal fun aapt2String(raw: String): String {
    val text = raw.trim { it in XML_SPACE }
    val out = StringBuilder()
    var quoted = false
    var lastSpace = false
    var i = 0
    while (i < text.length) {
        val c = text[i++]
        if (!quoted && c in XML_SPACE) {
            if (!lastSpace) out.append(' ')
            lastSpace = true
            continue
        }
        lastSpace = false
        when (c) {
            '\\' -> {
                if (i == text.length) break
                when (val escaped = text[i++]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    'u' -> {
                        out.append(text.substring(i, i + 4).toInt(16).toChar())
                        i += 4
                    }
                    else -> out.append(escaped)
                }
            }
            '"' -> quoted = !quoted
            '\'' -> {
                check(quoted) { "unescaped apostrophe in $raw" }
                out.append(c)
            }
            else -> out.append(c)
        }
    }
    return out.toString()
}

/**
 * The device locale that selects an upstream `values-*` dir. CMP's environment on jvm follows
 * Locale.getDefault(); Android's legacy `in` is `id`, as iOS and JDK 17+ report Indonesian.
 */
internal fun localeFor(dir: String): Locale =
    dir.removePrefix("values").removePrefix("-").let { qualifiers ->
        when {
            qualifiers.isEmpty() -> Locale.ENGLISH
            qualifiers.startsWith("b+") -> qualifiers.removePrefix("b+").split('+').let { Locale.Builder().setLanguage(it[0]).setScript(it[1]).build() }
            else ->
                qualifiers.split('-').let { parts ->
                    Locale.Builder().setLanguage(if (parts[0] == "in") "id" else parts[0]).setRegion(parts.getOrNull(1)?.removePrefix("r").orEmpty()).build()
                }
        }
    }

/** Runs [block] with the jvm default locale set to [locale]. */
internal inline fun <T> withLocale(
    locale: Locale,
    block: () -> T,
): T {
    val previous = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        return block()
    } finally {
        Locale.setDefault(previous)
    }
}
