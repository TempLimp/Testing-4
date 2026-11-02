package io.github.aedev.flow.utils.potoken

import io.github.aedev.flow.utils.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The jvm test target's web view: upstream's BotGuard page in a headless Chromium (Edge or Chrome, whichever is
 * installed) driven over the DevTools protocol, so live tests on a desktop mint real PoTokens. The flow is the iOS
 * WKWebView's: the page is served with a `https://www.youtube.com` origin by intercepting its own navigation, every
 * other load is refused, and the `PoTokenWebView` bridge posts through a DevTools binding. Never shipped; with no
 * browser installed the target reports no web view, as before.
 */
actual class PoTokenWebView private constructor(
    private val browser: HeadlessChromium,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t -> onAttestationError(t) })
    private val poTokenContinuations = ConcurrentHashMap<String, Continuation<String>>()
    private val attestationContinuation = AtomicReference<CancellableContinuation<Unit>?>(null)
    private val attestationGeneration = AtomicInteger(0)

    @Volatile
    private var expirationInstant: Instant? = null

    @Volatile
    private var broken = false

    @Volatile
    private var destroyed = false

    actual val isDestroyed: Boolean
        get() = destroyed

    init {
        browser.onBinding = { payload ->
            val call = Json.parseToJsonElement(payload).jsonObject
            onBridgeCall(
                call["m"]?.jsonPrimitive?.content.orEmpty(),
                call["a"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
            )
        }
    }

    actual suspend fun attest() {
        check(!destroyed) { "attest() called on a destroyed PoTokenWebView" }
        suspendCancellableCoroutine { cont ->
            attestationGeneration.incrementAndGet()
            broken = false
            attestationContinuation.set(cont)
            cont.invokeOnCancellation { attestationContinuation.compareAndSet(cont, null) }
            scope.launch { browser.load("${BotGuardPage.BASE_URL}/", BotGuardPage.html) }
        }
    }

    private fun onBridgeCall(
        method: String,
        args: List<String>,
    ) {
        when (method) {
            "downloadAndRunBotguard" -> downloadAndRunBotguard()
            "onRunBotguardResult" -> onRunBotguardResult(args.getOrElse(0) { "" })
            "onJsInitializationError" -> onAttestationError(buildExceptionForJsError(args.getOrElse(0) { "" }))
            "onMinterCreated" -> attestationContinuation.getAndSet(null)?.resume(Unit)
            "onObtainPoTokenResult" -> onObtainPoTokenResult(args.getOrElse(0) { "" }, args.getOrElse(1) { "" })
            "onObtainPoTokenError" ->
                poTokenContinuations.remove(args.getOrElse(0) { "" })?.resumeWithException(buildExceptionForJsError(args.getOrElse(1) { "" }))
            "uncaught" -> onUncaughtError("\"${args.getOrElse(0) { "" }}\", source: ${args.getOrElse(1) { "" }}")
        }
    }

    private fun onUncaughtError(description: String) {
        val exception = BadWebViewException(description)
        Log.e(TAG, "Uncaught JS error in BotGuard page: $description")
        broken = true
        attestationContinuation.getAndSet(null)?.resumeWithException(exception)
        poTokenContinuations.keys.toList().forEach { poTokenContinuations.remove(it)?.resumeWithException(exception) }
    }

    private fun downloadAndRunBotguard() {
        makeBotguardServiceRequest(BotGuardPage.CREATE_URL, "[ \"${BotGuardPage.REQUEST_KEY}\" ]") { responseBody ->
            browser.evaluate(BotGuardPage.runBotGuardScript(parseChallengeData(responseBody)))
        }
    }

    private fun onRunBotguardResult(botguardResponse: String) {
        makeBotguardServiceRequest(
            BotGuardPage.GENERATE_IT_URL,
            "[ \"${BotGuardPage.REQUEST_KEY}\", \"$botguardResponse\" ]",
        ) { responseBody ->
            val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)
            // leave 10 minutes of margin just to be sure
            expirationInstant = Clock.System.now() + expirationTimeInSeconds.seconds - 10.minutes
            browser.evaluate(BotGuardPage.createMinterScript(integrityToken))
        }
    }

    actual suspend fun generatePoToken(identifier: String): String =
        suspendCancellableCoroutine { cont ->
            poTokenContinuations[identifier] = cont
            cont.invokeOnCancellation { poTokenContinuations.remove(identifier) }
            scope.launch { browser.evaluate(BotGuardPage.obtainPoTokenScript(identifier)) }
        }

    private fun onObtainPoTokenResult(
        identifier: String,
        poTokenU8: String,
    ) {
        val poToken =
            try {
                u8ToBase64(poTokenU8)
            } catch (t: Throwable) {
                poTokenContinuations.remove(identifier)?.resumeWithException(t)
                return
            }
        poTokenContinuations.remove(identifier)?.resume(poToken)
    }

    actual val isExpired: Boolean
        get() = broken || expirationInstant.let { it == null || Clock.System.now() > it }

    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: suspend (String) -> Unit,
    ) {
        val generation = attestationGeneration.get()
        scope.launch {
            val (httpCode, body) = BotGuardPage.serviceRequest(url, data)
            if (httpCode != 200 || body == null) {
                onAttestationError(PoTokenException("Invalid response code: $httpCode"))
            } else if (generation == attestationGeneration.get()) {
                handleResponseBody(body)
            }
        }
    }

    private fun onAttestationError(error: Throwable) {
        broken = true
        attestationContinuation.getAndSet(null)?.resumeWithException(error)
    }

    actual fun close() {
        if (destroyed) return
        destroyed = true
        scope.cancel()
        browser.close()
    }

    actual companion object {
        private const val TAG = "PoTokenWebView"

        actual suspend fun create(): PoTokenWebView {
            val instance = PoTokenWebView(HeadlessChromium.launch())
            try {
                withTimeout(60.seconds) { instance.attest() }
            } catch (t: Throwable) {
                instance.close()
                throw t
            }
            return instance
        }
    }
}

/** One headless browser and its single page, over the DevTools protocol's WebSocket. */
internal class HeadlessChromium private constructor(
    private val process: Process,
    private val profile: File,
) : WebSocket.Listener {
    private val nextId = AtomicInteger(0)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val partial = StringBuilder()
    private lateinit var socket: WebSocket

    @Volatile
    private var page: Pair<String, String>? = null

    @Volatile
    var onBinding: (String) -> Unit = {}

    /** Navigates to [url], answered with [html] instead of the network. */
    suspend fun load(
        url: String,
        html: String,
    ) {
        page = url to html
        send("Page.navigate", buildJsonObject { put("url", url) })
    }

    suspend fun evaluate(script: String) {
        send("Runtime.evaluate", buildJsonObject { put("expression", script) })
    }

    fun close() {
        scope.cancel()
        if (::socket.isInitialized) socket.abort()
        process.destroy()
        process.onExit().thenRun { profile.deleteRecursively() }
    }

    private suspend fun send(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
    ): JsonObject {
        val id = nextId.incrementAndGet()
        val reply = CompletableDeferred<JsonObject>()
        pending[id] = reply
        val message =
            buildJsonObject {
                put("id", id)
                put("method", method)
                put("params", params)
            }
        synchronized(this) { socket.sendText(message.toString(), true) }.await()
        val answer = withTimeout(30.seconds) { reply.await() }
        answer["error"]?.let { throw BadWebViewException("$method failed: $it") }
        return answer["result"]?.jsonObject ?: JsonObject(emptyMap())
    }

    override fun onText(
        webSocket: WebSocket,
        data: CharSequence,
        last: Boolean,
    ): CompletionStage<*>? {
        partial.append(data)
        if (last) {
            val text = partial.toString()
            partial.setLength(0)
            onMessage(Json.parseToJsonElement(text).jsonObject)
        }
        webSocket.request(1)
        return null
    }

    private fun onMessage(message: JsonObject) {
        message["id"]?.jsonPrimitive?.int?.let { id ->
            pending.remove(id)?.complete(message)
            return
        }
        val params = message["params"]?.jsonObject ?: return
        when (message["method"]?.jsonPrimitive?.content) {
            "Runtime.bindingCalled" -> onBinding(params["payload"]!!.jsonPrimitive.content)
            "Fetch.requestPaused" -> scope.launch { answer(params) }
        }
    }

    // The page itself is answered from memory; every other load is refused, as WKWebView's content rule list does.
    private suspend fun answer(paused: JsonObject) {
        val requestId = paused["requestId"]!!.jsonPrimitive.content
        val url = paused["request"]!!.jsonObject["url"]!!.jsonPrimitive.content
        val current = page
        if (current != null && url == current.first) {
            send(
                "Fetch.fulfillRequest",
                buildJsonObject {
                    put("requestId", requestId)
                    put("responseCode", 200)
                    putJsonArray("responseHeaders") {
                        add(
                            buildJsonObject {
                                put("name", "Content-Type")
                                put("value", "text/html; charset=utf-8")
                            },
                        )
                    }
                    put("body", Base64.getEncoder().encodeToString(current.second.encodeToByteArray()))
                },
            )
        } else {
            send(
                "Fetch.failRequest",
                buildJsonObject {
                    put("requestId", requestId)
                    put("errorReason", "BlockedByClient")
                },
            )
        }
    }

    companion object {
        private const val BINDING = "flowPoToken"

        /** `FLOW_CHROMIUM`, else Edge or Chrome where their installers put them. */
        fun executable(): File? =
            listOfNotNull(
                System.getenv("FLOW_CHROMIUM"),
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/usr/bin/microsoft-edge",
            ).map(::File).firstOrNull { it.canExecute() }

        suspend fun launch(): HeadlessChromium =
            withContext(Dispatchers.IO) {
                val executable = executable() ?: throw BadWebViewException("No Chromium-based browser installed")
                val profile = Files.createTempDirectory("flow-botguard").toFile()
                val process =
                    ProcessBuilder(
                        executable.path,
                        "--headless=new",
                        "--remote-debugging-port=0",
                        "--user-data-dir=${profile.path}",
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--disable-extensions",
                        "about:blank",
                    ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                val browser = HeadlessChromium(process, profile)
                try {
                    // The browser writes the port it chose on the first line of this file once it listens.
                    val portFile = File(profile, "DevToolsActivePort")
                    val port =
                        withTimeout(30.seconds) {
                            while (portFile.readLinesOrEmpty().size < 2) {
                                // A browser that cannot start here (a sandbox the host refuses) exits at once.
                                if (!process.isAlive) throw BadWebViewException("$executable exited with ${process.exitValue()}")
                                delay(100)
                            }
                            portFile.readLines().first().trim()
                        }
                    val http = HttpClient.newHttpClient()
                    val targets =
                        http
                            .send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/json/list")).build(), HttpResponse.BodyHandlers.ofString())
                            .body()
                    val pageSocket =
                        Json
                            .parseToJsonElement(targets)
                            .jsonArray
                            .map { it.jsonObject }
                            .first { it["type"]?.jsonPrimitive?.content == "page" }["webSocketDebuggerUrl"]!!
                            .jsonPrimitive.content
                    browser.socket = http.newWebSocketBuilder().buildAsync(URI(pageSocket), browser).await()
                    browser.send("Runtime.enable")
                    browser.send("Runtime.addBinding", buildJsonObject { put("name", BINDING) })
                    browser.send("Page.enable")
                    browser.send(
                        "Page.addScriptToEvaluateOnNewDocument",
                        buildJsonObject { put("source", BotGuardPage.bridgeScript("function(m, a) { window.$BINDING(JSON.stringify({ m: m, a: a })); }")) },
                    )
                    browser.send("Emulation.setUserAgentOverride", buildJsonObject { put("userAgent", BotGuardPage.USER_AGENT) })
                    browser.send(
                        "Fetch.enable",
                        buildJsonObject {
                            putJsonArray("patterns") { add(buildJsonObject { put("urlPattern", "*") }) }
                        },
                    )
                    browser
                } catch (t: Throwable) {
                    browser.close()
                    throw t
                }
            }

        private fun File.readLinesOrEmpty(): List<String> = if (exists()) readLines() else emptyList()
    }
}

internal actual val webViewSupported: Boolean = HeadlessChromium.executable() != null

internal actual val webViewDispatcher: CoroutineDispatcher = Dispatchers.Default

// Each PoTokenWebView runs in a fresh browser profile, deleted with it, so there is no state left to clear.
internal actual suspend fun clearWebViewState() {}
