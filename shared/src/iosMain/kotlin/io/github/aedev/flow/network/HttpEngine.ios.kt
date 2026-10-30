package io.github.aedev.flow.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURL
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialWithUser

actual fun platformHttpClient(
    settings: HttpEngineSettings,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(Darwin) {
        val proxy = settings.proxy?.takeIf { it.hasUsableEndpoint() }
        engine {
            configureSession {
                settings.maxConnectionsPerHost?.let { HTTPMaximumConnectionsPerHost = it.toLong() }
                settings.cacheDirectory?.let { directory ->
                    URLCache =
                        NSURLCache(
                            memoryCapacity = 0u,
                            diskCapacity = settings.cacheSizeBytes.toULong(),
                            directoryURL = NSURL.fileURLWithPath(directory),
                        )
                }
                // Written here rather than through Ktor's `proxy`, which sets the HTTP keys only, so
                // https:// requests would bypass an HTTP proxy.
                proxy?.let { connectionProxyDictionary = it.connectionProxyDictionary() }
            }
            if (proxy != null && proxy.hasCredentials()) {
                handleChallenge { _, _, challenge, completionHandler ->
                    // A second challenge means the proxy refused these credentials; answering again would only loop.
                    if (challenge.protectionSpace.isProxy() && challenge.previousFailureCount == 0L) {
                        completionHandler(
                            NSURLSessionAuthChallengeUseCredential,
                            NSURLCredential.credentialWithUser(
                                proxy.username,
                                proxy.password,
                                NSURLCredentialPersistence.NSURLCredentialPersistenceForSession,
                            ),
                        )
                    } else {
                        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                    }
                }
            }
        }
        block()
    }

/** CFNetwork's proxy keys (kCFNetworkProxies*, kCFStreamPropertySOCKS*), by value. */
private fun AppProxyConfig.connectionProxyDictionary(): Map<Any?, Any?> =
    when (type) {
        AppProxyType.HTTP ->
            mapOf(
                "HTTPEnable" to 1,
                "HTTPProxy" to host,
                "HTTPPort" to port,
                "HTTPSEnable" to 1,
                "HTTPSProxy" to host,
                "HTTPSPort" to port,
            )

        AppProxyType.SOCKS5 ->
            buildMap {
                put("SOCKSEnable", 1)
                put("SOCKSProxy", host)
                put("SOCKSPort", port)
                if (hasCredentials()) {
                    put("kCFStreamPropertySOCKSUser", username)
                    put("kCFStreamPropertySOCKSPassword", password)
                }
            }
    }
