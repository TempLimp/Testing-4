package io.github.aedev.flow.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.platform.SystemFont
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// Upstream's AppFontTest also covers CustomFontStore (data/local); those cases move with that class.
class AppFontTest {
    private val dir = Path(SystemTemporaryDirectory, "app-font-test-${Random.nextLong().toULong()}")

    private fun newFile(name: String): Path {
        SystemFileSystem.createDirectories(dir)
        return Path(dir, name).also { SystemFileSystem.sink(it).close() }
    }

    @AfterTest
    fun cleanUp() {
        SystemFileSystem.list(dir.takeIf { SystemFileSystem.exists(it) } ?: return).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(dir)
    }

    @Test
    fun storedIdsRoundTripAndAnythingUnknownIsTheSystemFont() {
        AppFont.entries.forEach { assertEquals(it, AppFont.fromStorage(it.storageId)) }
        assertEquals(AppFont.SYSTEM, AppFont.fromStorage(null))
        assertEquals(AppFont.SYSTEM, AppFont.fromStorage("inter"))
    }

    @Test
    fun aCustomFontIsUsedOnlyWhenItsFileLoads() {
        val file = newFile("custom.ttf")
        assertEquals(FontFamily.Monospace, appFontFamily(AppFont.CUSTOM, file) { FontFamily.Monospace })
        assertEquals(FlowFontFamily, appFontFamily(AppFont.CUSTOM, file) { null })
    }

    @Test
    fun aCustomChoiceWithoutItsFileFallsBackToTheSystemFont() {
        var loaded = false
        val missing = Path(dir, "custom.otf")
        assertEquals(
            FlowFontFamily,
            appFontFamily(AppFont.CUSTOM, null) {
                loaded = true
                FontFamily.Monospace
            },
        )
        assertEquals(
            FlowFontFamily,
            appFontFamily(AppFont.CUSTOM, missing) {
                loaded = true
                FontFamily.Monospace
            },
        )
        assertFalse(loaded)
    }

    @Test
    fun builtInChoicesNeverTouchTheCustomFile() {
        val file = newFile("custom.ttf")
        assertEquals(FlowFontFamily, appFontFamily(AppFont.SYSTEM, file) { error("not loaded") })
        assertEquals(FontFamily.Serif, appFontFamily(AppFont.SERIF, file) { error("not loaded") })
    }

    @OptIn(ExperimentalTextApi::class)
    @Test
    fun condensedIsTheSystemCondensedFamilyInUpstreamsFiveWeights() {
        val fonts = (appFontFamily(AppFont.CONDENSED, null) { error("not loaded") } as FontListFontFamily).fonts
        assertEquals(listOf(400, 500, 600, 700, 800), fonts.map { it.weight.weight })
        fonts.forEach { assertEquals(CONDENSED_FAMILY, (it as SystemFont).identity) }
    }
}
