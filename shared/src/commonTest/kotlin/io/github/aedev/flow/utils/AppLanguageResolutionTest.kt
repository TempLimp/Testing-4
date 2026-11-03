package io.github.aedev.flow.utils

import io.github.aedev.flow.utils.AppLanguageManager.LanguageResolution
import io.github.aedev.flow.utils.AppLanguageManager.SYSTEM_DEFAULT
import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

class AppLanguageResolutionTest {
    @Test
    fun aLanguagePickedInTheAppBeforeAndroidKeptOneIsHandedOver() {
        assertEquals(
            LanguageResolution(tag = "de", pushToSystem = true),
            AppLanguageManager.resolveAgainstSystem(stored = "de", system = SYSTEM_DEFAULT, handedOver = false),
        )
    }

    @Test
    fun aLanguagePickedInAndroidSSettingsWinsOverTheAppSOldChoice() {
        assertEquals(
            LanguageResolution(tag = "zh-TW", pushToSystem = false),
            AppLanguageManager.resolveAgainstSystem(stored = "de", system = "zh-TW", handedOver = false),
        )
    }

    @Test
    fun resettingTheLanguageInAndroidSSettingsIsKeptOnceHandedOver() {
        assertEquals(
            LanguageResolution(tag = SYSTEM_DEFAULT, pushToSystem = false),
            AppLanguageManager.resolveAgainstSystem(stored = "de", system = SYSTEM_DEFAULT, handedOver = true),
        )
    }

    @Test
    fun followingTheSystemNeedsNothingHandedOver() {
        assertEquals(
            LanguageResolution(tag = SYSTEM_DEFAULT, pushToSystem = false),
            AppLanguageManager.resolveAgainstSystem(stored = SYSTEM_DEFAULT, system = SYSTEM_DEFAULT, handedOver = false),
        )
    }
}
