package io.github.aedev.flow.network

import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test
import java.net.InetSocketAddress
import java.net.Proxy

/** Upstream's AppProxyConfigTest checks on the endpoint's socket address, which only the jvm's proxy has (#888). */
class AppProxyConfigJvmTest {
    private fun config(
        type: AppProxyType = AppProxyType.HTTP,
        host: String = "proxy.example.test",
        port: Int = 8080,
    ) = AppProxyConfig(enabled = true, type = type, host = host, port = port)

    @Test
    fun httpProxyEndpointIsLeftUnresolvedSoNoLookupHappensOnTheCallingThread() {
        val proxy = config().toProxy()

        assertThat(proxy).isNotNull()
        assertThat(proxy!!.type()).isEqualTo(Proxy.Type.HTTP)
        val address = proxy.address() as InetSocketAddress
        assertThat(address.isUnresolved).isTrue()
        assertThat(address.hostString).isEqualTo("proxy.example.test")
        assertThat(address.port).isEqualTo(8080)
    }

    @Test
    fun socks5ProxyEndpointIsLeftUnresolvedToo() {
        val proxy = config(type = AppProxyType.SOCKS5, port = 1080).toProxy()

        assertThat(proxy).isNotNull()
        assertThat(proxy!!.type()).isEqualTo(Proxy.Type.SOCKS)
        val address = proxy.address() as InetSocketAddress
        assertThat(address.isUnresolved).isTrue()
        assertThat(address.port).isEqualTo(1080)
    }
}
