package io.github.aedev.flow.player.resolver

import io.github.aedev.flow.network.platformHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** iOS reclaims a suspended app's listening sockets; the proxy notices and listens again (S11, phone run 2). */
class LocalHlsProxyRestartJvmTest {
    @Test
    fun aListenerThatWasTakenAwayIsStartedAgainAndServes() =
        runBlocking {
            LocalHlsProxy.start()
            assertFalse(LocalHlsProxy.restartIfGone())

            LocalHlsProxy.dropListenerForTest()

            assertTrue(LocalHlsProxy.restartIfGone())
            val port = LocalHlsProxy.start()
            assertEquals("ok", platformHttpClient().get("http://127.0.0.1:$port/alive").bodyAsText())
            assertFalse(LocalHlsProxy.restartIfGone())
        }
}
