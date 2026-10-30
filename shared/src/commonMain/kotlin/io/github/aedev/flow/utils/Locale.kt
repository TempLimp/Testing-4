package io.github.aedev.flow.utils

/**
 * The part of `java.util.Locale` upstream uses: java.util.Locale on the jvm test target, NSLocale on iOS.
 * Locale-specific case mapping (`uppercase(Locale.US)`) is plain `uppercase()` here: Kotlin's common case
 * mapping is the invariant one, which only Turkish, Azeri and Lithuanian tailor.
 */
expect class Locale {
    /** Lowercase ISO 639 code, empty for the root locale. */
    val language: String

    /** Uppercase ISO 3166 code or UN M.49 number, empty when absent. */
    val country: String

    /** Title-case ISO 15924 code, empty when absent. */
    val script: String

    fun toLanguageTag(): String

    fun getDisplayName(inLocale: Locale): String

    fun getDisplayLanguage(inLocale: Locale): String

    fun getDisplayCountry(inLocale: Locale): String

    companion object {
        val ROOT: Locale
        val US: Locale

        /** The language the app is shown in, with the device's region. */
        fun getDefault(): Locale

        fun forLanguageTag(languageTag: String): Locale
    }
}

/** `Locale.getDisplayLanguage()`: the language's name in the app's language. */
val Locale.displayLanguage: String
    get() = getDisplayLanguage(Locale.getDefault())

/** A region-only locale, like `Locale("", code)`. */
fun regionLocale(country: String): Locale = Locale.forLanguageTag("und-$country")

/** `Char.titlecase(locale)`: the first letter of a display name, as the platform capitalises it in [locale]. */
expect fun Char.titlecase(locale: Locale): String

/** Compares strings the way [locale]'s collation orders them, as `java.text.Collator` does. */
expect fun collator(locale: Locale): Comparator<String>

/** Unicode canonical decomposition (NFD), as `java.text.Normalizer` with `Form.NFD`. */
expect fun String.normalizeNfd(): String
