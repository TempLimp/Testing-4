package io.github.aedev.flow.utils

import java.text.Collator
import java.text.Normalizer

actual class Locale(
    val java: java.util.Locale,
) {
    actual val language: String get() = java.language
    actual val country: String get() = java.country
    actual val script: String get() = java.script

    actual fun toLanguageTag(): String = java.toLanguageTag()

    actual fun getDisplayName(inLocale: Locale): String = java.getDisplayName(inLocale.java)

    actual fun getDisplayLanguage(inLocale: Locale): String = java.getDisplayLanguage(inLocale.java)

    actual fun getDisplayCountry(inLocale: Locale): String = java.getDisplayCountry(inLocale.java)

    override fun equals(other: Any?): Boolean = other is Locale && other.java == java

    override fun hashCode(): Int = java.hashCode()

    override fun toString(): String = java.toString()

    actual companion object {
        actual val ROOT: Locale = Locale(java.util.Locale.ROOT)
        actual val US: Locale = Locale(java.util.Locale.US)

        actual fun getDefault(): Locale = Locale(java.util.Locale.getDefault())

        actual fun forLanguageTag(languageTag: String): Locale = Locale(java.util.Locale.forLanguageTag(languageTag))
    }
}

actual fun Char.titlecase(locale: Locale): String = titlecase(locale.java)

actual fun collator(locale: Locale): Comparator<String> {
    val collator = Collator.getInstance(locale.java)
    return Comparator { a, b -> collator.compare(a, b) }
}

actual fun String.normalizeNfd(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
