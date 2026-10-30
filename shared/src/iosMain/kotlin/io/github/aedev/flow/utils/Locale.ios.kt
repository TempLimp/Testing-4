@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.utils

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSLocaleScriptCode
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.compare
import platform.Foundation.currentLocale
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.localeIdentifier
import platform.Foundation.localizedStringForCountryCode
import platform.Foundation.localizedStringForLanguageCode
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

    actual fun getDisplayName(inLocale: Locale): String =
        if (language.isEmpty() && country.isEmpty()) "" else inLocale.ns.displayNameForKey(NSLocaleIdentifier, ns.localeIdentifier).orEmpty()

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

actual fun collator(locale: Locale): Comparator<String> =
    Comparator { a, b -> (a as NSString).compare(b, options = 0u, range = NSMakeRange(0u, a.length.toULong()), locale = locale.ns).toInt() }

actual fun String.normalizeNfd(): String = (this as NSString).decomposedStringWithCanonicalMapping
