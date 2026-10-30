package io.github.aedev.flow.utils.cipher

import io.github.aedev.flow.network.ProxyAwareClient
import io.github.aedev.flow.utils.Log
import io.github.aedev.flow.utils.appFilesDir
import io.github.aedev.flow.utils.readText
import io.github.aedev.flow.utils.writeText
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Clock

/**
 * This is based and ported from Metrolist,
 * see https://github.com/MetrolistGroup/Metrolist for the original code and license.
 *
 * Fetches and caches YouTube's player.js for cipher operations.
 */
object PlayerJsFetcher {
    private const val TAG = "Flow_CipherFetcher"
    private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
    private const val PLAYER_JS_URL_TEMPLATE = "https://www.youtube.com/s/player/%s/player_ias.vflset/en_GB/base.js"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    private val httpClient = ProxyAwareClient()

    // A timed-out caller stops waiting, but the download it started still lands in the disk cache,
    // so the next extraction finds the script instead of paying for it again.
    private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchLock = Mutex()
    private var inFlightFetch: Deferred<Pair<String, String>?>? = null

    private val PLAYER_HASH_REGEX = Regex("""\\?/s\\?/player\\?/([a-zA-Z0-9_-]+)\\?/""")

    private fun getCacheDir(): Path = Path(appFilesDir(), "cipher_cache")

    private fun getCacheFile(hash: String): Path = Path(getCacheDir(), "player_$hash.js")

    private fun getHashFile(): Path = Path(getCacheDir(), "current_hash.txt")

    suspend fun getPlayerJs(forceRefresh: Boolean = false): Pair<String, String>? {
        Log.d(TAG, "getPlayerJs: forceRefresh=$forceRefresh")
        if (!forceRefresh) {
            withContext(Dispatchers.IO) { readFromCache() }?.let { cached ->
                Log.d(TAG, "Cache hit: hash=${cached.second}, length=${cached.first.length}")
                return cached
            }
            Log.d(TAG, "Cache miss, fetching fresh...")
        }
        val fetch =
            fetchLock.withLock {
                inFlightFetch?.takeIf { it.isActive }
                    ?: fetchScope.async { fetchAndCache() }.also { inFlightFetch = it }
            }
        return fetch.await()
    }

    private suspend fun fetchAndCache(): Pair<String, String>? =
        try {
            val cacheDir = getCacheDir()
            if (!SystemFileSystem.exists(cacheDir)) SystemFileSystem.createDirectories(cacheDir)

            val hash = fetchPlayerHash()
            if (hash == null) {
                Log.e(TAG, "Failed to extract player hash from iframe_api")
                null
            } else {
                Log.d(TAG, "Player hash: $hash")
                val playerJs = downloadPlayerJs(hash)
                if (playerJs == null) {
                    Log.e(TAG, "Failed to download player JS for hash=$hash")
                    null
                } else {
                    Log.d(TAG, "Downloaded player.js: hash=$hash, length=${playerJs.length}")
                    writeToCache(hash, playerJs)
                    Pair(playerJs, hash)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlayerJs exception: ${e.message}", e)
            null
        }

    fun invalidateCache() {
        Log.d(TAG, "Invalidating cipher cache...")
        try {
            val cacheDir = getCacheDir()
            if (SystemFileSystem.exists(cacheDir)) {
                SystemFileSystem.list(cacheDir).forEach { SystemFileSystem.delete(it, mustExist = false) }
            }
            Log.d(TAG, "Cache invalidated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invalidate cache: ${e.message}", e)
        }
    }

    private fun readFromCache(): Pair<String, String>? {
        return try {
            val hashFile = getHashFile()
            if (!SystemFileSystem.exists(hashFile)) return null

            val hashData = hashFile.readText().split("\n")
            if (hashData.size < 2) return null

            val hash = hashData[0]
            val timestamp = hashData[1].toLongOrNull() ?: return null
            val ageMs = Clock.System.now().toEpochMilliseconds() - timestamp

            if (ageMs > CACHE_TTL_MS) {
                Log.d(TAG, "Cache expired: hash=$hash, age=${ageMs / 3600000}h")
                return null
            }

            val cacheFile = getCacheFile(hash)
            if (!SystemFileSystem.exists(cacheFile)) return null

            val playerJs = cacheFile.readText()
            if (playerJs.isEmpty()) return null

            Log.d(TAG, "Cache valid: hash=$hash, length=${playerJs.length}, age=${ageMs / 3600000}h")
            Pair(playerJs, hash)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache: ${e.message}", e)
            null
        }
    }

    private fun writeToCache(
        hash: String,
        playerJs: String,
    ) {
        try {
            val cacheDir = getCacheDir()
            // Clean old cache files
            SystemFileSystem.list(cacheDir).filter { it.name.startsWith("player_") }.forEach { SystemFileSystem.delete(it, mustExist = false) }
            getCacheFile(hash).writeText(playerJs)
            getHashFile().writeText("$hash\n${Clock.System.now().toEpochMilliseconds()}")
            Log.d(TAG, "Cache written: hash=$hash")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing cache: ${e.message}", e)
        }
    }

    private suspend fun fetchPlayerHash(): String? {
        Log.d(TAG, "Fetching iframe_api...")
        val response = httpClient.get().get(IFRAME_API_URL) { header(HttpHeaders.UserAgent, USER_AGENT) }
        Log.d(TAG, "iframe_api response: HTTP ${response.status.value}")
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()
        val match = PLAYER_HASH_REGEX.find(body)
        val hash = match?.groupValues?.get(1)
        Log.d(TAG, "Player hash: $hash")
        return hash
    }

    private suspend fun downloadPlayerJs(hash: String): String? {
        val url = PLAYER_JS_URL_TEMPLATE.replace("%s", hash)
        Log.d(TAG, "Downloading player.js from: $url")
        val response = httpClient.get().get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
        Log.d(TAG, "player.js response: HTTP ${response.status.value}")
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()
        Log.d(TAG, "player.js downloaded: ${body.length} chars")
        return body
    }
}
