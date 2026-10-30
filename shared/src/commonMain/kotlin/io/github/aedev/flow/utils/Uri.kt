package io.github.aedev.flow.utils

import io.ktor.http.decodeURLPart
import io.ktor.http.decodeURLQueryComponent

/**
 * The part of `android.net.Uri` upstream uses on stream URLs: [parse] with [getQueryParameter] and
 * [host], and the static [encode]/[decode]. Android's rules are kept: [encode] leaves letters, digits
 * and `_-!.~'()*` alone, [decode] does not turn `+` into a space, and [getQueryParameter] does.
 */
class Uri private constructor(
    private val uriString: String,
) {
    val host: String?
        get() {
            val afterScheme = uriString.substringAfter("://", missingDelimiterValue = "").ifEmpty { return null }
            return afterScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('@')
                .substringBefore(':')
                .ifEmpty { null }
        }

    fun getQueryParameter(key: String): String? {
        val query = uriString.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val separator = pair.indexOf('=')
            val name = if (separator < 0) pair else pair.substring(0, separator)
            if (decode(name) != key) continue
            val value = if (separator < 0) "" else pair.substring(separator + 1)
            return value.decodeURLQueryComponent(plusIsSpace = true)
        }
        return null
    }

    override fun toString(): String = uriString

    override fun equals(other: Any?): Boolean = other is Uri && other.uriString == uriString

    override fun hashCode(): Int = uriString.hashCode()

    companion object {
        private const val HEX = "0123456789ABCDEF"
        private const val UNRESERVED = "_-!.~'()*"

        fun parse(uriString: String): Uri = Uri(uriString)

        fun encode(s: String): String =
            buildString {
                for (byte in s.encodeToByteArray()) {
                    val c = byte.toInt().toChar()
                    if (byte >= 0 && (c.isLetterOrDigit() || c in UNRESERVED)) {
                        append(c)
                    } else {
                        val b = byte.toInt() and 0xFF
                        append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
                    }
                }
            }

        fun decode(s: String): String = s.decodeURLPart()
    }
}
