package io.github.aedev.flow.data.recommendation.music

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FavouriteArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String = "",
)

private val favouriteArtistsDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "favourite_artists", scope = StoreScope.PROFILE_LIBRARY)

/**
 * The artists the viewer picked by hand. The music brain only keeps their affinity, which listening
 * keeps changing, so the picks themselves live here for the picker to show.
 */
class FavouriteArtistsStore
    constructor(
        private val musicBrain: MusicBrainEngine,
    ) {
        private val key = stringPreferencesKey("artists")
        private val json = Json { ignoreUnknownKeys = true }

        val artists: Flow<List<FavouriteArtist>> = favouriteArtistsDataStore.data.map { decode(it[key]) }

        suspend fun setFavourite(
            artist: FavouriteArtist,
            favourite: Boolean,
        ) {
            favouriteArtistsDataStore.edit { preferences ->
                val others = decode(preferences[key]).filterNot { it.id == artist.id }
                preferences[key] = json.encodeToString(if (favourite) others + artist else others)
            }
            musicBrain.setFavouriteArtist(artist.id, artist.name, favourite)
        }

        suspend fun currentIds(): Set<String> = artists.first().mapTo(HashSet(), FavouriteArtist::id)

        /** The picks for the master backup; null when there are none. */
        suspend fun export(): ByteArray? = artists.first().takeIf { it.isNotEmpty() }?.let { json.encodeToString(it).encodeToByteArray() }

        /** Adds the backed-up picks to the current ones and lifts each again in the music brain the backup just restored. */
        suspend fun restore(bytes: ByteArray) {
            val restored = decode(bytes.decodeToString())
            if (restored.isEmpty()) return
            favouriteArtistsDataStore.edit { preferences ->
                val current = decode(preferences[key])
                preferences[key] = json.encodeToString((current + restored).distinctBy { it.id })
            }
            restored.forEach { musicBrain.setFavouriteArtist(it.id, it.name, favourite = true) }
        }

        private fun decode(raw: String?): List<FavouriteArtist> =
            raw?.let { runCatching { json.decodeFromString<List<FavouriteArtist>>(it) }.getOrNull() }.orEmpty()
    }
