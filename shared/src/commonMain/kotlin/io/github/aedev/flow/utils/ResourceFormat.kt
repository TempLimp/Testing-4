package io.github.aedev.flow.utils

import kotlin.math.abs

private val Specifier = Regex("""%(?:(\d+)\$)?([-#+ 0,(<]*)(\d+)?(?:\.(\d+))?([a-zA-Z%])""")

/**
 * [format] filled in the way Android's `getString(id, args)` (java.util.Formatter) does, for the
 * specifiers Flow's strings use: `%s`, `%d` and `%f`, each with an optional `n$` index and `%f` with
 * an optional `.p` precision, and `%%`. Anything else throws, as a malformed specifier does on Android.
 * Compose Multiplatform's own `stringResource(res, args)` substitutes only `%n$s` and `%n$d`.
 */
// shortcut: digits and the decimal point are always ASCII ("1.5"), as with Java's Locale.ROOT, while Android formats with the app locale ("1,5" in German); upgrade when S13 ports the app-locale handling.
fun formatResourceString(
    format: String,
    args: Array<out Any?>,
): String {
    val out = StringBuilder()
    var ordinary = 0
    var index = 0
    while (index < format.length) {
        val percent = format.indexOf('%', index)
        if (percent < 0) {
            out.append(format, index, format.length)
            break
        }
        out.append(format, index, percent)
        val match = Specifier.matchAt(format, percent) ?: throw IllegalArgumentException("Bad format at $percent in \"$format\"")
        val (position, flags, width, precision, conversion) = match.destructured
        index = match.range.last + 1
        if (conversion == "%") {
            out.append('%')
            continue
        }
        require(flags.isEmpty() && width.isEmpty()) { "Unsupported flags or width in ${match.value}" }
        val argIndex = if (position.isEmpty()) ordinary++ else position.toInt() - 1
        require(argIndex in args.indices) { "Missing argument for ${match.value} in \"$format\"" }
        val arg = args[argIndex]
        when (conversion) {
            "s" -> {
                require(precision.isEmpty()) { "Unsupported ${match.value}" }
                out.append(arg.toString())
            }

            "d" -> {
                require(precision.isEmpty() && (arg is Int || arg is Long || arg is Short || arg is Byte)) {
                    "${match.value} needs an integer, got $arg"
                }
                out.append(arg.toString())
            }

            "f" -> {
                val value =
                    when (arg) {
                        is Double -> arg
                        is Float -> arg.toDouble()
                        else -> throw IllegalArgumentException("${match.value} needs a Float or Double, got $arg")
                    }
                out.append(fixed(value, precision.toIntOrNull() ?: 6))
            }

            else -> throw IllegalArgumentException("Unsupported conversion ${match.value}")
        }
    }
    return out.toString()
}

/**
 * Java's `%.Nf`: the shortest decimal form of [value] rounded half-up (FormattedFloatingDecimal),
 * so 0.15 gives "0.2" where C's printf, rounding the binary value, gives "0.1".
 */
private fun fixed(
    value: Double,
    precision: Int,
): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
    val text = abs(value).toString()
    val mantissa = text.substringBefore('E').substringBefore('e')
    val exponent = text.substringAfter('E', text.substringAfter('e', "0")).toInt()
    val integerDigits = mantissa.substringBefore('.')
    val allDigits = integerDigits + mantissa.substringAfter('.', "")
    // value = 0.<digits> x 10^point
    val digits = allDigits.trimStart('0')
    val point = integerDigits.length + exponent - (allDigits.length - digits.length)
    val keep = point + precision
    var scaled =
        when {
            digits.isEmpty() || keep < 0 -> "0"
            keep == 0 -> if (digits[0] >= '5') "1" else "0"
            keep >= digits.length -> digits + "0".repeat(keep - digits.length)
            digits[keep] >= '5' -> increment(digits.substring(0, keep))
            else -> digits.substring(0, keep)
        }
    scaled = scaled.padStart(precision + 1, '0')
    val whole = scaled.substring(0, scaled.length - precision)
    val sign = if (negative) "-" else ""
    return if (precision == 0) sign + whole else sign + whole + "." + scaled.substring(whole.length)
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
