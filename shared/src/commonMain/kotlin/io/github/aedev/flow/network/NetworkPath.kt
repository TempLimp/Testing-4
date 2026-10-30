package io.github.aedev.flow.network

import kotlinx.coroutines.flow.Flow

/**
 * The app's default network as the platform reports it: ConnectivityManager's capabilities upstream,
 * NWPathMonitor's path on iOS.
 */
internal data class NetworkSnapshot(
    /** It can reach the internet. iOS cannot tell an unauthenticated captive portal apart, which Android's VALIDATED did. */
    val online: Boolean,
    val wifi: Boolean,
    /** Billed by the byte: cellular or a personal hotspot. */
    val metered: Boolean,
    /** The app's own traffic runs through a VPN. */
    val vpn: Boolean,
)

internal expect fun currentNetwork(): NetworkSnapshot

/** The network now, then every change, for as long as it is collected. */
internal expect fun networkChanges(): Flow<NetworkSnapshot>
