@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.network

import kotlinx.cinterop.toKString
import platform.Network.nw_endpoint_copy_address_string
import platform.Network.nw_path_enumerate_gateways
import platform.posix.free
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Network.nw_interface_get_name
import platform.Network.nw_interface_get_type
import platform.Network.nw_interface_type_other
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_enumerate_interfaces
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.Network.nw_path_uses_interface_type
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import kotlin.concurrent.atomics.AtomicReference

// A VPN tunnel shows in the app's path as an interface of type "other" with one of these names.
private val VPN_INTERFACES = Regex("^(utun|ipsec|ppp|tun|tap).*")

private fun nw_path_t.snapshot(): NetworkSnapshot {
    var vpn = false
    val signature = StringBuilder()
    nw_path_enumerate_interfaces(this) { networkInterface ->
        val name = nw_interface_get_name(networkInterface)?.toKString().orEmpty()
        signature.append(nw_interface_get_type(networkInterface)).append(':').append(name).append(';')
        if (nw_interface_get_type(networkInterface) == nw_interface_type_other && VPN_INTERFACES.matches(name)) vpn = true
        true
    }
    nw_path_enumerate_gateways(this) { gateway ->
        nw_endpoint_copy_address_string(gateway)?.let { address ->
            signature.append(address.toKString()).append(';')
            free(address)
        }
        true
    }
    return NetworkSnapshot(
        online = nw_path_get_status(this) == nw_path_status_satisfied,
        wifi = nw_path_uses_interface_type(this, nw_interface_type_wifi),
        metered = nw_path_is_expensive(this),
        vpn = vpn,
        identity = signature.toString().takeIf { it.isNotEmpty() }?.hashCode()?.toLong(),
    )
}

internal actual fun networkChanges(): Flow<NetworkSnapshot> =
    callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path -> trySend(path.snapshot()) }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("io.github.aedev.flow.network.changes", null))
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }

/** One monitor for the process, as Apple recommends, so a point-in-time read is a field read. */
private object SharedPathMonitor {
    val latest = AtomicReference<NetworkSnapshot?>(null)
    val firstUpdate = dispatch_semaphore_create(0)

    init {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            val first = latest.exchange(path.snapshot()) == null
            if (first) dispatch_semaphore_signal(firstUpdate)
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("io.github.aedev.flow.network.current", null))
        nw_path_monitor_start(monitor)
    }
}

internal actual fun currentNetwork(): NetworkSnapshot {
    SharedPathMonitor.latest.load()?.let { return it }
    // The first path arrives on the monitor's queue right after it starts; each waiter passes the signal on.
    dispatch_semaphore_wait(SharedPathMonitor.firstUpdate, dispatch_time(DISPATCH_TIME_NOW, FIRST_PATH_TIMEOUT_NANOS))
    dispatch_semaphore_signal(SharedPathMonitor.firstUpdate)
    return SharedPathMonitor.latest.load() ?: NetworkSnapshot(online = false, wifi = false, metered = false, vpn = false)
}

private const val FIRST_PATH_TIMEOUT_NANOS = 2_000_000_000L
