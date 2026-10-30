package io.github.aedev.flow.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** The font the app draws its text in. Stored by [storageId], so entries can be renamed freely. */
enum class AppFont(
    val storageId: String,
) {
    SYSTEM("system"),
    CONDENSED("condensed"),
    SERIF("serif"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromStorage(id: String?): AppFont = entries.firstOrNull { it.storageId == id } ?: SYSTEM
    }
}

private val CondensedWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

/**
 * Upstream draws Android's `sans-serif-condensed` (Roboto Condensed). iOS has no such family; Avenir Next
 * Condensed ships with iOS in all five of these weights.
 */
internal const val CONDENSED_FAMILY = "Avenir Next Condensed"

/**
 * The family for [font]. A custom font comes from [customFile] through [loadCustom], which must only
 * return a family for a file it could actually read; anything missing or unreadable is the system font.
 */
@OptIn(ExperimentalTextApi::class)
fun appFontFamily(
    font: AppFont,
    customFile: Path?,
    loadCustom: (Path) -> FontFamily?,
): FontFamily =
    when (font) {
        AppFont.SYSTEM -> FlowFontFamily
        AppFont.CONDENSED -> FontFamily(CondensedWeights.map { SystemFont(CONDENSED_FAMILY, it) })
        AppFont.SERIF -> FontFamily.Serif
        AppFont.CUSTOM -> customFile?.takeIf { SystemFileSystem.metadataOrNull(it)?.isRegularFile == true }?.let(loadCustom) ?: FlowFontFamily
    }
