package io.github.aedev.flow.utils

import com.ibm.icu.text.CompactDecimalFormat
import com.ibm.icu.text.DecimalFormat
import com.ibm.icu.text.DecimalFormatSymbols
import com.ibm.icu.text.NumberFormat
import com.ibm.icu.util.ULocale
import com.ibm.icu.util.VersionInfo
import java.io.File
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import com.ibm.icu.text.PluralRules as IcuPluralRules

/**
 * CompactNumberData.kt is generated here from ICU4J, and the common formatter that reads it is checked
 * here against ICU4J's CompactDecimalFormat, which android.icu repackages: every locale ICU has, both
 * styles, and values across every magnitude, rounding edge and carry. Run with FLOW_REGENERATE=1 to
 * rewrite the data file after an ICU4J bump.
 */
class CompactNumberDataTest {
    @Test
    fun generatedDataMatchesIcu4j() {
        val generated = CompactDataGenerator.source()
        val file = File("src/commonMain/kotlin/io/github/aedev/flow/utils/CompactNumberData.kt")
        if (System.getenv("FLOW_REGENERATE") == "1") file.writeText(generated)
        assertEquals(generated, file.readText(), "CompactNumberData.kt is stale: rerun with FLOW_REGENERATE=1")
    }

    @Test
    fun formatsLikeIcuInEveryLocale() {
        val mismatches = mutableListOf<String>()
        var checked = 0
        for (tag in CompactDataGenerator.tags()) {
            for (long in listOf(false, true)) {
                val icu = CompactDecimalFormat.getInstance(ULocale.forLanguageTag(tag), CompactDataGenerator.style(long))
                for (value in SAMPLES) {
                    val expected = icu.format(value)
                    val actual = formatCompact(value, tag, long)
                    checked++
                    if (expected != actual) mismatches += "$tag ${if (long) "LONG" else "SHORT"} $value: ICU \"$expected\", ours \"$actual\""
                }
            }
        }
        assertEquals(emptyList(), mismatches.take(40), "${mismatches.size} of $checked differ")
    }

    @Test
    fun theAppsHostLanguagesResolveAsIcuDoes() {
        // The hl forms normalizeYouTubeHostLanguage sends and the default-locale tags iOS reports.
        for (tag in listOf("zh-TW", "zh-HK", "zh-CN", "zh-Hant-TW", "zh-Hans-CN", "pt-BR", "pt", "sr-Latn-RS", "en-IN", "es-419", "ar-EG", "fa-IR")) {
            for (value in listOf(1_234L, 56_789L, 1_234_567L, 9_876_543_210L)) {
                val icu = CompactDecimalFormat.getInstance(ULocale.forLanguageTag(tag), CompactDecimalFormat.CompactStyle.SHORT).format(value)
                assertEquals(icu, formatCompact(value, tag, long = false), "$tag $value")
            }
        }
    }

    private companion object {
        val SAMPLES: List<Long> =
            buildList {
                addAll(listOf(0L, 1L, 2L, 5L, 9L, 10L, 21L, 99L, 101L, 999L))
                var power = 1_000L
                while (power <= 1_000_000_000_000_000_000L) {
                    for (lead in listOf(1L, 2L, 3L, 5L, 11L, 15L, 21L, 99L, 101L, 125L, 999L)) {
                        if (lead <= Long.MAX_VALUE / power) add(lead * power)
                    }
                    // Half-even edges and carries: 1,050 / 1,150 / 9,950 / 999,500 and their neighbours.
                    for (offset in listOf(-1L, 0L, 1L)) {
                        add(power + power / 20 + offset)
                        add(power + 3 * power / 20 + offset)
                        if (power <= Long.MAX_VALUE / 10) add(10 * power - power / 20 + offset)
                        if (power <= Long.MAX_VALUE / 1_000) add(1_000 * power - power / 2 + offset)
                    }
                    if (power > Long.MAX_VALUE / 10) break
                    power *= 10
                }
                addAll(listOf(1_234L, 12_345L, 123_456L, 1_234_567L, 98_765_432L, Long.MAX_VALUE, -1_000L, -1_234L, -9_876_543L, Long.MIN_VALUE))
            }
    }
}

/** Builds CompactNumberData.kt from ICU4J: symbols, plural rules and SHORT/LONG patterns per locale. */
internal object CompactDataGenerator {
    private const val FIELD = "\u0001"
    private const val ITEM = "\u0002"
    private const val PAIR = "\u0003"
    private const val MAX_MAGNITUDE = 18

    fun style(long: Boolean) = if (long) CompactDecimalFormat.CompactStyle.LONG else CompactDecimalFormat.CompactStyle.SHORT

    /** Every locale ICU formats numbers for, without variants, plus each one's language and language-region form. */
    fun tags(): List<String> {
        val available = NumberFormat.getAvailableULocales().filter { it.variant.isEmpty() }
        val extras =
            available.flatMap { locale ->
                listOfNotNull(
                    locale.language.takeIf(String::isNotEmpty),
                    if (locale.language.isNotEmpty() && locale.country.isNotEmpty()) "${locale.language}-${locale.country}" else null,
                )
            }
        return (listOf("und") + available.map(ULocale::toLanguageTag) + extras).distinct().filter { it.isNotEmpty() }
    }

    private class LocaleData(
        val symbols: String,
        val plurals: String,
        val short: String,
        val long: String,
    ) {
        val key get() = listOf(symbols, plurals, short, long)
    }

    fun source(): String {
        val symbols = mutableListOf<String>()
        val plurals = mutableListOf<String>()
        val styles = mutableListOf<String>()
        val emitted = linkedMapOf<String, LocaleData>()
        val ordered = tags().sortedWith(compareBy({ if (it == "und") 0 else it.split('-').size }, { it }))
        for (tag in ordered) {
            val data = localeData(tag)
            val inherited = candidates(tag).drop(1).firstNotNullOfOrNull { emitted[it] } ?: emitted["und"]
            if (inherited == null || inherited.key != data.key) emitted[tag] = data
        }
        fun MutableList<String>.indexFor(value: String): Int = indexOf(value).takeIf { it >= 0 } ?: (size.also { add(value) })
        val locales =
            emitted.map { (tag, data) ->
                listOf(tag, symbols.indexFor(data.symbols), plurals.indexFor(data.plurals), styles.indexFor(data.short), styles.indexFor(data.long))
                    .joinToString(FIELD)
            }
        return buildString {
            appendLine("// Generated by CompactNumberDataTest (jvmTest) from ICU4J ${VersionInfo.ICU_VERSION}; do not edit by hand.")
            appendLine("// Fields are split by U+0001, lists by U+0002 and pairs by U+0003; CompactNumbers.kt reads them.")
            appendLine("// Regenerate: FLOW_REGENERATE=1 gradlew :shared:jvmTest --tests '*CompactNumberDataTest*'")
            appendLine()
            appendLine("package io.github.aedev.flow.utils")
            for ((name, values) in listOf("COMPACT_SYMBOLS" to symbols, "COMPACT_PLURALS" to plurals, "COMPACT_STYLES" to styles, "COMPACT_LOCALES" to locales)) {
                appendLine()
                appendLine("internal val $name: Array<String> =")
                appendLine("    arrayOf(")
                values.forEach { appendLine("        ${kotlinString(it)},") }
                appendLine("    )")
            }
        }
    }

    /** The lookup order CompactLocale.of uses, starting with [tag] itself. */
    private fun candidates(tag: String): List<String> {
        val locale = ULocale.forLanguageTag(tag)
        val language = locale.language
        val script = locale.script.takeIf(String::isNotEmpty)
        val region = locale.country.takeIf(String::isNotEmpty)
        return listOfNotNull(
            if (script != null && region != null) "$language-$script-$region" else null,
            region?.let { "$language-$it" },
            script?.let { "$language-$it" },
            language.takeIf(String::isNotEmpty),
            "und",
        ).distinct()
    }

    private fun localeData(tag: String): LocaleData {
        val locale = ULocale.forLanguageTag(tag)
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val short = CompactDecimalFormat.getInstance(locale, style(false))
        // CompactDecimalFormat reports no grouping of its own; it groups like the locale's decimal format.
        val decimal = NumberFormat.getInstance(locale) as DecimalFormat
        val primary = decimal.groupingSize
        val secondary = decimal.secondaryGroupingSize.takeIf { it > 0 } ?: primary
        val icuRules = IcuPluralRules.forLocale(locale)
        val keywords = icuRules.keywords.filter { it != "other" }.sorted()
        val ruleList = keywords.map { it to icuRules.getRules(it).substringBefore('@').trim() }
        val rules = PluralRules(ruleList)
        return LocaleData(
            symbols =
                listOf(
                    symbols.digitStrings.joinToString(ITEM),
                    symbols.decimalSeparatorString,
                    symbols.groupingSeparatorString,
                    symbols.minusSignString,
                    primary.toString(),
                    secondary.toString(),
                ).joinToString(FIELD),
            plurals = ruleList.joinToString(FIELD) { (keyword, rule) -> keyword + ITEM + rule },
            short = styleData(short, keywords + "other", rules),
            long = styleData(CompactDecimalFormat.getInstance(locale, style(true)), keywords + "other", rules),
        )
    }

    private fun styleData(
        format: CompactDecimalFormat,
        keywords: List<String>,
        rules: PluralRules,
    ): String {
        val magnitudes =
            (0..MAX_MAGNITUDE).map { magnitude ->
                // Italian's "mille" shows no number at all, so the digit count comes from the first lead that does.
                val shownDigits =
                    listOf(1L, 2L, 5L).firstNotNullOf { lead ->
                        integerDigits(format, BigInteger.TEN.pow(magnitude) * BigInteger.valueOf(lead))
                    }
                val multiplier = magnitude - (shownDigits - 1)
                val integerDigits = magnitude - multiplier + 1
                fun input(shown: String) = shown.toBigDecimal().movePointRight(multiplier).toBigIntegerExact()
                fun affixesOf(shown: String) = affixes(format, input(shown))
                val samples = keywords.mapNotNull { keyword -> sample(keyword, integerDigits, divided = multiplier > 0, rules)?.let { keyword to it } }
                val forms = samples.associate { (keyword, sample) -> keyword to affixesOf(sample) }
                // Most locales put the sign before the whole pattern; a few put it on the number ("elfu -1.2").
                val minus = format.decimalFormatSymbols.minusSignString
                val negativeForms =
                    samples.mapNotNull { (keyword, sample) ->
                        val negative = affixes(format, input(sample).negate())
                        negative.takeIf { it != minus + forms.getValue(keyword) }?.let { "$keyword-" to it }
                    }
                // CLDR may give exactly 1 its own pattern, as French's "mille" beside "1,2 millier".
                val exactOne =
                    if (integerDigits == 1) {
                        affixesOf("1").takeIf { it != forms[rules.select("1", "", exponent = 0)] }
                    } else {
                        null
                    }
                val entries =
                    (forms.toList() + negativeForms).map { (keyword, affixes) -> keyword + PAIR + affixes } +
                        listOfNotNull(exactOne?.let { "=1$PAIR$it" })
                (listOf(multiplier.toString()) + entries).joinToString(ITEM)
            }
        // Past the last magnitude the formatter reuses it, so identical trailing entries are dropped.
        val trimmed = magnitudes.toMutableList()
        while (trimmed.size > 1 && trimmed[trimmed.size - 1] == trimmed[trimmed.size - 2]) trimmed.removeAt(trimmed.lastIndex)
        return trimmed.joinToString(FIELD)
    }

    /** A shown number with [integerDigits] integer digits whose plural form is [keyword], as "12" or "1.5". */
    private fun sample(
        keyword: String,
        integerDigits: Int,
        divided: Boolean,
        rules: PluralRules,
    ): String? {
        val candidates =
            if (integerDigits == 1) {
                // A fraction shows only once the value is divided down: an undivided input is a whole number.
                (1..9).map(Int::toString) + if (divided) (1..9).flatMap { whole -> (1..9).map { "$whole.$it" } } else emptyList()
            } else {
                val first = BigInteger.TEN.pow(integerDigits - 1)
                (0 until 1_000).map { (first + BigInteger.valueOf(it.toLong())).toString() }
            }
        val matching = candidates.filter { shown -> rules.select(shown.substringBefore('.'), shown.substringAfter('.', ""), exponent = 0) == keyword }
        // Exactly 1 can carry its own pattern, so another number speaks for its plural form when one exists.
        return matching.firstOrNull { it != "1" } ?: matching.firstOrNull()
    }

    private fun integerDigits(
        format: CompactDecimalFormat,
        value: BigInteger,
    ): Int? {
        val (start, end) = numberSpan(format, value) ?: return null
        val text = format.format(value)
        return text.substring(start, end).substringBefore(format.decimalFormatSymbols.decimalSeparatorString).codePoints().filter(Character::isDigit).count().toInt()
    }

    /** "prefix PAIR suffix" around the number, or the whole text alone when the pattern shows no number. */
    private fun affixes(
        format: CompactDecimalFormat,
        value: BigInteger,
    ): String {
        val text = format.format(value)
        val (start, end) = numberSpan(format, value) ?: return text
        return text.substring(0, start) + PAIR + text.substring(end)
    }

    /** Where the digits, separators and decimal point sit in [format]'s output for [value]. */
    private fun numberSpan(
        format: CompactDecimalFormat,
        value: BigInteger,
    ): Pair<Int, Int>? {
        val numeric =
            setOf(
                NumberFormat.Field.INTEGER,
                NumberFormat.Field.FRACTION,
                NumberFormat.Field.DECIMAL_SEPARATOR,
                NumberFormat.Field.GROUPING_SEPARATOR,
            )
        val iterator = format.formatToCharacterIterator(value)
        var start = -1
        var end = -1
        var c = iterator.first()
        while (c != java.text.CharacterIterator.DONE) {
            if (iterator.attributes.keys.any { it in numeric }) {
                if (start < 0) start = iterator.index
                end = iterator.index + 1
            }
            c = iterator.next()
        }
        return if (start < 0) null else start to end
    }

    private fun kotlinString(value: String): String =
        buildString {
            append('"')
            for (char in value) {
                when {
                    char == '"' -> append("\\\"")
                    char == '\\' -> append("\\\\")
                    char == '$' -> append("\\$")
                    char.code in 0x20..0x7E -> append(char)
                    else -> append("\\u%04X".format(char.code))
                }
            }
            append('"')
        }
}
