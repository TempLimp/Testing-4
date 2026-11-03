package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.withContext

// Upstream reaches the repository through a Hilt @EntryPoint from the root composable; iOS has no
// Hilt, so the caller (S13's root) passes the Koin-provided instance itself.

/**
 * Tops up whatever has gone stale since the app was last open.
 *
 * Shares the repository's plan and lock with the Subscriptions screen, so a startup refresh and the
 * screen opening moments later do not fetch the same channels twice.
 */
suspend fun refreshSubscriptionsAtStartup(repository: SubscriptionFeedRepository) {
    withContext(PerformanceDispatcher.networkIO) {
        runCatching {
            val plan = repository.planRefresh(force = false)
            if (plan.isEmpty) return@runCatching
            repository.refresh(plan).collect { }
        }
    }
}
