package io.github.aedev.flow.utils

/**
 * ICU's CompactDecimalFormat ("1.2K", "12 Mio.", "1,2 thousand"), which upstream reads from android.icu.
 * Foundation offers no compact number style to Kotlin, so this runs ICU's algorithm over CLDR data taken
 * from ICU4J (CompactNumberData.kt, generated and checked against ICU4J by CompactNumberDataTest across
 * every locale ICU has).
 *
 * ICU's steps: divide by the power of ten the magnitude's pattern names; round half-even to an integer,
 * or to two significant digits when one integer digit is left; when rounding carries into the next
 * magnitude, divide again by that magnitude's power; then pick the pattern by the plural form of the
 * shown number. Groups need two digits before the first separator, as ICU's MIN2 strategy has it.
 */
internal fun formatCompact(
    value: Long,
    languageTag: String,
    long: Boolean,
): String {
    val locale = CompactLocale.of(languageTag)
    val style = if (long) locale.long else locale.short
    val negative = value < 0
    // Long.MIN_VALUE has no positive twin; its digits are written out instead.
    val digits = if (value == Long.MIN_VALUE) "9223372036854775808" else kotlin.math.abs(value).toString()
    var multiplier = style.multiplier(digits.length - 1)
    var shown = roundCompact(digits, multiplier)
    if (shown.integer.length > digits.length - multiplier) {
        // The rounding carried, as 999,999 → "1000K": ICU moves up to the next magnitude's pattern.
        val next = style.multiplier(digits.length)
        if (next != multiplier) {
            shown = roundCompact(shown.integer + shown.fraction, next - multiplier + shown.fraction.length)
            multiplier = next
        }
    }
    val magnitude = shown.integer.length - 1 + multiplier
    // ICU 77 picks the form with the compact exponent left at 0, so Catalan's "many" (e != 0..5) never applies.
    val plural = locale.plural(shown, exponent = 0)
    // Only 1 itself takes CLDR's exact-1 pattern ("mille"); -1,000 reads "-1 millier" in French.
    val exactlyOne = !negative && shown.integer == "1" && shown.fraction.isEmpty()
    // The sign leads the pattern, except where CLDR puts it on the number (Swahili's "elfu -1.2").
    val (prefix, suffix) =
        style.negativeAffixes(magnitude, plural).takeIf { negative }
            ?: style.affixes(magnitude, plural, exactlyOne).let { (prefix, suffix) -> (if (negative) locale.minus + prefix else prefix) to suffix }
    // A null suffix is a pattern with no number in it, like Italian's "mille" for one thousand.
    if (suffix == null) return prefix
    return prefix + locale.render(shown) + suffix
}

/** A number as shown: integer digits and the fraction digits that stay visible. */
internal class ShownNumber(
    val integer: String,
    val fraction: String,
)

/**
 * [digits] divided by 10^[shift], rounded half-even to an integer, or to two significant digits when
 * a single integer digit remains, with trailing fraction zeros dropped.
 */
private fun roundCompact(
    digits: String,
    shift: Int,
): ShownNumber {
    // At least one integer digit is left: the shift never exceeds the value's magnitude.
    val integerLength = digits.length - shift
    val keep = maxOf(integerLength, 2)
    val kept = digits.take(keep).padEnd(keep, '0')
    val rest = digits.drop(keep)
    val roundUp =
        when {
            rest.isEmpty() || rest[0] < '5' -> false
            rest[0] > '5' || rest.drop(1).any { it != '0' } -> true
            else -> (kept.last() - '0') % 2 == 1
        }
    val rounded = if (roundUp) increment(kept) else kept
    val integerEnd = integerLength + (rounded.length - keep)
    return ShownNumber(rounded.take(integerEnd), rounded.drop(integerEnd).trimEnd('0'))
}

private fun increment(digits: String): String {
    val chars = digits.toCharArray()
    var i = chars.lastIndex
    while (i >= 0) {
        if (chars[i] == '9') {
            chars[i] = '0'
            i--
        } else {
            chars[i] = chars[i] + 1
            return chars.concatToString()
        }
    }
    return "1" + chars.concatToString()
}

/** One locale's compact data: digits and symbols, plural rules, and the SHORT and LONG patterns. */
internal class CompactLocale(
    private val digits: List<String>,
    private val decimal: String,
    private val group: String,
    val minus: String,
    private val primaryGroup: Int,
    private val secondaryGroup: Int,
    private val rules: PluralRules,
    val short: CompactStyle,
    val long: CompactStyle,
) {
    fun plural(
        shown: ShownNumber,
        exponent: Int,
    ): String = rules.select(shown.integer, shown.fraction, exponent)

    fun render(shown: ShownNumber): String {
        val integer = StringBuilder()
        val length = shown.integer.length
        // MIN2: a separator only once at least two digits stand before the first one.
        val grouped = primaryGroup > 0 && length - primaryGroup >= 2
        for ((index, char) in shown.integer.withIndex()) {
            val position = length - index
            if (grouped && index > 0) {
                val past = position - primaryGroup
                if (past == 0 || (past > 0 && past % secondaryGroup == 0)) integer.append(group)
            }
            integer.append(digits[char - '0'])
        }
        if (shown.fraction.isEmpty()) return integer.toString()
        return integer.toString() + decimal + shown.fraction.map { digits[it - '0'] }.joinToString("")
    }

    companion object {
        private val cache = io.ktor.util.collections.ConcurrentMap<String, CompactLocale>()

        /** The data ICU would use for [languageTag]: the tag, then without region, script or both, then root. */
        fun of(languageTag: String): CompactLocale =
            cache.computeIfAbsent(languageTag) {
                val parts = languageTag.replace('_', '-').split('-').filter(String::isNotEmpty)
                val language = parts.firstOrNull()?.lowercase().orEmpty()
                val script = parts.drop(1).firstOrNull { it.length == 4 && it.all(Char::isLetter) }?.let { it.lowercase().replaceFirstChar(Char::uppercase) }
                val region = parts.drop(1).firstOrNull { (it.length == 2 && it.all(Char::isLetter)) || (it.length == 3 && it.all(Char::isDigit)) }?.uppercase()
                val candidates =
                    listOfNotNull(
                        if (script != null && region != null) "$language-$script-$region" else null,
                        region?.let { "$language-$it" },
                        script?.let { "$language-$it" },
                        language,
                    )
                candidates.firstNotNullOfOrNull(::parse) ?: parse("und")!!
            }

        private val index: Map<String, String> by lazy { COMPACT_LOCALES.associate { it.substringBefore(FIELD) to it.substringAfter(FIELD) } }

        // The generated tables' layout: fields split by U+0001, lists by U+0002, pairs by U+0003.
        private const val FIELD = '\u0001'
        private const val ITEM = '\u0002'
        private const val PAIR = '\u0003'

        private fun parse(tag: String): CompactLocale? {
            val (symbols, plurals, short, long) = (index[tag] ?: return null).split(FIELD).map(String::toInt)
            val (digits, decimal, group, minus, primary, secondary) = COMPACT_SYMBOLS[symbols].split(FIELD)
            val rules =
                COMPACT_PLURALS[plurals]
                    .split(FIELD)
                    .filter(String::isNotEmpty)
                    .map { it.substringBefore(ITEM) to it.substringAfter(ITEM) }
            return CompactLocale(
                digits = digits.split(ITEM),
                decimal = decimal,
                group = group,
                minus = minus,
                primaryGroup = primary.toInt(),
                secondaryGroup = secondary.toInt(),
                rules = PluralRules(rules),
                short = style(COMPACT_STYLES[short]),
                long = style(COMPACT_STYLES[long]),
            )
        }

        private fun style(encoded: String): CompactStyle {
            val magnitudes = encoded.split(FIELD).map { it.split(ITEM) }
            return CompactStyle(
                multipliers = magnitudes.map { it[0].toInt() }.toIntArray(),
                patterns =
                    magnitudes.map { magnitude ->
                        magnitude.drop(1).associate { form ->
                            val parts = form.split(PAIR)
                            parts[0] to (parts[1] to parts.getOrNull(2))
                        }
                    },
            )
        }

        private operator fun <T> List<T>.component6(): T = this[5]
    }
}

/** Patterns by magnitude: how many powers of ten to divide by, and the affixes for each plural form. */
internal class CompactStyle(
    private val multipliers: IntArray,
    private val patterns: List<Map<String, Pair<String, String?>>>,
) {
    fun multiplier(magnitude: Int): Int = if (multipliers.isEmpty()) 0 else multipliers[magnitude.coerceIn(0, multipliers.lastIndex)]

    /** The affixes for [plural], or the pattern CLDR gives the value 1 itself ("=1") when [exactlyOne]. */
    fun affixes(
        magnitude: Int,
        plural: String,
        exactlyOne: Boolean,
    ): Pair<String, String?> {
        if (patterns.isEmpty()) return NO_AFFIX
        val forms = patterns[magnitude.coerceIn(0, patterns.lastIndex)]
        return forms["=1"].takeIf { exactlyOne } ?: forms[plural] ?: forms["other"] ?: NO_AFFIX
    }

    /** The affixes of a negative value, sign included, where CLDR does not simply put the sign first. */
    fun negativeAffixes(
        magnitude: Int,
        plural: String,
    ): Pair<String, String?>? {
        if (patterns.isEmpty()) return null
        val forms = patterns[magnitude.coerceIn(0, patterns.lastIndex)]
        return forms["$plural-"] ?: forms["other-"].takeIf { plural !in forms }
    }

    private companion object {
        val NO_AFFIX: Pair<String, String?> = "" to ""
    }
}

/**
 * CLDR plural rules ("one: i = 1 and v = 0"), evaluated on a shown number's operands: n absolute value,
 * i integer digits, v and w visible fraction digit counts, f and t the fraction digits, c and e the
 * compact exponent.
 */
internal class PluralRules(
    private val rules: List<Pair<String, String>>,
) {
    fun select(
        integer: String,
        fraction: String,
        exponent: Int,
    ): String {
        val operands = Operands(integer, fraction, exponent)
        return rules.firstOrNull { (_, condition) -> matches(condition, operands) }?.first ?: "other"
    }

    private class Operands(
        integer: String,
        fraction: String,
        exponent: Int,
    ) {
        val i = integer.toDoubleOrNull() ?: 0.0
        val n = if (fraction.isEmpty()) i else "$integer.$fraction".toDouble()
        val v = fraction.length.toDouble()
        val f = fraction.ifEmpty { "0" }.toDouble()
        val t = fraction.trimEnd('0').ifEmpty { "0" }.toDouble()
        val w = fraction.trimEnd('0').length.toDouble()
        val e = exponent.toDouble()

        fun of(name: Char): Double =
            when (name) {
                'n' -> n
                'i' -> i
                'v' -> v
                'w' -> w
                'f' -> f
                't' -> t
                'c', 'e' -> e
                else -> throw IllegalArgumentException("Unknown plural operand $name")
            }
    }

    private fun matches(
        condition: String,
        operands: Operands,
    ): Boolean = condition.split(" or ").any { and -> and.split(" and ").all { relation(it.trim(), operands) } }

    private fun relation(
        text: String,
        operands: Operands,
    ): Boolean {
        val negated = text.contains("!=")
        val (left, right) = text.split(if (negated) "!=" else "=").map(String::trim)
        val operand = operands.of(left[0])
        val value = left.substringAfter('%', "").trim().toIntOrNull()?.let { operand.rem(it) } ?: operand
        val inList =
            right.split(',').any { item ->
                val bounds = item.trim().split("..")
                if (bounds.size == 2) {
                    value == kotlin.math.floor(value) && value >= bounds[0].toDouble() && value <= bounds[1].toDouble()
                } else {
                    value == bounds[0].toDouble()
                }
            }
        return inList != negated
    }
}
