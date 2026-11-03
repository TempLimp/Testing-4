package io.github.aedev.flow.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.withTypedArtists
import io.github.aedev.flow.data.profile.ProfileFiles
import io.github.aedev.flow.data.profile.StoreScope
import io.github.aedev.flow.utils.Log
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.SystemFileSystem
import kotlin.concurrent.Volatile
import kotlin.time.Clock

private const val QUEUE_STORE_NAME = "music_queue"

private val queueDataStore: DataStore<Preferences> by safePreferencesDataStore(
    name = QUEUE_STORE_NAME,
    scope = StoreScope.PROFILE_PRIVATE,
)

class QueuePersistence private constructor() {
    companion object {
        private const val TAG = "QueuePersistence"
        private const val SAVE_DEBOUNCE_MS = 5_000L // Debounce saves to every 5 seconds
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L // Auto-save every 30 seconds

        private val QUEUE_KEY = stringPreferencesKey("queue_json")
        private val CURRENT_INDEX_KEY = intPreferencesKey("current_index")
        private val CURRENT_POSITION_KEY = longPreferencesKey("current_position")
        private val CURRENT_TRACK_ID_KEY = stringPreferencesKey("current_track_id")
        private val SHUFFLE_ENABLED_KEY = stringPreferencesKey("shuffle_enabled")
        private val REPEAT_MODE_KEY = intPreferencesKey("repeat_mode")
        private val SAVED_AT_KEY = longPreferencesKey("saved_at")
        private val AUTOMIX_KEY = stringPreferencesKey("automix_json")

        @Volatile
        private var instance: QueuePersistence? = null
        private val lock = SynchronizedObject()

        fun getInstance(): QueuePersistence =
            instance ?: synchronized(lock) {
                instance ?: QueuePersistence().also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val saveMutex = Mutex()

    private var saveJob: Job? = null
    private var autoSaveJob: Job? = null
    private var lastSaveTime = 0L

    /**
     * Data class holding all queue state for persistence
     */
    data class QueueState(
        val queue: List<MusicTrack>,
        val currentIndex: Int,
        val currentPosition: Long,
        val currentTrackId: String?,
        val shuffleEnabled: Boolean,
        val repeatMode: Int, // 0=OFF, 1=ALL, 2=ONE
        val savedAt: Long,
        val automix: List<MusicTrack> = emptyList(),
    )

    /**
     * Save queue state with debouncing to prevent excessive disk writes
     */
    fun saveQueueDebounced(
        queue: List<MusicTrack>,
        currentIndex: Int,
        currentPosition: Long,
        currentTrackId: String?,
        shuffleEnabled: Boolean = false,
        repeatMode: Int = 0,
        automix: List<MusicTrack> = emptyList(),
    ) {
        saveJob?.cancel()
        saveJob =
            scope.launch {
                delay(SAVE_DEBOUNCE_MS)
                saveQueueImmediate(queue, currentIndex, currentPosition, currentTrackId, shuffleEnabled, repeatMode, automix)
            }
    }

    /** Drops a debounced save that an immediate save is about to supersede; it would write older state. */
    fun cancelPendingSave() {
        saveJob?.cancel()
    }

    /**
     * Immediately save queue state (use for critical moments like app pause)
     */
    suspend fun saveQueueImmediate(
        queue: List<MusicTrack>,
        currentIndex: Int,
        currentPosition: Long,
        currentTrackId: String?,
        shuffleEnabled: Boolean = false,
        repeatMode: Int = 0,
        automix: List<MusicTrack> = emptyList(),
    ) {
        if (queue.isEmpty()) return

        saveMutex.withLock {
            try {
                val now = Clock.System.now().toEpochMilliseconds()
                queueDataStore.edit { prefs ->
                    prefs[QUEUE_KEY] = GsonCompatJson.encodeToString(queue)
                    prefs[CURRENT_INDEX_KEY] = currentIndex
                    prefs[CURRENT_POSITION_KEY] = currentPosition
                    prefs[CURRENT_TRACK_ID_KEY] = currentTrackId ?: ""
                    prefs[SHUFFLE_ENABLED_KEY] = shuffleEnabled.toString()
                    prefs[REPEAT_MODE_KEY] = repeatMode
                    prefs[SAVED_AT_KEY] = now
                    prefs[AUTOMIX_KEY] = GsonCompatJson.encodeToString(automix)
                }
                lastSaveTime = now
                Log.d(TAG, "Queue saved: ${queue.size} tracks, index=$currentIndex, pos=$currentPosition, automix=${automix.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save queue", e)
            }
        }
    }

    /**
     * Restore queue state from persistent storage
     */
    suspend fun restoreQueue(): QueueState? {
        return try {
            val prefs = queueDataStore.data.first()

            val queueJson = prefs[QUEUE_KEY] ?: return null
            val queue: List<MusicTrack> =
                GsonCompatJson.decodeFromString<List<MusicTrack>>(queueJson).map { it.withTypedArtists() }

            if (queue.isEmpty()) return null

            val automixJson = prefs[AUTOMIX_KEY]
            val automix: List<MusicTrack> =
                if (!automixJson.isNullOrBlank()) {
                    GsonCompatJson.decodeFromString<List<MusicTrack>>(automixJson).map { it.withTypedArtists() }
                } else {
                    emptyList()
                }

            val state =
                QueueState(
                    queue = queue,
                    currentIndex = prefs[CURRENT_INDEX_KEY] ?: 0,
                    currentPosition = prefs[CURRENT_POSITION_KEY] ?: 0L,
                    currentTrackId = prefs[CURRENT_TRACK_ID_KEY]?.takeIf { it.isNotBlank() },
                    shuffleEnabled = prefs[SHUFFLE_ENABLED_KEY]?.toBooleanStrictOrNull() ?: false,
                    repeatMode = prefs[REPEAT_MODE_KEY] ?: 0,
                    savedAt = prefs[SAVED_AT_KEY] ?: 0L,
                    automix = automix,
                )

            Log.d(TAG, "Queue restored: ${queue.size} tracks, saved ${(Clock.System.now().toEpochMilliseconds() - state.savedAt) / 1000}s ago")
            state
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore queue", e)
            null
        }
    }

    /**
     * Start automatic periodic saving
     */
    fun startAutoSave(getQueueState: () -> QueueState?) {
        autoSaveJob?.cancel()
        autoSaveJob =
            scope.launch {
                while (true) {
                    delay(AUTO_SAVE_INTERVAL_MS)
                    getQueueState()?.let { state ->
                        if (state.queue.isNotEmpty()) {
                            saveQueueImmediate(
                                queue = state.queue,
                                currentIndex = state.currentIndex,
                                currentPosition = state.currentPosition,
                                currentTrackId = state.currentTrackId,
                                shuffleEnabled = state.shuffleEnabled,
                                repeatMode = state.repeatMode,
                                automix = state.automix,
                            )
                        }
                    }
                }
            }
    }

    /**
     * Stop automatic saving (call when service/player is stopped)
     */
    fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    /**
     * Whether a queue was ever saved, from the store's file alone. Only non-empty queues are written
     * and nothing clears the store, so the file exists exactly when there is a queue to resume. A media
     * button that cold-starts the music service needs this answer on the main thread, before any read
     * of the store could finish.
     */
    fun hasSavedQueueFile(): Boolean = SystemFileSystem.exists(ProfileFiles.preferences(StoreScope.PROFILE_PRIVATE, QUEUE_STORE_NAME))
}
