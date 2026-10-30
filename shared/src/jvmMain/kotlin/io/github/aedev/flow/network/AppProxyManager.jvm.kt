package io.github.aedev.flow.network

import io.ktor.client.engine.ProxyConfig
import java.net.InetSocketAddress
import java.net.Proxy

internal actual fun unresolvedProxy(
    type: AppProxyType,
    host: String,
    port: Int,
): ProxyConfig =
    Proxy(
        when (type) {
            AppProxyType.HTTP -> Proxy.Type.HTTP
            AppProxyType.SOCKS5 -> Proxy.Type.SOCKS
        },
        InetSocketAddress.createUnresolved(host, port),
    )
