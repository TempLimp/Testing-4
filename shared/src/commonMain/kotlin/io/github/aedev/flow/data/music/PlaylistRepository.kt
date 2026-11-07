package io.github.aedev.flow.data.music

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.local.GsonCompatJson
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.withTypedArtists
import io.github.aedev.flow.data.profile.StoreScope
import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val playlistDataStore: DataStore<Preferences> by safePreferencesDataStore(
    name = "playlists",
    scope = StoreScope.PROFILE_LIBRARY,
)

// Upstream writes these lists with Gson; GsonCompatJson reads and writes the same JSON.
private val PlaylistsJson = ListSerializer(Playlist.serializer())
private val TracksJson = ListSerializer(MusicTrack.serializer())

/**
 * Repository for managing playlists and favorites
 */
class PlaylistRepository {
    companion object {
        private val PLAYLISTS_KEY = stringPreferencesKey("playlists")
        private val FAVORITES_KEY = stringPreferencesKey("favorites")
        private val HISTORY_KEY = stringPreferencesKey("history")
    }

    // Get all playlists
    val playlists: Flow<List<Playlist>> =
        playlistDataStore.data.map { prefs ->
            val json = prefs[PLAYLISTS_KEY] ?: "[]"
            GsonCompatJson.decodeFromString(PlaylistsJson, json).map { playlist ->
                playlist.copy(tracks = playlist.tracks.map { it.withTypedArtists() })
            }
        }

    // Get favorites
    val favorites: Flow<List<MusicTrack>> =
        playlistDataStore.data.map { prefs ->
            val json = prefs[FAVORITES_KEY] ?: "[]"
            GsonCompatJson.decodeFromString(TracksJson, json).map { it.withTypedArtists() }
        }

    // Get history
    val history: Flow<List<MusicTrack>> =
        playlistDataStore.data.map { prefs ->
            val json = prefs[HISTORY_KEY] ?: "[]"
            GsonCompatJson.decodeFromString(TracksJson, json).map { it.withTypedArtists() }
        }

    // Create playlist
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createPlaylist(
        name: String,
        description: String = "",
    ): Playlist {
        val playlist =
            Playlist(
                id = Uuid.random().toString(),
                name = name,
                description = description,
                tracks = emptyList(),
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )

        val currentPlaylists = playlists.first().toMutableList()
        currentPlaylists.add(playlist)
        savePlaylists(currentPlaylists)

        return playlist
    }

    // Update playlist
    suspend fun updatePlaylist(playlist: Playlist) {
        val currentPlaylists = playlists.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            currentPlaylists[index] = playlist
            savePlaylists(currentPlaylists)
        }
    }

    // Delete playlist
    suspend fun deletePlaylist(playlistId: String) {
        val currentPlaylists = playlists.first().toMutableList()
        currentPlaylists.removeAll { it.id == playlistId }
        savePlaylists(currentPlaylists)
    }

    // Add track to playlist
    suspend fun addTrackToPlaylist(
        playlistId: String,
        track: MusicTrack,
    ) {
        val currentPlaylists = playlists.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = currentPlaylists[index]
            val updatedTracks = playlist.tracks.toMutableList()
            if (!updatedTracks.any { it.videoId == track.videoId }) {
                updatedTracks.add(track)
                currentPlaylists[index] = playlist.copy(tracks = updatedTracks)
                savePlaylists(currentPlaylists)
            }
        }
    }

    // Remove track from playlist
    suspend fun removeTrackFromPlaylist(
        playlistId: String,
        trackVideoId: String,
    ) {
        val currentPlaylists = playlists.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = currentPlaylists[index]
            val updatedTracks = playlist.tracks.filter { it.videoId != trackVideoId }
            currentPlaylists[index] = playlist.copy(tracks = updatedTracks)
            savePlaylists(currentPlaylists)
        }
    }

    // Add to favorites
    suspend fun addToFavorites(track: MusicTrack) {
        val currentFavorites = favorites.first().toMutableList()
        if (!currentFavorites.any { it.videoId == track.videoId }) {
            currentFavorites.add(0, track) // Add to beginning
            saveFavorites(currentFavorites)
        }
    }

    // Remove from favorites
    suspend fun removeFromFavorites(trackVideoId: String) {
        val currentFavorites = favorites.first().toMutableList()
        currentFavorites.removeAll { it.videoId == trackVideoId }
        saveFavorites(currentFavorites)
    }

    // Check if track is favorite
    suspend fun isFavorite(trackVideoId: String): Boolean = favorites.first().any { it.videoId == trackVideoId }

    // Toggle favorite
    suspend fun toggleFavorite(track: MusicTrack): Boolean =
        if (isFavorite(track.videoId)) {
            removeFromFavorites(track.videoId)
            false
        } else {
            addToFavorites(track)
            true
        }

    // Add to history
    suspend fun addToHistory(track: MusicTrack) {
        Log.d("PlaylistRepository", "Adding to history: ${track.title}")
        val currentHistory = history.first().toMutableList()
        // Remove if exists (to move to top)
        currentHistory.removeAll { it.videoId == track.videoId }
        currentHistory.add(0, track)
        // Limit history size (e.g. 100)
        if (currentHistory.size > 100) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }
        saveHistory(currentHistory)
        Log.d("PlaylistRepository", "History saved. Size: ${currentHistory.size}")
    }

    suspend fun clearHistory() {
        saveHistory(emptyList())
    }

    private suspend fun savePlaylists(playlists: List<Playlist>) {
        Log.d("PlaylistRepository", "Saving playlists: ${playlists.size}")
        playlistDataStore.edit { prefs ->
            prefs[PLAYLISTS_KEY] = GsonCompatJson.encodeToString(PlaylistsJson, playlists)
        }
    }

    private suspend fun saveFavorites(favorites: List<MusicTrack>) {
        Log.d("PlaylistRepository", "Saving favorites: ${favorites.size}")
        playlistDataStore.edit { prefs ->
            prefs[FAVORITES_KEY] = GsonCompatJson.encodeToString(TracksJson, favorites)
        }
    }

    private suspend fun saveHistory(history: List<MusicTrack>) {
        playlistDataStore.edit { prefs ->
            prefs[HISTORY_KEY] = GsonCompatJson.encodeToString(TracksJson, history)
        }
    }
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val tracks: List<MusicTrack> = emptyList(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val thumbnailUrl: String = tracks.firstOrNull()?.thumbnailUrl ?: "",
    val customTrackCount: Int? = null,
) {
    val trackCount: Int get() = customTrackCount ?: tracks.size
    val duration: Int get() = tracks.sumOf { it.duration }
}
