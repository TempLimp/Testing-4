package io.github.aedev.flow.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class ExportedPreferencesTest {
    private val quality = stringPreferencesKey("default_quality_wifi")
    private val autoplay = booleanPreferencesKey("autoplay_enabled")
    private val deviceNames = setOf(quality.name)

    private fun stored(vararg entries: Pair<Preferences.Key<*>, Any>) = mapOf(*entries)

    @Test
    fun aCopyLeavesTheProfileStoreSStaleDeviceKeysBehind() {
        val exported = exportedPreferences(stored(Pair(quality, "480p"), Pair(autoplay, true)), emptyMap(), deviceNames)

        assertThat(exported).containsExactly(autoplay, true)
    }

    @Test
    fun aFullBackupTakesDeviceKeysFromTheDeviceStoreOnly() {
        val exported = exportedPreferences(stored(Pair(quality, "480p"), Pair(autoplay, true)), stored(Pair(quality, "1080p")), deviceNames)

        assertThat(exported).containsExactly(autoplay, true, quality, "1080p")
    }
}
