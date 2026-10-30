package io.github.aedev.flow.network

import io.ktor.client.engine.ProxyConfig
import kotlin.concurrent.atomics.AtomicReference
import kotlin.io.encoding.Base64

enum class AppProxyType(
    val storageValue: String,
) {
    HTTP("http"),
    SOCKS5("socks5"),
    ;

    companion object {
        fun fromStorageValue(value: String?): AppProxyType =
            when (value?.lowercase()) {
                SOCKS5.storageValue -> SOCKS5
                else -> HTTP
            }
    }
}

data class AppProxyConfig(
    val enabled: Boolean = false,
    val type: AppProxyType = AppProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
    val bypassOnVpn: Boolean = false,
) {
    fun normalized(): AppProxyConfig =
        copy(
            host = host.trim(),
            username = username.trim(),
        )

    /** The proxy traffic should follow right now: a VPN pauses it when [bypassOnVpn] is set. */
    fun effective(vpnActive: Boolean): AppProxyConfig = if (bypassOnVpn && vpnActive) copy(enabled = false) else this

    /** Whether a VPN can change this proxy, so only then is the network watched for one. */
    fun watchesVpn(): Boolean = bypassOnVpn && hasUsableEndpoint()

    fun hasUsableEndpoint(): Boolean = enabled && host.isNotBlank() && port in 1..65535

    /** The endpoint as Ktor's proxy, never resolved here: building a client must not cost a DNS lookup (#888). */
    fun toProxy(): ProxyConfig? = if (hasUsableEndpoint()) unresolvedProxy(type, host, port) else null

    fun hasCredentials(): Boolean = username.isNotBlank()

    fun httpProxyAuthorizationHeader(): String? {
        if (!hasUsableEndpoint() || type != AppProxyType.HTTP || !hasCredentials()) return null
        val credentials = "$username:$password"
        val encoded = Base64.Default.encode(credentials.encodeToByteArray())
        return "Basic $encoded"
    }

    fun signature(): String =
        listOf(
            enabled,
            type.storageValue,
            host,
            port,
            username,
            password,
        ).joinToString(separator = "|")
}

internal expect fun unresolvedProxy(
    type: AppProxyType,
    host: String,
    port: Int,
): ProxyConfig

/**
 * The proxy every client follows. OkHttp asked a ProxySelector per connection and evicted pooled
 * connections on a change; a Ktor client's proxy is fixed when it is built, so long-lived clients are
 * [ProxyAwareClient]s, rebuilt whenever [currentSignature] changes. The SOCKS5 credentials upstream gave
 * java.net.Authenticator travel inside the engine settings instead (see [platformHttpClient]).
 */
object AppProxyManager {
    private val config = AtomicReference(AppProxyConfig())

    fun update(
        newConfig: AppProxyConfig,
        vpnActive: Boolean = false,
    ) {
        config.store(newConfig.normalized().effective(vpnActive))
    }

    fun currentConfig(): AppProxyConfig = config.load()

    fun currentProxy(): ProxyConfig? = config.load().toProxy()

    fun currentHttpProxyAuthorizationHeader(): String? = config.load().httpProxyAuthorizationHeader()

    fun currentSignature(): String = config.load().signature()

    /** [settings] routed through the proxy in force now, as upstream's `applyTo(OkHttpClient.Builder)`. */
    fun applyTo(settings: HttpEngineSettings): HttpEngineSettings {
        val activeConfig = config.load()
        if (!activeConfig.hasUsableEndpoint()) return settings
        return settings.copy(proxy = activeConfig)
    }
}
