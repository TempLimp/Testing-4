package io.github.aedev.flow

import io.github.aedev.flow.utils.formatResourceString
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of upstream's TranslationFormatStringsTest: every translated string formats with the
 * arguments its English string declares (a bare `%` in German crashed Notification settings,
 * upstream #981). Here each one also has to come out of [formatResourceString] exactly as
 * java.util.Formatter, which Android's getString uses, prints it.
 */
class TranslationFormatStringsTest {
    @Test
    fun everyTranslationFormatsWithTheEnglishArgumentsAsJavaDoes() {
        val dirs = upstreamStringDirs()
        assertEquals(34, dirs.size)
        val english = placeholders(loadUpstreamStrings(dirs.first().resolve("strings.xml")))
        val failures = mutableListOf<String>()
        var formatted = 0
        for (dir in dirs) {
            for ((key, text) in loadUpstreamStrings(dir.resolve("strings.xml"))) {
                if (text !is String || key.endsWith("[]")) continue
                val conversions = english[key] ?: english[key.substringBefore('#') + "#other"] ?: continue
                for (sample in Samples) {
                    val args = Array<Any>(conversions.size) { sample.getValue(conversions[it]) }
                    val java = runCatching { String.format(Locale.ROOT, text, *args) }
                    val ours = runCatching { formatResourceString(text, args) }
                    formatted++
                    if (java.isFailure || ours != java) failures += "${dir.name} $key \"$text\" ${args.toList()}: java=$java ours=$ours"
                }
            }
        }
        assertTrue(formatted > 1000, "only $formatted formatted")
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** The conversion of each argument English declares, by position; strings without any are left out. */
    private fun placeholders(strings: Map<String, Any>): Map<String, List<Char>> =
        strings
            .filterValues { it is String }
            .mapValues { (_, text) ->
                var implicit = 0
                val byIndex =
                    Placeholder
                        .findAll(text as String)
                        .associate { match -> (match.groupValues[1].toIntOrNull() ?: ++implicit) to match.groupValues[2].single() }
                List(byIndex.keys.maxOrNull() ?: 0) { byIndex[it + 1] ?: 's' }
            }.filterValues { it.isNotEmpty() }

    private companion object {
        /** The placeholders the English strings use; a literal `90% played` is not one. */
        val Placeholder = Regex("""%(?:(\d+)\$)?(?:\.\d+)?([sdf])""")

        val Samples =
            listOf(
                mapOf('d' to 1, 'f' to 1.0, 's' to "x"),
                mapOf('d' to 1234567, 'f' to 0.15, 's' to "Flow"),
                mapOf('d' to -5, 'f' to 2.345, 's' to "100%"),
                mapOf('d' to 0, 'f' to 9.95, 's' to ""),
                mapOf('d' to Int.MAX_VALUE, 'f' to 1.005, 's' to "ü"),
            )
    }
}
