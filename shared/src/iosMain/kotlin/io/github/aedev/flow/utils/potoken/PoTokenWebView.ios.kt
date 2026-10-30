@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.utils.potoken

import io.github.aedev.flow.utils.Log
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.WebKit.WKContentRuleList
import platform.WebKit.WKContentRuleListStore
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Upstream's Android WebView as a WKWebView. The page, scripts and BotGuard requests are upstream's
 * ([BotGuardPage]); `addJavascriptInterface` becomes a user script defining the `PoTokenWebView` bridge
 * over a script message handler, the console's "Uncaught" errors become the page's error events, and
 * `blockNetworkLoads` becomes a content rule list blocking every subresource load.
 */
actual class PoTokenWebView private constructor(
    contentRuleList: WKContentRuleList,
) {
    private val bridge = BridgeHandler(this)
    private val webView: WKWebView
    private val scope = MainScope()
    private val poTokenContinuations = ConcurrentMap<String, Continuation<String>>()
    private val exceptionHandler =
        CoroutineExceptionHandler { _, t ->
            onAttestationError(t)
        }

    @Volatile
    private var expirationInstant: Instant? = null

    // Resumed exactly once per [attest] run. Errors arriving with no attestation in flight only
    // fail pending mints, and a console error after a successful attestation must not re-resume it.
    private val attestationContinuation = AtomicReference<CancellableContinuation<Unit>?>(null)

    // Reloading the page cannot cancel an already-dispatched BotGuard HTTP request, so each
    // attestation is stamped and late responses from a superseded run are dropped rather than
    // evaluated against the page that replaced them.
    private val attestationGeneration = AtomicInt(0)

    @Volatile
    private var broken = false

    @Volatile
    private var destroyed = false

    actual val isDestroyed: Boolean
        get() = destroyed

    init {
        val configuration = WKWebViewConfiguration()
        configuration.userContentController.addScriptMessageHandler(bridge, HANDLER)
        configuration.userContentController.addUserScript(
            WKUserScript(
                source = BRIDGE_SCRIPT,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = true,
            ),
        )
        configuration.userContentController.addContentRuleList(contentRuleList)
        webView = WKWebView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0), configuration = configuration)
        webView.customUserAgent = BotGuardPage.USER_AGENT
    }

    actual suspend fun attest() {
        check(!destroyed) { "attest() called on a destroyed PoTokenWebView" }
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                attestationGeneration.incrementAndFetch()
                broken = false
                attestationContinuation.store(cont)
                cont.invokeOnCancellation { attestationContinuation.compareAndSet(cont, null) }
                loadHtmlAndObtainBotguard()
            }
        }
    }

    private fun resumeAttestation() {
        attestationContinuation.exchange(null)?.resume(Unit)
    }

    private fun failAttestation(error: Throwable) {
        attestationContinuation.exchange(null)?.resumeWithException(error)
    }

    private fun loadHtmlAndObtainBotguard() {
        Log.d(TAG, "loadHtmlAndObtainBotguard() called")
        scope.launch(exceptionHandler) {
            // calls downloadAndRunBotguard() when the page has finished loading
            webView.loadHTMLString(BotGuardPage.html, NSURL.URLWithString(BotGuardPage.BASE_URL))
        }
    }

    internal fun onBridgeCall(
        method: String,
        args: List<String>,
    ) {
        when (method) {
            "downloadAndRunBotguard" -> downloadAndRunBotguard()
            "onRunBotguardResult" -> onRunBotguardResult(args.getOrElse(0) { "" })
            "onJsInitializationError" -> onJsInitializationError(args.getOrElse(0) { "" })
            "onMinterCreated" -> onMinterCreated()
            "onObtainPoTokenResult" -> onObtainPoTokenResult(args.getOrElse(0) { "" }, args.getOrElse(1) { "" })
            "onObtainPoTokenError" -> onObtainPoTokenError(args.getOrElse(0) { "" }, args.getOrElse(1) { "" })
            "uncaught" -> onUncaughtError(args.getOrElse(0) { "" }, args.getOrElse(1) { "" })
        }
    }

    private fun onUncaughtError(
        message: String,
        source: String,
    ) {
        val fmt = "\"$message\", source: $source"
        val exception = BadWebViewException(fmt)
        Log.e(TAG, "Uncaught JS error in BotGuard WebView: $fmt")
        broken = true
        // No-ops when nothing is attesting, so a post-attestation error only fails in-flight mints
        // and leaves isExpired to trigger the next re-attestation.
        failAttestation(exception)
        popAllPoTokenContinuations().forEach { (_, cont) -> cont.resumeWithException(exception) }
    }

    private fun downloadAndRunBotguard() {
        Log.d(TAG, "downloadAndRunBotguard() called")
        makeBotguardServiceRequest(BotGuardPage.CREATE_URL, "[ \"${BotGuardPage.REQUEST_KEY}\" ]") { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webView.evaluateJavaScript(BotGuardPage.runBotGuardScript(parsedChallengeData), null)
        }
    }

    private fun onJsInitializationError(error: String) {
        Log.e(TAG, "Initialization error from JavaScript: $error")
        onAttestationError(buildExceptionForJsError(error))
    }

    private fun onRunBotguardResult(botguardResponse: String) {
        Log.d(TAG, "botguardResponse: $botguardResponse")
        makeBotguardServiceRequest(
            BotGuardPage.GENERATE_IT_URL,
            "[ \"${BotGuardPage.REQUEST_KEY}\", \"$botguardResponse\" ]",
        ) { responseBody ->
            Log.d(TAG, "GenerateIT response: $responseBody")
            try {
                val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)
                Log.d(TAG, "Parsed integrityToken (${integrityToken.take(50)}...), expires in $expirationTimeInSeconds sec")
                // leave 10 minutes of margin just to be sure
                expirationInstant = Clock.System.now() + expirationTimeInSeconds.seconds - 10.minutes
                Log.d(TAG, "Evaluating createPoTokenMinter JavaScript...")
                webView.evaluateJavaScript(BotGuardPage.createMinterScript(integrityToken), null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse integrity token data: ${e.message}", e)
                onAttestationError(PoTokenException("parseIntegrityTokenData failed: ${e.message}"))
            }
        }
    }

    private fun onMinterCreated() {
        Log.d(TAG, "poToken minter created successfully, attestation complete")
        resumeAttestation()
    }

    actual suspend fun generatePoToken(identifier: String): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                Log.d(TAG, "generatePoToken() called with identifier $identifier")
                poTokenContinuations[identifier] = cont
                cont.invokeOnCancellation { poTokenContinuations.remove(identifier) }
                webView.evaluateJavaScript(BotGuardPage.obtainPoTokenScript(identifier), null)
            }
        }

    private fun onObtainPoTokenError(
        identifier: String,
        error: String,
    ) {
        Log.e(TAG, "obtainPoToken error from JavaScript: $error")
        poTokenContinuations.remove(identifier)?.resumeWithException(buildExceptionForJsError(error))
    }

    private fun onObtainPoTokenResult(
        identifier: String,
        poTokenU8: String,
    ) {
        Log.d(TAG, "Generated poToken (before decoding): identifier=$identifier poTokenU8=$poTokenU8")
        val poToken =
            try {
                u8ToBase64(poTokenU8)
            } catch (t: Throwable) {
                poTokenContinuations.remove(identifier)?.resumeWithException(t)
                return
            }
        Log.d(TAG, "Generated poToken: identifier=$identifier poToken=$poToken")
        poTokenContinuations.remove(identifier)?.resume(poToken)
    }

    actual val isExpired: Boolean
        get() = broken || expirationInstant.let { it == null || Clock.System.now() > it }

    private fun popAllPoTokenContinuations(): Map<String, Continuation<String>> {
        val result = poTokenContinuations.toMap()
        result.keys.forEach { poTokenContinuations.remove(it) }
        return result
    }

    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        val generation = attestationGeneration.load()
        scope.launch(exceptionHandler) {
            val (httpCode, body) = withContext(Dispatchers.IO) { BotGuardPage.serviceRequest(url, data) }
            if (httpCode != 200 || body == null) {
                onAttestationError(PoTokenException("Invalid response code: $httpCode"))
            } else {
                if (generation != attestationGeneration.load()) {
                    Log.d(TAG, "Dropping BotGuard response from a superseded attestation")
                    return@launch
                }
                handleResponseBody(body)
            }
        }
    }

    /**
     * Marks the attestation failed without tearing the web view down, so the instance stays
     * reusable and [attest] can simply be run again on it. Only [close] destroys it.
     */
    private fun onAttestationError(error: Throwable) {
        broken = true
        failAttestation(error)
    }

    actual fun close() {
        if (destroyed) return
        destroyed = true
        scope.cancel()
        webView.configuration.userContentController.removeScriptMessageHandlerForName(HANDLER)
        webView.stopLoading()
        webView.loadHTMLString("", null)
    }

    actual companion object {
        private const val TAG = "PoTokenWebView"
        private const val HANDLER = "flowPoToken"

        // upstream's @JavascriptInterface methods, posted to the native side in call order.
        private val BRIDGE_SCRIPT =
            """
            (function() {
                function post(m, a) { window.webkit.messageHandlers.$HANDLER.postMessage({ m: m, a: a || [] }); }
                window.${BotGuardPage.JS_INTERFACE} = {
                    downloadAndRunBotguard: function() { post('downloadAndRunBotguard'); },
                    onRunBotguardResult: function(r) { post('onRunBotguardResult', [String(r)]); },
                    onJsInitializationError: function(e) { post('onJsInitializationError', [String(e)]); },
                    onMinterCreated: function() { post('onMinterCreated'); },
                    onObtainPoTokenResult: function(id, t) { post('onObtainPoTokenResult', [String(id), String(t)]); },
                    onObtainPoTokenError: function(id, e) { post('onObtainPoTokenError', [String(id), String(e)]); }
                };
                window.addEventListener('error', function(e) {
                    post('uncaught', ['Uncaught ' + e.message, String(e.filename) + ' (' + e.lineno + ')']);
                });
                window.addEventListener('unhandledrejection', function(e) {
                    post('uncaught', ['Uncaught (in promise) ' + e.reason, '']);
                });
            })();
            """.trimIndent()

        // Every subresource load: the page itself comes from loadHTMLString and is not one.
        private const val BLOCK_ALL_LOADS =
            """[{"trigger":{"url-filter":".*","resource-type":["image","style-sheet","script","font","raw","svg-document","media","popup","ping","fetch","websocket","other"]},"action":{"type":"block"}}]"""

        actual suspend fun create(): PoTokenWebView {
            val rules = withContext(Dispatchers.Main) { blockAllLoads() }
            val instance = withContext(Dispatchers.Main) { PoTokenWebView(rules) }
            try {
                instance.attest()
            } catch (t: Throwable) {
                withContext(NonCancellable + Dispatchers.Main) { instance.close() }
                throw t
            }
            return instance
        }

        private suspend fun blockAllLoads(): WKContentRuleList =
            suspendCancellableCoroutine { cont ->
                val store = WKContentRuleListStore.defaultStore()
                if (store == null) {
                    cont.resumeWithException(BadWebViewException("No content rule list store"))
                    return@suspendCancellableCoroutine
                }
                store.compileContentRuleListForIdentifier("io.github.aedev.flow.potoken.block", BLOCK_ALL_LOADS) { list, error ->
                    if (list != null) {
                        cont.resume(list)
                    } else {
                        cont.resumeWithException(BadWebViewException("Could not block network loads: ${error?.localizedDescription}"))
                    }
                }
            }
    }
}

private class BridgeHandler(
    private val owner: PoTokenWebView,
) : NSObject(),
    WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
        val method = body["m"] as? String ?: return
        val args = (body["a"] as? List<*>)?.map { it as? String ?: it?.toString().orEmpty() }.orEmpty()
        owner.onBridgeCall(method, args)
    }
}

// A WKWebView needs an app around it: the Kotlin test executable has no bundle and no main run loop to answer it.
internal actual val webViewSupported: Boolean = NSBundle.mainBundle.bundlePath.endsWith(".app")

internal actual val webViewDispatcher: CoroutineDispatcher = Dispatchers.Main

internal actual suspend fun clearWebViewState() {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            WKWebsiteDataStore.defaultDataStore().removeDataOfTypes(
                WKWebsiteDataStore.allWebsiteDataTypes(),
                modifiedSince = NSDate(timeIntervalSinceReferenceDate = -978_307_200.0),
            ) { cont.resume(Unit) }
        }
    }
}
