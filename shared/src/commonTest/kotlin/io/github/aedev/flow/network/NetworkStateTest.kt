package io.github.aedev.flow.network

import io.github.aedev.flow.utils.NetworkConnectivityObserver
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** The point-in-time answers and the observed ones read the same network (NWPathMonitor on iOS). */
class NetworkStateTest {
    @Test
    fun theObservedNetworkMatchesThePointInTimeOne() =
        runBlocking {
            val observed = withTimeout(10.seconds) { networkChanges().first() }

            assertEquals(currentNetwork(), observed)
            assertEquals(NetworkState.isOnline(), observed.online)
            assertEquals(NetworkState.isOnWifi(), observed.wifi)
            assertEquals(NetworkConnectivityObserver().checkCurrentConnectivity(), observed.online)
        }

    @Test
    fun theVpnStateFlowStartsWithTheCurrentState() =
        runBlocking {
            assertEquals(appTrafficUsesVpn(), withTimeout(10.seconds) { VpnStateMonitor().vpnActive().first() })
        }
}
