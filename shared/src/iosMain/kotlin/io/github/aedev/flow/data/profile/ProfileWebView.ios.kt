package io.github.aedev.flow.data.profile

import io.github.aedev.flow.utils.potoken.webViewSupported
import io.ktor.util.sha1
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSUUID
import platform.WebKit.WKWebsiteDataStore
import kotlin.coroutines.resume

/** The data store the running profile's web views use: WebKit's default for Default, one per profile otherwise. */
internal fun profileWebsiteDataStore(): WKWebsiteDataStore =
    ProfileWebView.dataStoreId()?.let { WKWebsiteDataStore.dataStoreForIdentifier(dataStoreUuid(it)) }
        ?: WKWebsiteDataStore.defaultDataStore()

internal actual suspend fun deleteProfileWebData(id: String): Boolean {
    // WebKit answers on the main run loop, which only an app has; the Kotlin test runner has none.
    if (!webViewSupported) return false
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            WKWebsiteDataStore.removeDataStoreForIdentifier(dataStoreUuid(id)) { error -> continuation.resume(error == null) }
        }
    }
}

private val UuidText = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

// Registry ids are random UUIDs; any other safe id still gets a store of its own, named by its digest.
@OptIn(ExperimentalForeignApi::class)
internal fun dataStoreUuid(id: String): NSUUID {
    if (UuidText.matches(id)) return NSUUID(uUIDString = id)
    val bytes = sha1(id.encodeToByteArray()).copyOf(16)
    return bytes.usePinned { NSUUID(uUIDBytes = it.addressOf(0).reinterpret<UByteVar>()) }
}
