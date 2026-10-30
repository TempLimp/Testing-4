package io.github.aedev.flow.network

import io.ktor.http.Url
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Upstream rebuilt its OkHttp client per proxy signature; the Ktor client follows the same rule. */
class ProxyAwareClientTest {
    @AfterTest
    fun reset() = AppProxyManager.update(AppProxyConfig())

    @Test
    fun oneClientServesEveryCallUntilTheProxyChanges() {
        val holder = ProxyAwareClient()
        val direct = holder.get()
        assertSame(direct, holder.get())

        AppProxyManager.update(AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128))
        val proxied = holder.get()
        assertNotSame(direct, proxied)
        assertSame(proxied, holder.get())

        // The VPN bypass alone is not part of the signature, so it keeps the client.
        AppProxyManager.update(AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128, bypassOnVpn = true))
        assertSame(proxied, holder.get())
    }

    @Test
    fun aDeviceOnTheLocalNetworkGetsTheDirectClient() {
        val holder = ProxyAwareClient()
        AppProxyManager.update(AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128))

        val proxied = holder.get(Url("https://www.youtube.com/"))
        val local = holder.get(Url("http://192.168.1.20:8765/sync"))

        assertNotSame(proxied, local)
        assertSame(local, holder.get(Url("http://tv.local/")))
    }
}
