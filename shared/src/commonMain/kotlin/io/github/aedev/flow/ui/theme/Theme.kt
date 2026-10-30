package io.github.aedev.flow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * What the theme preference points at: a built-in palette, Material You, the active custom theme,
 * or [SYSTEM] to follow the device's light and dark setting.
 *
 * Stored by name, so a name is never reused or renamed; retired palettes are mapped in [fromStored].
 * [LIGHT] and [OLED] are older spellings of Flow Default's light and AMOLED styles.
 */
enum class ThemeMode {
    SYSTEM,
    DARK,
    MONOCHROME,
    CATPPUCCIN,
    GREEN_APPLE,
    LAVENDER_MIST,
    NORDIC_HORIZON,
    TAKO,
    YIN_YANG,
    STRAWBERRY_DAIQUIRI,
    KANAGAWA,
    TOKYO_NIGHT,
    ROSE_PINE,
    EVERFOREST,
    GRUVBOX,
    DRACULA,
    SOLARIZED,
    TIDE,
    SAGE,
    CAFFEINE,
    CLAUDE,
    OCEAN_BLUE,
    GUNMETAL,
    COSMIC_VOID,
    CYBERPUNK,
    ROYAL_GOLD,
    CREAM_LIGHT,
    MATERIAL_YOU,
    CUSTOM,
    LIGHT,
    OLED,
    ;

    companion object {
        /**
         * Palettes retired when the catalogue moved to Flow Desktop's, and the palette and style each
         * one lands on. Stored preferences and old backups still carry these names.
         */
        private val retired: Map<String, Pair<ThemeMode, ThemeVariant>> =
            mapOf(
                "FOREST_GREEN" to (EVERFOREST to ThemeVariant.DARK),
                "SUNSET_ORANGE" to (CLAUDE to ThemeVariant.DARK),
                "PURPLE_NEBULA" to (COSMIC_VOID to ThemeVariant.DARK),
                "MIDNIGHT_BLACK" to (TIDE to ThemeVariant.AMOLED),
                "ROSE_GOLD" to (STRAWBERRY_DAIQUIRI to ThemeVariant.DARK),
                "ARCTIC_ICE" to (TIDE to ThemeVariant.DARK),
                "CRIMSON_RED" to (DARK to ThemeVariant.DARK),
                "MINTY_FRESH" to (TIDE to ThemeVariant.DARK),
                "SOLAR_FLARE" to (GRUVBOX to ThemeVariant.DARK),
                "ESPRESSO" to (CAFFEINE to ThemeVariant.DARK),
                "MINT_LIGHT" to (TIDE to ThemeVariant.LIGHT),
                "ROSE_LIGHT" to (STRAWBERRY_DAIQUIRI to ThemeVariant.LIGHT),
                "SKY_LIGHT" to (NORDIC_HORIZON to ThemeVariant.LIGHT),
            )

        /** Every retired name, for tests and for the backup reader. */
        val retiredNames: Set<String> get() = retired.keys

        /** The mode a stored name means today, or null for a name that never existed. */
        fun fromStored(raw: String?): ThemeMode? {
            if (raw.isNullOrBlank()) return null
            retired[raw]?.let { return it.first }
            return entries.firstOrNull { it.name == raw }
        }

        /** The style a stored name implies, used only when no style was stored alongside it. */
        fun storedDefaultVariant(raw: String?): ThemeVariant? = retired[raw]?.second ?: fromStored(raw)?.defaultVariant()
    }
}

fun ThemeMode.defaultVariant(): ThemeVariant =
    when (this) {
        ThemeMode.LIGHT, ThemeMode.CREAM_LIGHT -> ThemeVariant.LIGHT
        ThemeMode.OLED -> ThemeVariant.AMOLED
        else -> ThemeVariant.DARK
    }

fun ThemeMode.canonicalFamily(): ThemeMode =
    when (this) {
        ThemeMode.LIGHT, ThemeMode.OLED -> ThemeMode.DARK
        else -> this
    }

fun ThemeMode.resolveSystemDefault(
    isSystemDark: Boolean,
    systemLightThemeMode: ThemeMode = ThemeMode.DARK,
    systemDarkThemeMode: ThemeMode = ThemeMode.DARK,
): ThemeMode {
    if (this != ThemeMode.SYSTEM) return canonicalFamily()

    val selectedMode = if (isSystemDark) systemDarkThemeMode else systemLightThemeMode
    return if (selectedMode == ThemeMode.SYSTEM) ThemeMode.DARK else selectedMode.canonicalFamily()
}

fun ThemeMode.isEffectivelyDark(
    isSystemDark: Boolean,
    systemLightThemeMode: ThemeMode = ThemeMode.DARK,
    systemDarkThemeMode: ThemeMode = ThemeMode.DARK,
    themeVariant: ThemeVariant? = null,
): Boolean {
    if (this == ThemeMode.SYSTEM) return isSystemDark
    themeVariant?.let { return it != ThemeVariant.LIGHT }
    return when (resolveSystemDefault(isSystemDark, systemLightThemeMode, systemDarkThemeMode)) {
        ThemeMode.LIGHT, ThemeMode.CREAM_LIGHT -> false
        ThemeMode.SYSTEM, ThemeMode.MATERIAL_YOU -> isSystemDark
        else -> true
    }
}

data class ExtendedColors(
    val textSecondary: Color,
    val border: Color,
    val success: Color,
    val shortsAccent: Color,
)

val LocalExtendedColors =
    staticCompositionLocalOf {
        ExtendedColors(
            textSecondary = Color.Unspecified,
            border = Color.Unspecified,
            success = Color.Unspecified,
            shortsAccent = Color.Unspecified,
        )
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeVariant: ThemeVariant = ThemeVariant.DARK,
    customTheme: CustomTheme? = null,
    systemLightThemeMode: ThemeMode = ThemeMode.DARK,
    systemDarkThemeMode: ThemeMode = ThemeMode.DARK,
    systemDarkThemeVariant: ThemeVariant = ThemeVariant.DARK,
    fontFamily: FontFamily = FlowFontFamily,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        resolveFlowColorScheme(
            isSystemDark = isSystemInDarkTheme(),
            themeMode = themeMode,
            themeVariant = themeVariant,
            customTheme = customTheme,
            systemLightThemeMode = systemLightThemeMode,
            systemDarkThemeMode = systemDarkThemeMode,
            systemDarkThemeVariant = systemDarkThemeVariant,
        )

    val extendedColors =
        ExtendedColors(
            textSecondary = colorScheme.onSurfaceVariant,
            border = colorScheme.outlineVariant,
            success = colorScheme.tertiary,
            // Brand mark, not a scheme role: the Shorts glyph is red in every theme.
            shortsAccent = YouTubeRed,
        )

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = remember(fontFamily) { flowTypography(fontFamily) },
            content = content,
        )
    }
}

/**
 * Non-composable resolution of the app's active color scheme. Single source of truth
 * shared by [FlowTheme] and the home-screen widgets, so widgets always render in the
 * exact palette the user selected in-app (including custom themes).
 *
 * iOS has no system dynamic color, so [ThemeMode.MATERIAL_YOU] resolves like upstream does below
 * Android 12: through [FlowPalettes.forMode], which falls back to Flow Default.
 */
fun resolveFlowColorScheme(
    isSystemDark: Boolean,
    themeMode: ThemeMode,
    themeVariant: ThemeVariant,
    customTheme: CustomTheme?,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    systemDarkThemeVariant: ThemeVariant,
): ColorScheme {
    val (effectiveThemeMode, effectiveVariant) =
        resolveFlowThemeSlot(isSystemDark, themeMode, themeVariant, systemLightThemeMode, systemDarkThemeMode, systemDarkThemeVariant)
    return when {
        effectiveThemeMode == ThemeMode.CUSTOM && customTheme != null -> {
            customTheme.colorsFor(effectiveVariant).toColorScheme(effectiveVariant)
        }

        else -> {
            FlowPalettes.forMode(effectiveThemeMode).colorsFor(effectiveVariant).toColorScheme(effectiveVariant)
        }
    }
}

/** The palette and variant the app shows while the system is in one night mode. */
data class FlowThemeSlot(
    val mode: ThemeMode,
    val variant: ThemeVariant,
)

fun resolveFlowThemeSlot(
    isSystemDark: Boolean,
    themeMode: ThemeMode,
    themeVariant: ThemeVariant,
    systemLightThemeMode: ThemeMode,
    systemDarkThemeMode: ThemeMode,
    systemDarkThemeVariant: ThemeVariant,
): FlowThemeSlot =
    FlowThemeSlot(
        mode = themeMode.resolveSystemDefault(isSystemDark, systemLightThemeMode, systemDarkThemeMode),
        variant =
            if (themeMode == ThemeMode.SYSTEM) {
                if (isSystemDark) systemDarkThemeVariant else ThemeVariant.LIGHT
            } else {
                themeVariant
            },
    )

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current
