package io.github.aedev.flow.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether this app's traffic runs through a VPN. It reads the app's own default network, so a VPN
 * that excludes Flow by split tunnelling counts as off. One instance for the app (Hilt's @Singleton
 * upstream; register it with Koin's `single {}`).
 */
class VpnStateMonitor {
    /** Emits the current state, then every change. A path monitor is held only while collected. */
    fun vpnActive(): Flow<Boolean> = networkChanges().map { it.vpn }.distinctUntilChanged()
}

/** Whether this app's default network is a VPN right now, for a one-off read outside a flow. */
fun appTrafficUsesVpn(): Boolean = runCatching { currentNetwork().vpn }.getOrDefault(false)
