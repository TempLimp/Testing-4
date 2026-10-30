package io.github.aedev.flow.utils

import io.github.aedev.flow.network.currentNetwork
import io.github.aedev.flow.network.networkChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Observes network connectivity changes and provides reactive state.
 * Used by Media3MusicService to pause/resume retries based on network availability.
 *
 * Upstream registers a ConnectivityManager callback through a Context; this collects the platform's
 * path updates instead and needs none.
 */
class NetworkConnectivityObserver {
    companion object {
        private const val TAG = "NetworkConnectivity"
    }

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var observation: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Check current network connectivity status.
     */
    fun checkCurrentConnectivity(): Boolean = NetworkState.isOnline()

    /**
     * Check if the network is metered (e.g., mobile data).
     */
    fun isNetworkMetered(): Boolean = runCatching { currentNetwork().metered }.getOrDefault(false)

    /**
     * Start observing network changes.
     */
    fun startObserving() {
        if (observation != null) return
        observation =
            networkChanges()
                .onEach { network ->
                    Log.d(TAG, "Connectivity: ${network.online}")
                    _isConnected.value = network.online
                }.catch { Log.e(TAG, "Failed to observe the network", it) }
                .launchIn(scope)
        Log.d(TAG, "Started observing network")
    }

    /**
     * Stop observing network changes.
     */
    fun stopObserving() {
        observation?.cancel()
        observation = null
        Log.d(TAG, "Stopped observing network")
    }

    /**
     * Get network connectivity as a Flow for reactive observation.
     */
    fun observeConnectivity(): Flow<Boolean> = networkChanges().map { it.online }.distinctUntilChanged()
}
