package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertFalse
import io.github.aedev.flow.testing.assertTrue
import io.github.aedev.flow.utils.Locale
import kotlin.test.Test

class AppLanguageListTest {
    private val tags = AppLanguageManager.getSupportedLanguages(Locale.forLanguageTag("en")).map { it.tag }

    @Test
    fun thePickerListsEveryShippedTranslation1300() {
        listOf("en", "zh-TW", "zh-CN", "yue-Hant", "pt-BR", "pt", "id", "vi", "ko", "ja").forEach { tag ->
            assertTrue("$tag missing from $tags", tag in tags)
        }
        assertFalse("in" in tags)
    }

    @Test
    fun everyListedTagIsAWellFormedLanguageTag() {
        tags.forEach { tag -> assertEquals(tag, Locale.forLanguageTag(tag).toLanguageTag()) }
    }

    @Test
    fun traditionalChineseIsNamedInItsOwnScript() {
        val option = AppLanguageManager.getSupportedLanguages(Locale.forLanguageTag("en")).first { it.tag == "zh-TW" }
        assertEquals("中文 (台灣)", option.nativeName)
        assertEquals("Chinese (Taiwan)", option.localizedName)
    }
}
