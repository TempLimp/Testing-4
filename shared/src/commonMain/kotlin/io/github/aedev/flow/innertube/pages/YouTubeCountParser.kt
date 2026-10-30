package io.github.aedev.flow.innertube.pages


/**
 * Reads a count as YouTube renders it in the request's host language: an exact one
 * ("1.013.511 visualizaciones", "334 435 просмотров") or an abbreviated one ("2,4 M de
 * visualizaciones", "33万回視聴", "3.3 लाख व्यूज़"). The suffix is looked up in [hl]'s own table and
 * then as English K/M/B, which YouTube also uses in some languages ("334 K visualizaciones").
 */
internal object YouTubeCountParser {
    fun parse(
        text: String?,
        hl: String?,
        suffixSource: CompactSuffixSource = IcuCompactSuffixSource,
    ): Long? {
        val normalized = normalizeCountText(text ?: return null)
        val number = NUMBER.find(normalized) ?: return null
        val value = readNumber(number.value) ?: return null
        val rest = normalized.substring(number.range.last + 1).trimStart().lowercase()
        val multiplier =
            hl
                ?.takeIf(String::isNotBlank)
                ?.let { matchSuffix(rest, suffixSource.suffixes(it)) }
                ?: matchSuffix(rest, ENGLISH_SUFFIXES)
                ?: 1L
        return value.times(multiplier)
    }

    private val NUMBER = Regex("""[0-9](?:[0-9.,'’ ٫٬]*[0-9])?""")

    private val ENGLISH_SUFFIXES = mapOf("k" to 1_000L, "m" to 1_000_000L, "b" to 1_000_000_000L)

    // A grouping separator is always followed by three digits and a compact decimal never is, so the
    // text itself tells "2,4 M" from "334.435" without trusting the locale's symbols, which differ
    // from what YouTube prints in some languages (Arabic's U+066B against YouTube's ".").
    private fun readNumber(token: String): Decimal? {
        val lastSeparator = token.indexOfLast { !it.isDigit() }
        val digitsAfter = token.length - lastSeparator - 1
        val isDecimal = lastSeparator >= 0 && token[lastSeparator] in DECIMAL_MARKS && digitsAfter != 3
        val plain =
            if (isDecimal) {
                token.substring(0, lastSeparator).filter(Char::isDigit) + "." + token.substring(lastSeparator + 1)
            } else {
                token.filter(Char::isDigit)
            }
        return Decimal.parse(plain)
    }

    /** The digits YouTube printed, kept exact the way BigDecimal kept them: "2.4" times a million is 2,400,000. */
    private class Decimal(
        private val whole: Long,
        private val fraction: String,
    ) {
        // Truncates toward zero like BigDecimal.toLong(); a product past Long reads as no count.
        fun times(multiplier: Long): Long? {
            val wholePart = whole.timesExact(multiplier) ?: return null
            if (fraction.isEmpty()) return wholePart
            var scale = 1L
            repeat(fraction.length) { scale = scale.timesExact(10L) ?: return null }
            val fractionPart = (fraction.toLong().timesExact(multiplier) ?: return null) / scale
            return (wholePart + fractionPart).takeIf { it >= wholePart }
        }

        private fun Long.timesExact(other: Long): Long? = if (other != 0L && this > Long.MAX_VALUE / other) null else this * other

        companion object {
            fun parse(plain: String): Decimal? {
                val whole = plain.substringBefore('.')
                val fraction = plain.substringAfter('.', "")
                if (whole.length > 18 || fraction.length > 18) return null
                return Decimal(whole.toLongOrNull() ?: return null, fraction.takeIf { it.all(Char::isDigit) } ?: return null)
            }
        }
    }

    private fun matchSuffix(
        rest: String,
        suffixes: Map<String, Long>,
    ): Long? =
        suffixes.entries
            .filter { (suffix, _) -> rest.startsWith(suffix) && endsWord(rest, suffix) }
            .maxByOrNull { (suffix, _) -> suffix.length }
            ?.value

    // Han and Hangul suffixes run straight into the next word ("33万回視聴", "33만회"); alphabetic
    // ones must end where a word does, or "mil" would read as English "m".
    private fun endsWord(
        rest: String,
        suffix: String,
    ): Boolean {
        val next = rest.getOrNull(suffix.length) ?: return true
        val last = suffix.last()
        val joinsNext = last.isHanOrHangul() || !last.isLetter()
        return joinsNext || !next.isLetter()
    }

    // Character.UnicodeScript.HAN and HANGUL over the Basic Multilingual Plane (Unicode Scripts.txt):
    // a suffix ends in a single Char, so the supplementary ideographs cannot occur here.
    private val UNSPACED_RANGES =
        listOf(
            0x1100..0x11FF, 0x2E80..0x2E99, 0x2E9B..0x2EF3, 0x2F00..0x2FD5, 0x3005..0x3005, 0x3007..0x3007,
            0x3021..0x3029, 0x302E..0x302F, 0x3038..0x303B, 0x3131..0x318E, 0x3200..0x321E, 0x3260..0x327E,
            0x3400..0x4DBF, 0x4E00..0x9FFF, 0xA960..0xA97C, 0xAC00..0xD7A3, 0xD7B0..0xD7C6, 0xD7CB..0xD7FB,
            0xF900..0xFA6D, 0xFA70..0xFAD9, 0xFFA0..0xFFBE, 0xFFC2..0xFFC7, 0xFFCA..0xFFCF, 0xFFD2..0xFFD7,
            0xFFDA..0xFFDC,
        )

    private fun Char.isHanOrHangul(): Boolean = UNSPACED_RANGES.any { code in it }
    private const val DECIMAL_MARKS = ".,٫"
}
