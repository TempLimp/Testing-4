package io.github.aedev.flow.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.http.Url
import kotlin.concurrent.atomics.AtomicReference

/**
 * One [HttpClient] per proxy configuration, rebuilt only when that configuration changes: calls
 * share one connection pool, while a proxy switch still takes effect without a restart.
 */
class ProxyAwareClient(
    private val settings: HttpEngineSettings = HttpEngineSettings(),
    private val configure: HttpClientConfig<*>.() -> Unit = {},
) {
    private val cached = AtomicReference<Pair<String, HttpClient>?>(null)
    private val direct by lazy { platformHttpClient(settings, configure) }

    /** The client for [url]: one through the current proxy, or a direct one for a device on the local network. */
    fun get(url: Url? = null): HttpClient {
        if (url != null && AppProxyManager.currentProxy() != null && AppProxySelector.select(url) == null) return direct
        val signature = AppProxyManager.currentSignature()
        while (true) {
            val current = cached.load()
            if (current != null && current.first == signature) return current.second
            val fresh = signature to platformHttpClient(AppProxyManager.applyTo(settings), configure)
            if (cached.compareAndSet(current, fresh)) {
                // Like the evicted OkHttp pool: requests already running finish, new ones take the new route.
                current?.second?.close()
                return fresh.second
            }
            fresh.second.close()
        }
    }
}
