package io.github.aedev.flow.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.net.NetworkInterface

private val VPN_INTERFACES = Regex("^(tun|utun|tap|ppp|wg|ipsec).*")

// The jvm target only runs tests: interfaces are read once, with no wifi or metering to report.
internal actual fun currentNetwork(): NetworkSnapshot {
    val up = NetworkInterface.networkInterfaces().filter { it.isUp && !it.isLoopback }.toList()
    return NetworkSnapshot(
        online = up.any { it.inetAddresses.hasMoreElements() },
        wifi = false,
        metered = false,
        vpn = up.any { VPN_INTERFACES.matches(it.name) },
    )
}

internal actual fun networkChanges(): Flow<NetworkSnapshot> = flowOf(currentNetwork())
