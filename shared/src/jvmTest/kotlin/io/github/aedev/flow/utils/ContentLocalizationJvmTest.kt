package io.github.aedev.flow.utils

import io.github.aedev.flow.data.local.CONTENT_LANGUAGE_FOLLOW_APP
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

// The ContentLocalizationTest case that moves the default locale, which only the jvm target can do.
class ContentLocalizationJvmTest {
    @Test
    fun aSystemAppLanguageAsksForTheDeviceLanguageNotTheOneStillOnScreen() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru"))
        try {
            assertThat(contentYouTubeLocale("system", CONTENT_LANGUAGE_FOLLOW_APP, "US", Locale(java.util.Locale.ENGLISH)).hl).isEqualTo("en")
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
