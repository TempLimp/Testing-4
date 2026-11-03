package io.github.aedev.flow.data.local

import kotlinx.coroutines.flow.first

/**
 * What Deep Flow and paused watch history allow to be recorded. Writers ask here instead of reading
 * the preferences themselves, so every one of them follows the same rules.
 */
class PrivacyGate internal constructor(
    private val deepFlowActive: suspend () -> Boolean,
    private val watchHistoryPaused: suspend () -> Boolean,
    private val scrobbleDuringDeepFlow: suspend () -> Boolean,
) {
    constructor(preferences: PlayerPreferences) : this(
        deepFlowActive = preferences::isDeepFlowCurrentlyActive,
        watchHistoryPaused = { preferences.watchHistoryPaused.first() },
        scrobbleDuringDeepFlow = { preferences.deepFlowScrobble.first() },
    )

    /** Deep Flow is on and its timer has not run out. */
    suspend fun isDeepFlowActive(): Boolean = deepFlowActive()

    suspend fun isWatchHistoryPaused(): Boolean = watchHistoryPaused()

    /** Listens go to scrobbling services unless Deep Flow is on and the user kept them private. */
    suspend fun allowsScrobbling(): Boolean = !deepFlowActive() || scrobbleDuringDeepFlow()
}
