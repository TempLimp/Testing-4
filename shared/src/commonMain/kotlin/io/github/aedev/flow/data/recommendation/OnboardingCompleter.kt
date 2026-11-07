package io.github.aedev.flow.data.recommendation


/** Seeds the engine with the topics picked during onboarding and marks onboarding done. */
class OnboardingCompleter
    constructor() {
        suspend fun complete(topics: Set<String>) = FlowNeuroEngine.completeOnboarding(topics)
    }
