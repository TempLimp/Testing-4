package io.github.aedev.flow.network

import io.github.aedev.flow.testing.assertThat
import io.ktor.client.engine.ProxyType
import io.ktor.client.engine.type
import kotlin.test.Test

/**
 * A resolved proxy address costs a DNS lookup wherever the `Proxy` is built, and clients are built
 * from lazy fields that Hilt can touch on the main thread — that combination crashed the Library and
 * Music tabs with `NetworkOnMainThreadException` (issue #888). These tests pin the endpoint down as
 * unresolved so the lookup stays on the connection thread; that half runs on the jvm
 * (AppProxyConfigJvmTest), the only target whose proxy holds a socket address.
 */
class AppProxyConfigTest {
    private fun config(
        type: AppProxyType = AppProxyType.HTTP,
        host: String = "proxy.example.test",
        port: Int = 8080,
    ) = AppProxyConfig(enabled = true, type = type, host = host, port = port)

    @Test
    fun eachTypeBecomesTheMatchingProxy() {
        assertThat(config().toProxy()?.type).isEqualTo(ProxyType.HTTP)
        assertThat(config(type = AppProxyType.SOCKS5, port = 1080).toProxy()?.type).isEqualTo(ProxyType.SOCKS)
    }

    @Test
    fun anUnusableEndpointYieldsNoProxy() {
        assertThat(config().copy(enabled = false).toProxy()).isNull()
        assertThat(config(host = "   ").toProxy()).isNull()
        assertThat(config(port = 0).toProxy()).isNull()
        assertThat(config(port = 70_000).toProxy()).isNull()
    }

    @Test
    fun aVpnPausesTheProxyOnlyWhenTheBypassIsOn() {
        val bypass = config().copy(bypassOnVpn = true)

        assertThat(bypass.effective(vpnActive = true).toProxy()).isNull()
        assertThat(bypass.effective(vpnActive = false).toProxy()).isNotNull()
        assertThat(config().effective(vpnActive = true).toProxy()).isNotNull()
        assertThat(config().effective(vpnActive = false).toProxy()).isNotNull()
    }

    @Test
    fun theNetworkIsWatchedForAVpnOnlyWhileItCouldChangeTheProxy() {
        assertThat(config().copy(bypassOnVpn = true).watchesVpn()).isTrue()
        assertThat(config().watchesVpn()).isFalse()
        assertThat(config().copy(enabled = false, bypassOnVpn = true).watchesVpn()).isFalse()
        assertThat(config(host = " ").copy(bypassOnVpn = true).watchesVpn()).isFalse()
    }

    @Test
    fun theBypassSwitchAloneDoesNotChangeTheSignatureClientsAreKeyedBy() {
        assertThat(config().copy(bypassOnVpn = true).signature()).isEqualTo(config().signature())
    }
}
