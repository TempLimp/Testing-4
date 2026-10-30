package io.github.aedev.flow.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.compression.ContentEncoding

// The jvm target only runs tests, so it carries no disk cache and no proxy credentials: CIO has no
// proxy authenticator, and no test signs in to a proxy.
actual fun platformHttpClient(
    settings: HttpEngineSettings,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(CIO) {
        engine {
            proxy = settings.proxy?.toProxy()
            settings.maxConnectionsPerHost?.let { endpoint.maxConnectionsPerRoute = it }
        }
        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }
        block()
    }
