package io.github.aedev.flow.network

import io.github.aedev.flow.testing.UPSTREAM_TEST_RESOURCES
import io.github.aedev.flow.utils.readText
import io.ktor.client.request.get
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The app proxy against real proxies, on the Darwin engine (PARITY section 7). CI starts tinyproxy (HTTP) and
 * microsocks (SOCKS5), each plain and with credentials, lists them in `build/proxy-check/proxies.txt`, and after the
 * tests finds these requests in each proxy's own log. Each proxy must carry HTTPS requests to outside hosts (CFNetwork
 * would go around a proxy for a loopback target), and the same proxy moved to a closed port must make them fail, so a
 * client that ignored its proxy cannot pass. Without that file, as on any other run, there is nothing to check.
 */
class ProxyRoutingTest {
    @Test
    fun requestsGoThroughRealProxies() =
        runBlocking {
            // The fixtures' absolute path is the checkout's; the simulator reads the runner's files in place.
            val listing = Path(UPSTREAM_TEST_RESOURCES.substringBefore("/upstream/") + "/build/proxy-check/proxies.txt")
            if (!SystemFileSystem.exists(listing)) return@runBlocking println("PROXY CHECK: not configured")
            val client = ProxyAwareClient()
            val failures = mutableListOf<String>()
            try {
                for (line in listing.readText().lines().filter { it.isNotBlank() }) {
                    // name type host port user password, "-" for no credentials
                    val field = line.trim().split(' ')
                    val config =
                        AppProxyConfig(
                            enabled = true,
                            type = AppProxyType.fromStorageValue(field[1]),
                            host = field[2],
                            port = field[3].toInt(),
                            username = field[4].takeIf { it != "-" }.orEmpty(),
                            password = field[5].takeIf { it != "-" }.orEmpty(),
                        )
                    AppProxyManager.update(config)
                    for (target in TARGETS) {
                        val status = fetch(client, target)
                        println("PROXY CHECK: ${field[0]} $target -> ${status.getOrElse { "failed: ${it::class.simpleName}: ${it.message}" }}")
                        if (status.getOrNull() !in 200..399) failures += "${field[0]} $target"
                    }
                    AppProxyManager.update(config.copy(port = CLOSED_PORT))
                    val around = fetch(client, TARGETS.first())
                    println("PROXY CHECK: ${field[0]} moved to closed port $CLOSED_PORT -> ${around.getOrElse { "failed, as it must" }}")
                    if (around.isSuccess) failures += "${field[0]}: a request went around the proxy"
                }
            } finally {
                AppProxyManager.update(AppProxyConfig())
            }
            assertTrue(failures.isEmpty(), "not through the proxy: $failures")
        }

    private suspend fun fetch(
        client: ProxyAwareClient,
        target: String,
    ): Result<Int> = runCatching { withTimeout(30.seconds) { client.get(Url(target)).get(target).status.value } }

    private companion object {
        val TARGETS = listOf("https://example.com/", "https://www.youtube.com/generate_204")
        const val CLOSED_PORT = 9
    }
}
