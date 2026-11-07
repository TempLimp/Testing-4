@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.utils

import platform.Foundation.NSCaseInsensitiveSearch
import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSLocaleScriptCode
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.NSWidthInsensitiveSearch
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.compare
import platform.Foundation.currentLocale
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.localeIdentifier
import platform.Foundation.localizedStringForCountryCode
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.localizedStringForScriptCode
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Foundation.preferredLanguages
import platform.Foundation.uppercaseStringWithLocale

actual class Locale(
    val ns: NSLocale,
) {
    actual val language: String get() = component(NSLocaleLanguageCode).takeUnless { it == "und" }.orEmpty()
    actual val country: String get() = component(NSLocaleCountryCode)
    actual val script: String get() = component(NSLocaleScriptCode)

    private fun component(key: String?): String = ns.objectForKey(key) as? String ?: ""

    actual fun toLanguageTag(): String =
        listOf(language.ifEmpty { "und" }, script, country).filter(String::isNotEmpty).joinToString("-")

    // Java's and Android's shape, "language (script,country)", not CLDR's localized pattern (zh: "中文（台灣）").
    actual fun getDisplayName(inLocale: Locale): String {
        val parts =
            listOf(
                language to getDisplayLanguage(inLocale),
                script to (if (script.isEmpty()) "" else inLocale.ns.localizedStringForScriptCode(script).orEmpty()),
                country to getDisplayCountry(inLocale),
            ).filter { it.first.isNotEmpty() }.map { (code, name) -> name.ifEmpty { code } }
        return if (parts.size < 2) parts.firstOrNull().orEmpty() else "${parts[0]} (${parts.drop(1).joinToString(",")})"
    }

    actual fun getDisplayLanguage(inLocale: Locale): String =
        if (language.isEmpty()) "" else inLocale.ns.localizedStringForLanguageCode(language).orEmpty()

    actual fun getDisplayCountry(inLocale: Locale): String =
        if (country.isEmpty()) "" else inLocale.ns.localizedStringForCountryCode(country).orEmpty()

    override fun equals(other: Any?): Boolean = other is Locale && other.ns.localeIdentifier == ns.localeIdentifier

    override fun hashCode(): Int = ns.localeIdentifier.hashCode()

    override fun toString(): String = ns.localeIdentifier

    actual companion object {
        actual val ROOT: Locale = Locale(NSLocale(localeIdentifier = ""))
        actual val US: Locale = Locale(NSLocale(localeIdentifier = "en_US"))

        /**
         * The first preferred language, which is the one Compose resources pick strings for, with the
         * device's region when that language names none. Java's default on Android likewise carries
         * the app language with a region.
         */
        actual fun getDefault(): Locale {
            val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String)?.let(::forLanguageTag) ?: return Locale(NSLocale.currentLocale)
            if (preferred.country.isNotEmpty()) return preferred
            val region = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String ?: return preferred
            return Locale(NSLocale(localeIdentifier = "${preferred.ns.localeIdentifier}_$region"))
        }

        actual fun forLanguageTag(languageTag: String): Locale = Locale(NSLocale(localeIdentifier = NSLocale.canonicalLocaleIdentifierFromString(languageTag)))
    }
}

// The same steps as Kotlin's jvm Char.titlecase(Locale), on Foundation's case mapping.
actual fun Char.titlecase(locale: Locale): String {
    val upper = (toString() as NSString).uppercaseStringWithLocale(locale.ns)
    if (upper.length > 1) return if (this == 'ŉ') upper else upper[0] + upper.substring(1).lowercase()
    if (upper != uppercase()) return upper
    return titlecaseChar().toString()
}

actual fun collator(
    locale: Locale,
    primaryStrength: Boolean,
): Comparator<String> {
    // PRIMARY in ICU sets case, accent and width differences aside; Foundation spells those out as options.
    val options = if (primaryStrength) NSCaseInsensitiveSearch or NSDiacriticInsensitiveSearch or NSWidthInsensitiveSearch else 0u
    return Comparator { a, b -> (a as NSString).compare(b, options = options, range = NSMakeRange(0u, a.length.toULong()), locale = locale.ns).toInt() }
}

actual fun String.normalizeNfd(): String = (this as NSString).decomposedStringWithCanonicalMapping

actual fun String.normalizeNfkc(): String = (this as NSString).precomposedStringWithCompatibilityMapping
