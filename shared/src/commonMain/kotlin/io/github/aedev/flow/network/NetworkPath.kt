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
    /**
     * Tells one network from another, as Android's Network.networkHandle does for VisitorIdentityStore:
     * the interfaces and gateways on iOS, which has no handle. Null when the platform cannot say.
     */
    val identity: Long? = null,
)

internal expect fun currentNetwork(): NetworkSnapshot

/** The network now, then every change, for as long as it is collected. */
internal expect fun networkChanges(): Flow<NetworkSnapshot>
