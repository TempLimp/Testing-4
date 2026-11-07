package io.github.aedev.flow.data.recommendation


/**
 * The topics the recommendation engine follows and hides, behind an injectable seam. It adapts the
 * engine's static entry points so callers take this as a dependency instead of reaching the engine
 * globally; the engine keeps its single instance underneath.
 */
class TopicPreferencesRepository
    constructor() {
        suspend fun preferredTopics(): Set<String> = FlowNeuroEngine.getPreferredTopics()

        suspend fun blockedTopics(): Set<String> = FlowNeuroEngine.getBlockedTopics()

        suspend fun addPreferred(topic: String) = FlowNeuroEngine.addPreferredTopic(topic)

        suspend fun removePreferred(topic: String) = FlowNeuroEngine.removePreferredTopic(topic)

        suspend fun addBlocked(topic: String) = FlowNeuroEngine.addBlockedTopic(topic)

        suspend fun removeBlocked(topic: String) = FlowNeuroEngine.removeBlockedTopic(topic)
    }
