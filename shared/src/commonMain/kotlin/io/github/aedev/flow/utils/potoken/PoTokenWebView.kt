package io.github.aedev.flow.utils.potoken

import io.github.aedev.flow.network.ProxyAwareClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher

/**
 * This is based and ported from Metrolist,
 * see https://github.com/MetrolistGroup/Metrolist for the original code and license.
 *
 * The BotGuard page: upstream's `po_token.html` in a web view with a `https://www.youtube.com` origin
 * and network loads blocked, driven through a JavaScript bridge named `PoTokenWebView`. WKWebView on
 * iOS; the jvm test target has no web view and reports itself unsupported, as Android does without one.
 */
expect class PoTokenWebView {
    /**
     * Runs a full BotGuard attestation — load the page, run BotGuard, obtain an `integrityToken`,
     * create the minter — and suspends until it completes. Safe to call repeatedly on one instance.
     */
    suspend fun attest()

    suspend fun generatePoToken(identifier: String): String

    val isExpired: Boolean

    val isDestroyed: Boolean

    fun close()

    companion object {
        /** Builds a web view and runs its first attestation. */
        suspend fun create(): PoTokenWebView
    }
}

/** Whether this platform has a web view to run BotGuard in (Android's `CookieManager.getInstance()` check). */
internal expect val webViewSupported: Boolean

/** The thread web view calls run on: the main thread on iOS, as Android's WebView requires. */
internal expect val webViewDispatcher: CoroutineDispatcher

/** Clears the web view's cookies and storage, the identity BotGuard keeps grading. */
internal expect suspend fun clearWebViewState()

/** Upstream's PoTokenWebView constants and scripts, shared by the platform web views. */
internal object BotGuardPage {
    const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
    const val JS_INTERFACE = "PoTokenWebView"
    const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
    const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
    const val BASE_URL = "https://www.youtube.com"

    private val httpClient = ProxyAwareClient()

    /** The page with the call that starts the attestation once it has loaded. */
    val html: String = PO_TOKEN_HTML.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>")

    fun runBotGuardScript(parsedChallengeData: String): String =
        """try {
            data = $parsedChallengeData
            runBotGuard(data).then(function (result) {
                this.webPoSignalOutput = result.webPoSignalOutput
                $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
            }, function (error) {
                $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
            })
        } catch (error) {
            $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
        }"""

    fun createMinterScript(integrityToken: String): String =
        """try {
            console.log('[JS] Setting integrityToken and calling createPoTokenMinter...');
            this.integrityToken = $integrityToken
            console.log('[JS] integrityToken set, now calling createPoTokenMinter...');
            createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                console.log('[JS] createPoTokenMinter .then() resolved!');
                $JS_INTERFACE.onMinterCreated()
            }).catch(function(error) {
                console.log('[JS] createPoTokenMinter .catch() error: ' + error);
                $JS_INTERFACE.onJsInitializationError(error + "\n" + (error.stack || ''))
            })
        } catch (error) {
            console.log('[JS] createPoTokenMinter SYNC error: ' + error);
            $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
        }"""

    fun obtainPoTokenScript(identifier: String): String =
        """try {
            identifier = "$identifier"
            u8Identifier = ${stringToU8(identifier)}
            obtainPoToken(u8Identifier).then(function(poTokenU8) {
                poTokenU8String = poTokenU8.join(",")
                $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
            }).catch(function(error) {
                $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + (error.stack || ''))
            })
        } catch (error) {
            $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
        }"""

    /** POSTs [data] to a BotGuard endpoint; null with the HTTP code when it does not answer 200. */
    suspend fun serviceRequest(
        url: String,
        data: String,
    ): Pair<Int, String?> {
        val response =
            httpClient.get().post(url) {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/json")
                header("Content-Type", "application/json+protobuf")
                header("x-goog-api-key", GOOGLE_API_KEY)
                header("x-user-agent", "grpc-web-javascript/0.1")
                setBody(data)
            }
        val code = response.status.value
        return code to if (code == 200) response.bodyAsText() else null
    }
}
