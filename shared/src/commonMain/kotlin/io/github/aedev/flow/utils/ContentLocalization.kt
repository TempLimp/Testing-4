package io.github.aedev.flow.utils

import io.github.aedev.flow.data.local.CONTENT_LANGUAGE_FOLLOW_APP
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.normalizeYouTubeHostLanguage

// Upstream's newPipeLocalization and newPipeContentCountry configure NewPipe's extractor, which does not run on
// iOS (an iOS limit in PARITY); the two functions every InnerTube request uses are ported below.

/** Two-letter country for YouTube's `gl`, falling back to the device country and then to US. */
fun normalizeYouTubeCountry(region: String): String {
    val normalized = region.trim().uppercase()
    if (normalized.matches(COUNTRY_CODE)) return normalized

    return Locale
        .getDefault()
        .country
        .trim()
        .uppercase()
        .takeIf { it.matches(COUNTRY_CODE) }
        ?: "US"
}

/** The locale every InnerTube request carries: the content language, else the language the UI is shown in. */
fun contentYouTubeLocale(
    appliedAppLanguage: String,
    contentLanguage: String,
    region: String,
    deviceLocale: Locale,
): YouTubeLocale {
    val language = if (contentLanguage == CONTENT_LANGUAGE_FOLLOW_APP) appliedAppLanguage else contentLanguage
    return YouTubeLocale(gl = normalizeYouTubeCountry(region), hl = normalizeYouTubeHostLanguage(language, deviceLocale))
}

private val COUNTRY_CODE = Regex("[A-Z]{2}")
