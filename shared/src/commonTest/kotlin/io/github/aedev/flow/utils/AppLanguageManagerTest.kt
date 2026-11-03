package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.Test

/**
 * Upstream's first three cases wrap an Android Context in the chosen language (`wrapContext`), which iOS has no
 * counterpart for: the system applies the per-app language to the whole process at launch. The rest port as they are.
 */
class AppLanguageManagerTest {
    @Test
    fun aSavedLanguageIsTheOneYouTubeFollows() {
        AppLanguageManager.saveLanguageTag("ru")
        assertEquals("ru", AppLanguageManager.appliedLanguage.value)

        AppLanguageManager.saveLanguageTag("pt_br")
        assertEquals("pt-BR", AppLanguageManager.appliedLanguage.value)
    }

    @Test
    fun resolvingAtStartPublishesTheLanguageTheUIUses() {
        AppLanguageManager.saveLanguageTag("de")
        AppLanguageManager.saveLanguageTag(AppLanguageManager.SYSTEM_DEFAULT)

        assertEquals(AppLanguageManager.resolveLanguageTag(), AppLanguageManager.appliedLanguage.value)
    }
}