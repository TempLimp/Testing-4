package io.github.aedev.flow.data.local

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test

class WatchedVideoPreferenceMigrationTest {

    @Test
    fun splitPreferenceInheritsEnabledLegacyValueWhenUnset() {
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = null, legacyValue = true)
        ).isTrue()
    }

    @Test
    fun splitPreferenceInheritsDisabledLegacyValueWhenUnset() {
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = null, legacyValue = false)
        ).isFalse()
    }

    @Test
    fun splitPreferenceOverridesLegacyValueOnceChanged() {
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = false, legacyValue = true)
        ).isFalse()
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = true, legacyValue = false)
        ).isTrue()
    }

    @Test
    fun newInstallsDefaultSplitPreferenceToDisabled() {
        assertThat(
            resolveMigratedHideWatchedPreference(splitValue = null, legacyValue = null)
        ).isFalse()
    }
}
