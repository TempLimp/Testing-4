package io.github.aedev.flow

import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.allPluralStringResources
import io.github.aedev.flow.resources.allStringArrayResources
import io.github.aedev.flow.resources.allStringResources
import io.github.aedev.flow.resources.channels_count
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Every upstream string, array and plural, in every locale, reads through Compose Multiplatform
 * exactly as Android's getString returns it. Keys a locale has but the default lacks are not
 * shipped (no code can reference them), so they are skipped.
 */
class UpstreamStringsTest {
    @Test
    fun everyLocaleReadsAsOnAndroid() =
        runTest(timeout = 5.minutes) {
            val dirs = upstreamStringDirs()
            assertEquals(34, dirs.size, "default + 33 locales in ${upstreamRes.absolutePath}")
            val defaults = loadUpstreamStrings(dirs.first().resolve("strings.xml"))
            assertEquals(4028, defaults.keys.count { '#' !in it && !it.endsWith("[]") })

            val mismatches = mutableListOf<String>()
            var checked = 0
            val defaultNames = defaults.keys.map { it.substringBefore('#') }.toSet()
            for (dir in dirs) {
                // By name, so a locale keeps plural quantities English has no use for (Arabic's two, few, many).
                val entries = loadUpstreamStrings(dir.resolve("strings.xml")).filterKeys { it.substringBefore('#') in defaultNames }
                // Which item a count picks is CLDR's call; each pick must be one of this locale's items,
                // or the default's when the locale lacks the plural, as on Android.
                val plurals = entries.keys.filter { '#' in it }.map { it.substringBefore('#') }.toSet()
                withLocale(localeFor(dir.name)) {
                    for ((key, expected) in entries) {
                        val actual =
                            when {
                                key.endsWith("[]") -> getStringArray(Res.allStringArrayResources.getValue(key.removeSuffix("[]")))
                                '#' in key -> continue
                                else -> getString(Res.allStringResources.getValue(key))
                            }
                        checked++
                        if (actual != expected) mismatches += "${dir.name} $key: expected <$expected> got <$actual>"
                    }
                    for (name in Res.allPluralStringResources.keys) {
                        val source = if (name in plurals) entries else defaults
                        val items = source.filterKeys { '#' in it && it.substringBefore('#') == name }.values
                        for (count in listOf(0, 1, 2, 3, 5, 11, 21, 22, 101, 1000)) {
                            val actual = getPluralString(Res.allPluralStringResources.getValue(name), count)
                            checked++
                            if (actual !in items) mismatches += "${dir.name} $name($count): <$actual> is not one of $items"
                        }
                    }
                }
            }
            assertTrue(checked > 90_000, "only $checked checked")
            assertTrue(mismatches.isEmpty(), "${mismatches.size} of $checked differ:\n" + mismatches.take(40).joinToString("\n"))
        }

    @Test
    fun pluralsFollowEachLanguagesRules() =
        runTest {
            withLocale(Locale.ENGLISH) {
                assertEquals("%d channel", getPluralString(Res.plurals.channels_count, 1))
                assertEquals("%d channels", getPluralString(Res.plurals.channels_count, 2))
            }
            withLocale(Locale.forLanguageTag("ru")) {
                assertEquals("%d канал", getPluralString(Res.plurals.channels_count, 21))
                assertEquals("%d канала", getPluralString(Res.plurals.channels_count, 3))
                assertEquals("%d каналов", getPluralString(Res.plurals.channels_count, 11))
            }
        }
}
