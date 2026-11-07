package io.github.aedev.flow.data.stats

import io.github.aedev.flow.data.local.PrivacyGate

// The VideoStatsRecorder class of this file reads each view's topics from FlowNeuroEngine, so it comes with S09.

/** Which privacy settings stop an entry from reaching the recap ledger. */
internal enum class RecapEntry {
    /** A watched video, with its title and channel: Deep Flow and paused watch history stop it. */
    VIEW,

    /** Searches, actions, dislikes and skipped segments: Deep Flow stops them. */
    ACTIVITY,

    /** Removing something already stored: always allowed. */
    FORGET,
    ;

    fun allows(
        deepFlowActive: Boolean,
        watchHistoryPaused: Boolean,
    ): Boolean =
        when (this) {
            VIEW -> !deepFlowActive && !watchHistoryPaused
            ACTIVITY -> !deepFlowActive
            FORGET -> true
        }

    suspend fun allowed(gate: PrivacyGate): Boolean =
        this == FORGET || allows(gate.isDeepFlowActive(), this == VIEW && gate.isWatchHistoryPaused())
}
