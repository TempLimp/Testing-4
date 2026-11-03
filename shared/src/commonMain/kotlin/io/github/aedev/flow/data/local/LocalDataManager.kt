package io.github.aedev.flow.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.profile.StoreScope
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.settings_custom_theme_default_name
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import io.github.aedev.flow.ui.theme.canonicalFamily
import io.github.aedev.flow.ui.theme.defaultVariant
import io.github.aedev.flow.ui.utils.getString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

val dataStore: DataStore<Preferences> by safePreferencesDataStore(name = "flow_preferences", scope = StoreScope.PROFILE_SETTINGS)

class LocalDataManager
    constructor() {
        companion object {
            private val THEME_MODE = stringPreferencesKey("theme_mode")
            private val ACCENT_COLOR = stringPreferencesKey("accent_color")
            private val SUBSCRIPTIONS = stringPreferencesKey("subscriptions")
            private val WATCH_HISTORY = stringPreferencesKey("watch_history")
            private val LIKED_VIDEOS = stringPreferencesKey("liked_videos")
            private val PLAYLISTS = stringPreferencesKey("playlists")
            private val SEARCH_HISTORY = stringSetPreferencesKey("search_history")
            private val VIDEO_QUALITY_WIFI = stringPreferencesKey("quality_wifi")
            private val VIDEO_QUALITY_CELLULAR = stringPreferencesKey("quality_cellular")
            private val BACKGROUND_PLAY = stringPreferencesKey("background_play")
            private val TRENDING_REGION = stringPreferencesKey("trending_region")
            private val LAST_UPDATE_CHECK = stringPreferencesKey("last_update_check")
            private val UPDATE_SKIPPED_VERSION = stringPreferencesKey("update_skipped_version")
            private val UPDATE_PROMPTED_VERSION = stringPreferencesKey("update_prompted_version")
            private val UPDATE_NOTIFIED_VERSION = stringPreferencesKey("update_notified_version")
            private val BEDTIME_REMINDER =
                androidx.datastore.preferences.core
                    .booleanPreferencesKey("bedtime_reminder")
            private val BEDTIME_START_HOUR =
                androidx.datastore.preferences.core
                    .intPreferencesKey("bedtime_start_hour")
            private val BEDTIME_START_MINUTE =
                androidx.datastore.preferences.core
                    .intPreferencesKey("bedtime_start_minute")
            private val BEDTIME_END_HOUR =
                androidx.datastore.preferences.core
                    .intPreferencesKey("bedtime_end_hour") // Optional, mostly for UI
            private val BEDTIME_END_MINUTE =
                androidx.datastore.preferences.core
                    .intPreferencesKey("bedtime_end_minute")

            private val BREAK_REMINDER =
                androidx.datastore.preferences.core
                    .booleanPreferencesKey("break_reminder")
            private val BREAK_FREQUENCY =
                androidx.datastore.preferences.core
                    .intPreferencesKey("break_frequency") // Minutes

            private val CUSTOM_THEME_COLORS = stringPreferencesKey("custom_theme_colors")
            private val CUSTOM_THEME_PALETTES = stringPreferencesKey("custom_theme_palettes_v2")
            private val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
            private val ACTIVE_CUSTOM_THEME = stringPreferencesKey("custom_theme_id")
            private const val LEGACY_CUSTOM_THEME_ID = "custom-android-legacy"
            private val THEME_VARIANT = stringPreferencesKey("theme_variant")
            private val SYSTEM_LIGHT_THEME_MODE = stringPreferencesKey("system_light_theme_mode")
            private val SYSTEM_DARK_THEME_MODE = stringPreferencesKey("system_dark_theme_mode")
            private val SYSTEM_DARK_THEME_VARIANT = stringPreferencesKey("system_dark_theme_variant")

            val AUTO_BACKUP_LAST_RUN =
                androidx.datastore.preferences.core
                    .longPreferencesKey("auto_backup_last_run")

            /** Keys kept in the device store and shared by every profile; see DevicePreferencesStore.kt. */
            internal val DEVICE_KEY_NAMES =
                setOf(
                    LAST_UPDATE_CHECK.name,
                    UPDATE_SKIPPED_VERSION.name,
                    UPDATE_PROMPTED_VERSION.name,
                    UPDATE_NOTIFIED_VERSION.name,
                    AUTO_BACKUP_LAST_RUN.name,
                )
        }

        enum class AutoBackupFrequency { NONE, DAILY, WEEKLY, MONTHLY }

        enum class AutoBackupType { APP_DATA, BRAIN, MASTER }

        // Update Settings
        val lastUpdateCheck: Flow<Long> =
            devicePreferencesDataStore.data.map { prefs ->
                prefs[LAST_UPDATE_CHECK]?.toLongOrNull() ?: 0L
            }

        suspend fun setLastUpdateCheck(timestamp: Long) {
            devicePreferencesDataStore.edit { prefs ->
                prefs[LAST_UPDATE_CHECK] = timestamp.toString()
            }
        }

        val skippedUpdateVersion: Flow<String?> = devicePreferencesDataStore.data.map { it[UPDATE_SKIPPED_VERSION] }

        suspend fun setSkippedUpdateVersion(version: String) {
            devicePreferencesDataStore.edit { it[UPDATE_SKIPPED_VERSION] = version }
        }

        val promptedUpdateVersion: Flow<String?> = devicePreferencesDataStore.data.map { it[UPDATE_PROMPTED_VERSION] }

        suspend fun setPromptedUpdateVersion(version: String) {
            devicePreferencesDataStore.edit { it[UPDATE_PROMPTED_VERSION] = version }
        }

        val notifiedUpdateVersion: Flow<String?> = devicePreferencesDataStore.data.map { it[UPDATE_NOTIFIED_VERSION] }

        suspend fun setNotifiedUpdateVersion(version: String) {
            devicePreferencesDataStore.edit { it[UPDATE_NOTIFIED_VERSION] = version }
        }

        // Theme Settings
        val themeMode: Flow<ThemeMode> =
            dataStore.data.map { prefs ->
                parseThemeMode(prefs[THEME_MODE], ThemeMode.SYSTEM)
            }

        suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { prefs ->
                prefs[THEME_MODE] = mode.canonicalFamily().name
            }
        }

        val themeVariant: Flow<ThemeVariant> =
            dataStore.data.map { prefs ->
                parseThemeVariant(
                    raw = prefs[THEME_VARIANT],
                    fallback = ThemeMode.storedDefaultVariant(prefs[THEME_MODE]) ?: ThemeVariant.DARK,
                )
            }

        suspend fun setThemeVariant(variant: ThemeVariant) {
            dataStore.edit { prefs -> prefs[THEME_VARIANT] = variant.name }
        }

        val systemLightThemeMode: Flow<ThemeMode> =
            dataStore.data.map { prefs ->
                parseThemeMode(prefs[SYSTEM_LIGHT_THEME_MODE], ThemeMode.DARK)
            }

        suspend fun setSystemLightThemeMode(mode: ThemeMode) {
            dataStore.edit { prefs ->
                prefs[SYSTEM_LIGHT_THEME_MODE] = mode.canonicalFamily().name
            }
        }

        val systemDarkThemeMode: Flow<ThemeMode> =
            dataStore.data.map { prefs ->
                parseThemeMode(prefs[SYSTEM_DARK_THEME_MODE], ThemeMode.DARK)
            }

        suspend fun setSystemDarkThemeMode(mode: ThemeMode) {
            dataStore.edit { prefs ->
                prefs[SYSTEM_DARK_THEME_MODE] = mode.canonicalFamily().name
            }
        }

        val systemDarkThemeVariant: Flow<ThemeVariant> =
            dataStore.data.map { prefs ->
                parseThemeVariant(
                    prefs[SYSTEM_DARK_THEME_VARIANT],
                    systemDarkFallbackVariant(prefs[SYSTEM_DARK_THEME_MODE]),
                )
            }

        suspend fun setSystemDarkThemeVariant(variant: ThemeVariant) {
            dataStore.edit { prefs -> prefs[SYSTEM_DARK_THEME_VARIANT] = variant.name }
        }

        /** A stored theme name as today's mode: retired palettes land on their successor, unknown names on [fallback]. */
        private fun parseThemeMode(
            raw: String?,
            fallback: ThemeMode,
        ): ThemeMode = (ThemeMode.fromStored(raw) ?: fallback).canonicalFamily()

        /** The dark slot never falls back to a light style. */
        private fun systemDarkFallbackVariant(raw: String?): ThemeVariant =
            (ThemeMode.storedDefaultVariant(raw) ?: ThemeVariant.DARK).let { if (it == ThemeVariant.LIGHT) ThemeVariant.DARK else it }

        private fun parseThemeVariant(
            raw: String?,
            fallback: ThemeVariant,
        ): ThemeVariant =
            runCatching {
                raw?.let(ThemeVariant::valueOf) ?: fallback
            }.getOrDefault(fallback)

        /**
         * The user's custom themes. Before this list existed there was one custom palette per style;
         * it is read once as a theme named "My theme" until the list is first written.
         */
        val customThemes: Flow<List<CustomTheme>> =
            dataStore.data.map { prefs -> readCustomThemes(prefs) }

        /** The custom theme the CUSTOM mode shows: the selected one, or the first when none is selected. */
        val activeCustomTheme: Flow<CustomTheme?> =
            dataStore.data.map { prefs ->
                val themes = readCustomThemes(prefs)
                themes.firstOrNull { it.id == prefs[ACTIVE_CUSTOM_THEME] } ?: themes.firstOrNull()
            }

        /** Adds [theme], or replaces the one with its id. */
        suspend fun saveCustomTheme(theme: CustomTheme) {
            dataStore.edit { prefs ->
                val themes = readCustomThemes(prefs)
                val updated =
                    if (themes.any { it.id == theme.id }) {
                        themes.map { if (it.id == theme.id) theme else it }
                    } else {
                        (themes + theme).take(CustomTheme.MAX_COUNT)
                    }
                prefs[CUSTOM_THEMES] = CustomThemeCodec.encodeList(updated)
            }
        }

        suspend fun deleteCustomTheme(id: String) {
            dataStore.edit { prefs ->
                val remaining = readCustomThemes(prefs).filterNot { it.id == id }
                prefs[CUSTOM_THEMES] = CustomThemeCodec.encodeList(remaining)
                if (prefs[ACTIVE_CUSTOM_THEME] == id) prefs.remove(ACTIVE_CUSTOM_THEME)
                if (remaining.isEmpty() && prefs[THEME_MODE] == ThemeMode.CUSTOM.name) prefs[THEME_MODE] = ThemeMode.DARK.name
            }
        }

        suspend fun setActiveCustomTheme(id: String) {
            dataStore.edit { prefs -> prefs[ACTIVE_CUSTOM_THEME] = id }
        }

        /** Selects [id] and switches the app to it in [variant]. */
        suspend fun useCustomTheme(
            id: String,
            variant: ThemeVariant,
        ) {
            dataStore.edit { prefs ->
                prefs[ACTIVE_CUSTOM_THEME] = id
                prefs[THEME_MODE] = ThemeMode.CUSTOM.name
                prefs[THEME_VARIANT] = variant.name
            }
        }

        /**
         * Adds [imported] to the list, giving a new id to any that clashes with an existing theme,
         * up to the limit. Returns how many were added.
         */
        suspend fun importCustomThemes(
            imported: List<CustomTheme>,
            newId: () -> String,
        ): Int {
            var added = 0
            dataStore.edit { prefs ->
                val themes = readCustomThemes(prefs).toMutableList()
                imported.forEach { theme ->
                    if (themes.size >= CustomTheme.MAX_COUNT) return@forEach
                    themes += if (themes.any { it.id == theme.id }) theme.copy(id = newId()) else theme
                    added++
                }
                prefs[CUSTOM_THEMES] = CustomThemeCodec.encodeList(themes)
            }
            return added
        }

        private suspend fun readCustomThemes(prefs: Preferences): List<CustomTheme> {
            prefs[CUSTOM_THEMES]?.let { return CustomThemeCodec.decode(it) }
            val legacy = decodeLegacyCustomPalettes(prefs[CUSTOM_THEME_PALETTES], prefs[CUSTOM_THEME_COLORS]) ?: return emptyList()
            return listOf(legacyCustomTheme(legacy, LEGACY_CUSTOM_THEME_ID, getString(Res.string.settings_custom_theme_default_name)))
        }

        // Subscriptions
        val subscriptions: Flow<List<Channel>> =
            dataStore.data.map { prefs ->
                val json = prefs[SUBSCRIPTIONS] ?: "[]"
                GsonCompatJson.decodeFromString<List<Channel>>(json)
            }

        suspend fun addSubscription(channel: Channel) {
            dataStore.edit { prefs ->
                val current: List<Channel> =
                    GsonCompatJson.decodeFromString<List<Channel>>(prefs[SUBSCRIPTIONS] ?: "[]")
                val updated = current.toMutableList()
                if (updated.none { it.id == channel.id }) {
                    updated.add(channel)
                    prefs[SUBSCRIPTIONS] = GsonCompatJson.encodeToString(updated)
                }
            }
        }

        suspend fun removeSubscription(channelId: String) {
            dataStore.edit { prefs ->
                val current: List<Channel> =
                    GsonCompatJson.decodeFromString<List<Channel>>(prefs[SUBSCRIPTIONS] ?: "[]")
                val updated = current.filter { it.id != channelId }
                prefs[SUBSCRIPTIONS] = GsonCompatJson.encodeToString(updated)
            }
        }

        // Watch History
        val watchHistory: Flow<List<Video>> =
            dataStore.data.map { prefs ->
                val json = prefs[WATCH_HISTORY] ?: "[]"
                GsonCompatJson.decodeFromString<List<Video>>(json)
            }

        suspend fun addToWatchHistory(video: Video) {
            if (PlayerPreferences().isDeepFlowCurrentlyActive()) return

            dataStore.edit { prefs ->
                val current: List<Video> =
                    GsonCompatJson.decodeFromString<List<Video>>(prefs[WATCH_HISTORY] ?: "[]")
                val updated = current.toMutableList()
                updated.removeAll { it.id == video.id }
                updated.add(0, video)
                if (updated.size > 500) {
                    updated.removeAt(updated.size - 1)
                }
                prefs[WATCH_HISTORY] = GsonCompatJson.encodeToString(updated)
            }
        }

        suspend fun clearWatchHistory() {
            dataStore.edit { prefs ->
                prefs[WATCH_HISTORY] = "[]"
            }
        }

        // Liked Videos
        val likedVideos: Flow<List<Video>> =
            dataStore.data.map { prefs ->
                val json = prefs[LIKED_VIDEOS] ?: "[]"
                GsonCompatJson.decodeFromString<List<Video>>(json)
            }

        suspend fun toggleLike(video: Video) {
            dataStore.edit { prefs ->
                val current: List<Video> =
                    GsonCompatJson.decodeFromString<List<Video>>(prefs[LIKED_VIDEOS] ?: "[]")
                val updated = current.toMutableList()
                if (updated.any { it.id == video.id }) {
                    updated.removeAll { it.id == video.id }
                } else {
                    updated.add(0, video)
                }
                prefs[LIKED_VIDEOS] = GsonCompatJson.encodeToString(updated)
            }
        }

        // Playlists
        val playlists: Flow<List<Playlist>> =
            dataStore.data.map { prefs ->
                val json = prefs[PLAYLISTS] ?: "[]"
                GsonCompatJson.decodeFromString<List<Playlist>>(json)
            }

        suspend fun createPlaylist(name: String): Playlist {
            val newPlaylist =
                Playlist(
                    id = "local_${Clock.System.now().toEpochMilliseconds()}",
                    name = name,
                    thumbnailUrl = "",
                    videoCount = 0,
                    isLocal = true,
                )
            dataStore.edit { prefs ->
                val current: List<Playlist> =
                    GsonCompatJson.decodeFromString<List<Playlist>>(prefs[PLAYLISTS] ?: "[]")
                val updated = current.toMutableList()
                updated.add(newPlaylist)
                prefs[PLAYLISTS] = GsonCompatJson.encodeToString(updated)
            }
            return newPlaylist
        }

        suspend fun addVideoToPlaylist(
            playlistId: String,
            video: Video,
        ) {
            dataStore.edit { prefs ->
                val current: List<Playlist> =
                    GsonCompatJson.decodeFromString<List<Playlist>>(prefs[PLAYLISTS] ?: "[]")
                val updated =
                    current.map { playlist ->
                        if (playlist.id == playlistId) {
                            val videos = playlist.videos.toMutableList()
                            if (videos.none { it.id == video.id }) {
                                videos.add(video)
                            }
                            playlist.copy(
                                videos = videos,
                                videoCount = videos.size,
                                thumbnailUrl = videos.firstOrNull()?.thumbnailUrl ?: "",
                            )
                        } else {
                            playlist
                        }
                    }
                prefs[PLAYLISTS] = GsonCompatJson.encodeToString(updated)
            }
        }

        // Search History
        val searchHistory: Flow<List<String>> =
            dataStore.data.map { prefs ->
                prefs[SEARCH_HISTORY]?.toList() ?: emptyList()
            }

        suspend fun addSearchQuery(query: String) {
            dataStore.edit { prefs ->
                val current = prefs[SEARCH_HISTORY]?.toMutableSet() ?: mutableSetOf()
                current.add(query)
                if (current.size > 20) {
                    current.remove(current.first())
                }
                prefs[SEARCH_HISTORY] = current
            }
        }

        suspend fun clearSearchHistory() {
            dataStore.edit { prefs ->
                prefs[SEARCH_HISTORY] = emptySet()
            }
        }

        // Settings
        val trendingRegion: Flow<String> =
            dataStore.data.map { prefs ->
                prefs[TRENDING_REGION] ?: "US"
            }

        suspend fun setTrendingRegion(region: String) {
            dataStore.edit { prefs ->
                prefs[TRENDING_REGION] = region
            }
        }

        val bedtimeReminder: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[BEDTIME_REMINDER] ?: false
            }

        val breakReminder: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[BREAK_REMINDER] ?: false
            }

        suspend fun setBedtimeReminder(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[BEDTIME_REMINDER] = enabled
            }
        }

        val bedtimeStartHour: Flow<Int> = dataStore.data.map { prefs -> prefs[BEDTIME_START_HOUR] ?: 23 } // Default 11 PM
        val bedtimeStartMinute: Flow<Int> = dataStore.data.map { prefs -> prefs[BEDTIME_START_MINUTE] ?: 0 }
        val bedtimeEndHour: Flow<Int> = dataStore.data.map { prefs -> prefs[BEDTIME_END_HOUR] ?: 7 } // Default 7 AM
        val bedtimeEndMinute: Flow<Int> = dataStore.data.map { prefs -> prefs[BEDTIME_END_MINUTE] ?: 0 }

        suspend fun setBedtimeSchedule(
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int,
        ) {
            dataStore.edit { prefs ->
                prefs[BEDTIME_START_HOUR] = startHour
                prefs[BEDTIME_START_MINUTE] = startMinute
                prefs[BEDTIME_END_HOUR] = endHour
                prefs[BEDTIME_END_MINUTE] = endMinute
            }
        }

        suspend fun setBreakReminder(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[BREAK_REMINDER] = enabled
            }
        }

        val breakFrequency: Flow<Int> = dataStore.data.map { prefs -> prefs[BREAK_FREQUENCY] ?: 30 } // Default 30 min

        suspend fun setBreakFrequency(minutes: Int) {
            dataStore.edit { prefs ->
                prefs[BREAK_FREQUENCY] = minutes
            }
        }

        suspend fun getExportData(): SettingsBackup {
            val prefs = dataStore.data.first()
            val strings = mutableMapOf<String, String>()
            val booleans = mutableMapOf<String, Boolean>()
            val ints = mutableMapOf<String, Int>()
            val floats = mutableMapOf<String, Float>()
            val longs = mutableMapOf<String, Long>()

            prefs.asMap().entries.forEach { (key, value) ->
                val name = key.name
                if (name == "theme_mode" || name == "theme_variant" || name == "accent_color" ||
                    name == "custom_theme_colors" || name == "custom_theme_palettes_v2" ||
                    name == "custom_themes" || name == "custom_theme_id" ||
                    name == "system_light_theme_mode" || name == "system_dark_theme_mode" ||
                    name == "system_dark_theme_variant" ||
                    name == "bedtime_reminder" || name == "break_reminder" ||
                    name.startsWith("bedtime_") || name == "break_frequency"
                ) {
                    when (value) {
                        is String -> strings[name] = value
                        is Boolean -> booleans[name] = value
                        is Int -> ints[name] = value
                        is Float -> floats[name] = value
                        is Long -> longs[name] = value
                    }
                }
            }

            strings[THEME_MODE.name] = parseThemeMode(prefs[THEME_MODE], ThemeMode.SYSTEM).name
            strings[THEME_VARIANT.name] =
                parseThemeVariant(
                    prefs[THEME_VARIANT],
                    ThemeMode.storedDefaultVariant(prefs[THEME_MODE]) ?: ThemeVariant.DARK,
                ).name
            strings[SYSTEM_LIGHT_THEME_MODE.name] = parseThemeMode(prefs[SYSTEM_LIGHT_THEME_MODE], ThemeMode.DARK).name
            strings[SYSTEM_DARK_THEME_MODE.name] = parseThemeMode(prefs[SYSTEM_DARK_THEME_MODE], ThemeMode.DARK).name
            strings[SYSTEM_DARK_THEME_VARIANT.name] =
                parseThemeVariant(prefs[SYSTEM_DARK_THEME_VARIANT], systemDarkFallbackVariant(prefs[SYSTEM_DARK_THEME_MODE])).name
            strings[CUSTOM_THEMES.name] = CustomThemeCodec.encodeList(readCustomThemes(prefs))
            return SettingsBackup(strings, booleans, ints, floats, longs)
        }

        suspend fun restoreData(backup: SettingsBackup) {
            dataStore.edit { prefs ->
                val restoredThemeMode = ThemeMode.fromStored(backup.strings["theme_mode"])
                restoredThemeMode?.let { mode ->
                    prefs[THEME_MODE] = mode.canonicalFamily().name
                    prefs[THEME_VARIANT] =
                        parseThemeVariant(
                            backup.strings["theme_variant"],
                            ThemeMode.storedDefaultVariant(backup.strings["theme_mode"]) ?: ThemeVariant.DARK,
                        ).name
                }
                ThemeMode.fromStored(backup.strings["system_light_theme_mode"])?.let { mode ->
                    prefs[SYSTEM_LIGHT_THEME_MODE] = mode.canonicalFamily().name
                }
                val restoredSystemDarkMode = ThemeMode.fromStored(backup.strings["system_dark_theme_mode"])
                restoredSystemDarkMode?.let { mode ->
                    prefs[SYSTEM_DARK_THEME_MODE] = mode.canonicalFamily().name
                    prefs[SYSTEM_DARK_THEME_VARIANT] =
                        parseThemeVariant(
                            backup.strings["system_dark_theme_variant"],
                            systemDarkFallbackVariant(backup.strings["system_dark_theme_mode"]),
                        ).name
                }

                if (restoredThemeMode == null) {
                    backup.strings["theme_variant"]
                        ?.let { raw -> runCatching { ThemeVariant.valueOf(raw) }.getOrNull() }
                        ?.let { prefs[THEME_VARIANT] = it.name }
                }
                if (restoredSystemDarkMode == null) {
                    backup.strings["system_dark_theme_variant"]
                        ?.let { raw -> runCatching { ThemeVariant.valueOf(raw) }.getOrNull() }
                        ?.let { prefs[SYSTEM_DARK_THEME_VARIANT] = it.name }
                }

                backup.strings["accent_color"]?.let { prefs[ACCENT_COLOR] = it }
                restoredCustomThemes(backup)?.let { restored ->
                    val merged = (readCustomThemes(prefs).filterNot { current -> restored.any { it.id == current.id } } + restored)
                    prefs[CUSTOM_THEMES] = CustomThemeCodec.encodeList(merged.take(CustomTheme.MAX_COUNT))
                }
                backup.strings["custom_theme_id"]?.let { prefs[ACTIVE_CUSTOM_THEME] = it }
                backup.booleans.forEach { (k, v) ->
                    if (k == "bedtime_reminder" || k == "break_reminder") {
                        prefs[
                            androidx.datastore.preferences.core
                                .booleanPreferencesKey(k),
                        ] = v
                    }
                }
                backup.ints.forEach { (k, v) ->
                    if (k.startsWith("bedtime_") || k == "break_frequency") {
                        prefs[
                            androidx.datastore.preferences.core
                                .intPreferencesKey(k),
                        ] = v
                    }
                }
            }
        }

        /** The custom themes a backup carries: the list, or an older backup's single palette as one theme. */
        private suspend fun restoredCustomThemes(backup: SettingsBackup): List<CustomTheme>? {
            backup.strings["custom_themes"]?.let { return CustomThemeCodec.decode(it) }
            val legacy =
                decodeLegacyCustomPalettes(backup.strings["custom_theme_palettes_v2"], backup.strings["custom_theme_colors"])
                    ?: return null
            return listOf(legacyCustomTheme(legacy, LEGACY_CUSTOM_THEME_ID, getString(Res.string.settings_custom_theme_default_name)))
        }

        val autoBackupLastRun: Flow<Long> =
            devicePreferencesDataStore.data.map { prefs ->
                prefs[AUTO_BACKUP_LAST_RUN] ?: 0L
            }

        suspend fun setAutoBackupLastRun(timestamp: Long) {
            devicePreferencesDataStore.edit { prefs ->
                prefs[AUTO_BACKUP_LAST_RUN] = timestamp
            }
        }
    }
