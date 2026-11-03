package io.github.aedev.flow.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppLanguageOption(
    val tag: String,
    val nativeName: String,
    val localizedName: String,
)

/**
 * Upstream keeps the chosen language in SharedPreferences and hands it to Android 13's per-app language. iOS has a
 * per-app language too (Settings › Flow › Language, the app's `AppleLanguages`), which plays Android 13's part here.
 * Android's Context wrapping (`wrapContext`, `activityContext`) has no iOS counterpart: the system applies the
 * per-app language to the whole process when the app starts.
 */
object AppLanguageManager {
    const val SYSTEM_DEFAULT = "system"
    private const val PREFS_FILE = "flow_language_prefs"
    private const val PREFS_KEY = "app_language_tag"
    private const val HANDED_OVER_KEY = "app_language_handed_to_system"

    private val supportedLanguageTags = APP_LANGUAGE_TAGS.split(',')

    private val applied = MutableStateFlow<String?>(null)

    private val prefs by lazy { sharedPreferences(PREFS_FILE) }

    /**
     * The language the UI is shown in, once resolved at start or saved. YouTube's `hl` follows this,
     * not the preference that mirrors it: a restored or synced preference never reached the UI.
     */
    val appliedLanguage: StateFlow<String?> = applied.asStateFlow()

    /** iOS keeps the per-app language itself, shared with Settings › Flow › Language, as Android 13+ does. */
    val systemStoresLanguage: Boolean get() = true

    fun loadSelectedLanguageTag(): String = prefs.getString(PREFS_KEY, SYSTEM_DEFAULT) ?: SYSTEM_DEFAULT

    fun saveLanguageTag(tag: String) {
        prefs
            .edit()
            .putString(PREFS_KEY, tag)
            .apply()
        applied.value = normalizeLanguageTag(tag)
    }

    /** Saves [tag] and hands it to the system, which shows it from the app's next start. */
    fun applyLanguageTag(tag: String) {
        saveLanguageTag(tag)
        setSystemLanguageTag(tag)
    }

    /**
     * The language to start with. A choice made in Settings › Flow › Language wins and is saved here; a
     * choice saved before the system kept one is handed over to it once.
     */
    fun resolveLanguageTag(): String {
        val stored = loadSelectedLanguageTag()
        val resolution =
            resolveAgainstSystem(
                stored = normalizeLanguageTag(stored),
                system = systemLanguageTag(),
                handedOver = prefs.getBoolean(HANDED_OVER_KEY, false),
            )
        if (resolution.pushToSystem) setSystemLanguageTag(resolution.tag)
        if (!prefs.getBoolean(HANDED_OVER_KEY, false)) prefs.edit().putBoolean(HANDED_OVER_KEY, true).apply()
        if (resolution.tag != stored) saveLanguageTag(resolution.tag)
        applied.value = resolution.tag
        return resolution.tag
    }

    internal data class LanguageResolution(
        val tag: String,
        val pushToSystem: Boolean,
    )

    internal fun resolveAgainstSystem(
        stored: String,
        system: String,
        handedOver: Boolean,
    ): LanguageResolution =
        if (handedOver || system != SYSTEM_DEFAULT || stored == SYSTEM_DEFAULT) {
            LanguageResolution(tag = system, pushToSystem = false)
        } else {
            LanguageResolution(tag = stored, pushToSystem = true)
        }

    private fun systemLanguageTag(): String = normalizeLanguageTag(systemAppLanguageTag())

    private fun setSystemLanguageTag(tag: String) {
        val normalized = normalizeLanguageTag(tag)
        setSystemAppLanguageTag(normalized.takeUnless { it == SYSTEM_DEFAULT })
    }

    fun getSupportedLanguages(displayLocale: Locale = Locale.getDefault()): List<AppLanguageOption> =
        supportedLanguageTags
            .map { tag ->
                val locale = localeFromTag(tag)
                AppLanguageOption(
                    tag = tag,
                    nativeName =
                        locale.getDisplayName(locale).replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
                        },
                    localizedName =
                        locale.getDisplayName(displayLocale).replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(displayLocale) else ch.toString()
                        },
                )
            }.sortedBy { it.localizedName }

    fun getLanguageLabel(
        tag: String,
        displayLocale: Locale = Locale.getDefault(),
    ): String {
        val normalizedTag = normalizeLanguageTag(tag)
        if (normalizedTag == SYSTEM_DEFAULT) {
            return ""
        }

        val locale = localeFromTag(normalizedTag)
        return locale.getDisplayName(displayLocale).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(displayLocale) else ch.toString()
        }
    }

    fun normalizeLanguageTag(rawTag: String?): String {
        val value = rawTag?.trim().orEmpty()
        if (value.isEmpty() || value.equals(SYSTEM_DEFAULT, ignoreCase = true)) {
            return SYSTEM_DEFAULT
        }

        return when (value.lowercase()) {
            "in", "id-id" -> "id"
            "pt-rbr", "pt_br", "pt-br" -> "pt-BR"
            else -> localeFromTag(value).toLanguageTag().takeIf { it.isNotBlank() } ?: value
        }
    }

    private fun localeFromTag(tag: String): Locale {
        val normalizedTag =
            when (tag) {
                "id" -> "id"
                else -> tag
            }
        return Locale.forLanguageTag(normalizedTag)
    }
}

/** The per-app language the system holds (the first of the app's own `AppleLanguages`), or null when following the device. */
internal expect fun systemAppLanguageTag(): String?

/** Sets the per-app language the system shows from the next start; null follows the device again. */
internal expect fun setSystemAppLanguageTag(tag: String?)
