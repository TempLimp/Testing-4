package io.github.aedev.flow.data.profile

import io.github.aedev.flow.resources.Res
import io.github.aedev.flow.resources.profile_default_name
import io.github.aedev.flow.ui.utils.getString

/**
 * Sync belongs to Default: its ledgers, CRDTs and device id describe that one profile's data, so another profile
 * syncing under the same device id would corrupt both.
 */
object SyncProfile {
    fun syncsHere(): Boolean = ActiveProfile.get().isDefault

    /** The name to show for the profile that syncs. */
    suspend fun name(state: ProfileRegistryState): String =
        state.profiles.firstOrNull { it.id == state.defaultId }?.displayName() ?: getString(Res.string.profile_default_name)
}
