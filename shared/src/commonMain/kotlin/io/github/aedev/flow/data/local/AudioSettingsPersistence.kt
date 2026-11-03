package io.github.aedev.flow.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.concurrent.Volatile

/** Shared with the equalizer store: one file may only ever have one DataStore instance. */
internal val audioSettingsDataStore: DataStore<Preferences> by safePreferencesDataStore(
    name = "audio_settings",
    scope = StoreScope.PROFILE_SETTINGS,
)

class AudioSettingsPersistence private constructor() {
    companion object : SynchronizedObject() {
        private val PITCH_KEY = floatPreferencesKey("pitch_level")
        private val SPEED_KEY = floatPreferencesKey("speed_level")

        @Volatile
        private var instance: AudioSettingsPersistence? = null

        fun getInstance(): AudioSettingsPersistence =
            instance ?: synchronized(this) {
                instance ?: AudioSettingsPersistence().also { instance = it }
            }
    }

    data class AudioSettings(
        val pitch: Float = 0.0f,
        val speed: Float = 1.0f,
    )

    val settingsFlow: Flow<AudioSettings> =
        audioSettingsDataStore.data
            .map { preferences ->
                AudioSettings(
                    pitch = preferences[PITCH_KEY] ?: 0.0f,
                    speed = preferences[SPEED_KEY] ?: 1.0f,
                )
            }

    suspend fun savePitch(pitch: Float) {
        audioSettingsDataStore.edit { it[PITCH_KEY] = pitch }
    }

    suspend fun saveSpeed(speed: Float) {
        audioSettingsDataStore.edit { it[SPEED_KEY] = speed }
    }
}
