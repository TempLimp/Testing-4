package io.github.aedev.flow.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.flow.first

private val savedPlaylistSyncDataStore: DataStore<Preferences> by
    safePreferencesDataStore(name = "saved_playlist_sync", scope = StoreScope.PROFILE_LIBRARY)

/** When each saved playlist was last checked against every page of its YouTube original. */
class SavedPlaylistSyncStore
    constructor() {
        private val dataStore = savedPlaylistSyncDataStore

        suspend fun syncedAt(playlistId: String): Long? = dataStore.data.first()[key(playlistId)]

        suspend fun markSynced(
            playlistId: String,
            atMillis: Long,
        ) {
            dataStore.edit { it[key(playlistId)] = atMillis }
        }

        private fun key(playlistId: String) = longPreferencesKey("synced_at:$playlistId")
    }
