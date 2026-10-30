package io.github.aedev.flow.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * What upstream set on its OkHttp builders, for the platform's engine: NSURLSession on iOS, CIO on
 * the jvm test target.
 *
 * NSURLSession reads gzip, deflate and br bodies itself, and OkHttp did the same for gzip, so only the
 * jvm engine installs Ktor's content decoding.
 */
data class HttpEngineSettings(
    /** The proxy in force; null or an unusable endpoint means a direct connection. */
    val proxy: AppProxyConfig? = null,
    val maxConnectionsPerHost: Int? = null,
    /** An on-disk HTTP cache, as OkHttp's `Cache(directory, maxSize)`; null keeps the engine's default. */
    val cacheDirectory: String? = null,
    val cacheSizeBytes: Long = 0L,
)

expect fun platformHttpClient(
    settings: HttpEngineSettings = HttpEngineSettings(),
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient
