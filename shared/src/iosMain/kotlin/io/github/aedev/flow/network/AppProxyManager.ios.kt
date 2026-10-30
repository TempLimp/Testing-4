package io.github.aedev.flow.network

import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol

// On Darwin a ProxyConfig is only a URL; nothing is resolved until NSURLSession connects.
internal actual fun unresolvedProxy(
    type: AppProxyType,
    host: String,
    port: Int,
): ProxyConfig =
    when (type) {
        AppProxyType.HTTP -> ProxyBuilder.http(URLBuilder(protocol = URLProtocol.HTTP, host = host, port = port).build())
        AppProxyType.SOCKS5 -> ProxyBuilder.socks(host, port)
    }
