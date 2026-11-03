package io.github.aedev.flow.utils

import io.github.aedev.flow.data.local.CONTENT_LANGUAGE_FOLLOW_APP
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

// Upstream's three newPipeLocalization cases are not ported: that function configures NewPipe's extractor, which iOS
// does not have (PARITY, utils/ContentLocalization.kt). The case that sets the JVM default locale is in
// ContentLocalizationJvmTest.
class ContentLocalizationTest {
    @Test
    fun blankRegionFallsBackInsteadOfSendingAnInvalidGl() {
        assertThat(normalizeYouTubeCountry("  ")).matches("[A-Z]{2}")
        assertThat(normalizeYouTubeCountry("fr")).isEqualTo("FR")
    }

    @Test
    fun followingTheAppAsksYouTubeForTheLanguageOnScreen1277() {
        val locale = contentYouTubeLocale("ru", CONTENT_LANGUAGE_FOLLOW_APP, "US", Locale.forLanguageTag("en"))

        assertThat(locale.hl).isEqualTo("ru")
        assertThat(locale.gl).isEqualTo("US")
    }

    @Test
    fun aChosenContentLanguageWinsOverTheAppLanguage() {
        assertThat(contentYouTubeLocale("ru", "en", "RU", Locale.forLanguageTag("en")).hl).isEqualTo("en")
    }
}
