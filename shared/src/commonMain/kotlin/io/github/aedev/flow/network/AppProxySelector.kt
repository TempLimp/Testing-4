package io.github.aedev.flow.network

import io.ktor.client.engine.ProxyConfig
import io.ktor.http.Url

/**
 * Picks the route for a request from the proxy in force at that moment. Devices on the local network
 * stay direct, as casting and device sync need. [ProxyAwareClient.get] applies it per request.
 *
 * Upstream's OkHttp Authenticator that answered an HTTP proxy's sign-in challenge has no Ktor
 * counterpart; the iOS engine answers the challenge from the credentials in the engine settings.
 */
internal object AppProxySelector {
    /** The proxy [url] goes through, or null for a direct connection. */
    fun select(url: Url): ProxyConfig? {
        val proxy = AppProxyManager.currentProxy()
        return if (proxy == null || url.host.isEmpty() || isLocalHost(url.host)) null else proxy
    }
}

/**
 * Whether [host] names this device or one on the local network. Only literal addresses and local
 * names are recognised: resolving a hostname here would cost a DNS lookup per connection.
 */
internal fun isLocalHost(host: String): Boolean {
    val name = host.lowercase().removePrefix("[").removeSuffix("]")
    if (name == "localhost" || name.endsWith(".local")) return true
    if (':' in name) {
        return name == "::1" || name.startsWith("fe80:") || name.startsWith("fc") || name.startsWith("fd")
    }
    val octets = name.split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    val (first, second) = octets
    return first == 10 ||
        first == 127 ||
        (first == 169 && second == 254) ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168)
}
