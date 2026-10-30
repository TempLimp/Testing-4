package io.github.aedev.flow.utils

import io.github.aedev.flow.network.currentNetwork

/**
 * Point-in-time answers to "what network am I on?".
 *
 * The same two questions had four hand-rolled answers across the player, the Shorts pager and the
 * player effects, and they disagreed on what to return when connectivity could not be read.
 * [NetworkConnectivityObserver] answers the reactive version of the same questions.
 *
 * Upstream's calls take a Context to reach ConnectivityManager; these need none.
 */
object NetworkState {
    /**
     * Whether the active transport is wifi.
     *
     * Returns `false` when connectivity cannot be read. The player copies of this check used to
     * assume wifi in that case, which only differs when there is no active network at all — i.e.
     * when nothing is going to stream anyway — so the data-conservative answer wins.
     */
    fun isOnWifi(): Boolean = runCatching { currentNetwork().wifi }.getOrDefault(false)

    /** Whether the active network can actually reach the internet. */
    fun isOnline(): Boolean = runCatching { currentNetwork().online }.getOrDefault(false)
}
