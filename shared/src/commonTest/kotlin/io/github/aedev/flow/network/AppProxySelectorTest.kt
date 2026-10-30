package io.github.aedev.flow.network

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertWithMessage
import io.ktor.client.engine.ProxyType
import io.ktor.client.engine.type
import io.ktor.http.Url
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Long-lived clients are built before the saved proxy loads, so they ask the selector per
 * connection instead of capturing a proxy once.
 */
class AppProxySelectorTest {
    @AfterTest
    fun reset() = AppProxyManager.update(AppProxyConfig())

    private fun enable() = AppProxyManager.update(AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128))

    // Upstream's selector answers [Proxy.NO_PROXY] for a direct route; Ktor's answers null.
    private fun select(url: String) = AppProxySelector.select(Url(url))

    @Test
    fun remoteHostsGoDirectWhileNoProxyIsSet() {
        assertThat(select("https://i.ytimg.com/vi/x/hq.jpg")).isNull()
    }

    @Test
    fun remoteHostsFollowAProxySetAfterTheClientWasBuilt() {
        enable()

        val proxy = select("https://i.ytimg.com/vi/x/hq.jpg")

        assertThat(proxy?.type).isEqualTo(ProxyType.HTTP)
        // java.net.Proxy on the jvm, a proxy URL on iOS: both print the endpoint.
        assertThat(proxy.toString()).contains("proxy.example.test")
    }

    @Test
    fun turningTheProxyOffSendsRemoteHostsDirectAgain() {
        enable()
        AppProxyManager.update(AppProxyConfig())

        assertThat(select("https://www.youtube.com/")).isNull()
    }

    @Test
    fun devicesOnTheLocalNetworkStayDirect() {
        enable()

        assertThat(select("ws://192.168.1.20:8765/sync")).isNull()
        assertThat(select("http://[fe80::1]:8080/")).isNull()
        assertThat(select("http://tv.local/")).isNull()
    }

    @Test
    fun localHostsAreRecognisedFromLiteralsAndLocalNamesOnly() {
        val local =
            listOf(
                "localhost",
                "127.0.0.1",
                "10.0.2.2",
                "172.16.0.9",
                "172.31.255.1",
                "192.168.0.4",
                "169.254.1.1",
                "::1",
                "fd12::4",
                "printer.local",
            )
        val remote = listOf("www.youtube.com", "172.32.0.1", "8.8.8.8", "192.169.0.1", "fdroid.org", "300.1.1.1", "2001:db8::1")

        local.forEach { assertWithMessage(it).that(isLocalHost(it)).isTrue() }
        remote.forEach { assertWithMessage(it).that(isLocalHost(it)).isFalse() }
    }

    @Test
    fun aVpnSendsTrafficDirectAndRekeysClientsWhenTheBypassIsOn() {
        val config = AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128, bypassOnVpn = true)
        AppProxyManager.update(config, vpnActive = false)
        val withProxy = AppProxyManager.currentSignature()

        AppProxyManager.update(config, vpnActive = true)

        assertThat(select("https://www.youtube.com/")).isNull()
        assertThat(AppProxyManager.currentSignature()).isNotEqualTo(withProxy)

        AppProxyManager.update(config, vpnActive = false)

        assertThat(select("https://www.youtube.com/")?.type).isEqualTo(ProxyType.HTTP)
        assertThat(AppProxyManager.currentSignature()).isEqualTo(withProxy)
    }

    @Test
    fun aVpnLeavesTheProxyOnWhenTheBypassIsOff() {
        AppProxyManager.update(AppProxyConfig(enabled = true, host = "proxy.example.test", port = 3128), vpnActive = true)

        assertThat(select("https://www.youtube.com/")?.type).isEqualTo(ProxyType.HTTP)
    }
}
