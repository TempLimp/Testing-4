package io.github.aedev.flow.data.local

import androidx.datastore.preferences.core.edit
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.utils.AppLanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** A restored language used to reach only the preference YouTube read, never the UI (#1277). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLanguageRestoreTest {
    // Robolectric gave upstream a fresh app per test, with a main thread to apply the language on; the port's
    // stores are files the whole run shares, and the test runner has no main dispatcher of its own.
    @BeforeTest
    fun clearStores() =
        runBlocking<Unit> {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            playerPreferencesDataStore.edit { it.clear() }
            devicePreferencesDataStore.edit { it.clear() }
        }

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun aRestoredBackupAppliesItsAppLanguageToTheUI() =
        runBlocking {
            AppLanguageManager.saveLanguageTag("ru")
            val preferences = PlayerPreferences()

            preferences.restoreData(SettingsBackup(strings = mapOf("app_language" to "en")))

            assertThat(preferences.appLanguage.first()).isEqualTo("en")
            assertThat(AppLanguageManager.loadSelectedLanguageTag()).isEqualTo("en")
            assertThat(AppLanguageManager.appliedLanguage.value).isEqualTo("en")
        }

    @Test
    fun aBackupWithoutALanguageLeavesTheUILanguageAlone() =
        runBlocking {
            AppLanguageManager.saveLanguageTag("ru")

            PlayerPreferences().restoreData(SettingsBackup(strings = mapOf("trending_region" to "RU")))

            assertThat(AppLanguageManager.appliedLanguage.value).isEqualTo("ru")
        }
}