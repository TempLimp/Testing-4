package io.github.aedev.flow.data.audio.eq

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.local.audioSettingsDataStore
import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.eq_my_curve
import io.github.aedev.flow.ui.utils.getString
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface EqStatePersistence {
    suspend fun load(): EqState

    suspend fun save(state: EqState)
}

/** The equalizer as one JSON value, also the form it takes in backups and device sync. */
object EqStateJson {
    /** The settings key the state is stored, backed up and synced under. */
    const val KEY = "eq_state_json"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(state: EqState): String = json.encodeToString(EqState.serializer(), state)

    fun decode(raw: String): EqState? = runCatching { json.decodeFromString(EqState.serializer(), raw) }.getOrNull()?.sanitized()

    @OptIn(ExperimentalUuidApi::class)
    fun newPresetId(): String = "user:${Uuid.random()}"
}

class EqStateStore : EqStatePersistence {
        override suspend fun load(): EqState =
            runCatching {
                val preferences = audioSettingsDataStore.data.first()
                preferences[STATE]?.let(EqStateJson::decode)?.let { return@runCatching it }
                val migrated =
                    EqLegacyMigration.migrate(
                        profileName = preferences[LEGACY_PROFILE],
                        bassBoost = preferences[LEGACY_BASS_BOOST],
                        customCurveJson = preferences[LEGACY_CUSTOM_CURVE],
                        customPresetsJson = preferences[LEGACY_CUSTOM_PRESETS],
                        myCurveName = getString(Res.string.eq_my_curve),
                        newId = EqStateJson::newPresetId,
                    )
                audioSettingsDataStore.edit { store ->
                    store[STATE] = EqStateJson.encode(migrated)
                    LEGACY_KEYS.forEach { store.remove(it) }
                }
                migrated
            }.getOrElse { error ->
                Log.e(TAG, "Equalizer state unreadable, starting flat", error)
                EqState()
            }

        override suspend fun save(state: EqState) {
            audioSettingsDataStore.edit { it[STATE] = EqStateJson.encode(state) }
        }

        private companion object {
            const val TAG = "EqStateStore"
            val STATE = stringPreferencesKey(EqStateJson.KEY)
            val LEGACY_PROFILE = stringPreferencesKey("eq_profile_name")
            val LEGACY_BASS_BOOST = floatPreferencesKey("bass_boost_strength")
            val LEGACY_CUSTOM_CURVE = stringPreferencesKey("custom_eq_profile_json")
            val LEGACY_CUSTOM_PRESETS = stringPreferencesKey("custom_eq_presets_json")
            val LEGACY_KEYS =
                listOf(
                    LEGACY_PROFILE,
                    LEGACY_BASS_BOOST,
                    LEGACY_CUSTOM_CURVE,
                    LEGACY_CUSTOM_PRESETS,
                    floatPreferencesKey("virtualizer_strength"),
                    floatPreferencesKey("volume_multiplier"),
                )
        }
    }
