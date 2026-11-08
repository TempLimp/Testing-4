package io.github.aedev.flow.player.cache

import io.github.aedev.flow.utils.Log
import io.ktor.util.hex
import io.ktor.util.sha1
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Media3's SimpleCache for the loopback proxy: googlevideo byte ranges kept on disk, keyed by Media3's default cache
 * key (the request's URL), least recently used out first once the total passes [maxBytes] (0: no limit, Media3's
 * NoOpCacheEvictor). A request is answered from disk when one stored span holds all of it; a transfer that completes
 * becomes a span. Spans are files named after their key's hash, start and length, so the index rebuilds from the folder.
 */
class ProxyMediaCache(
    private val dir: Path,
    private val maxBytes: () -> Long,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val clock: () -> Long = { io.github.aedev.flow.player.elapsedRealtime() },
) {
    private class Span(
        val hash: String,
        val start: Long,
        val length: Long,
        val file: Path,
        var lastUsed: Long,
    ) {
        val end: Long get() = start + length
    }

    private val lock = SynchronizedObject()
    private val spans = mutableListOf<Span>()
    private var loaded = false
    private var nextTemp = 0

    /** Bytes held, as SimpleCache.getCacheSpace. */
    fun cacheSpace(): Long = synchronized(lock) { load().sumOf { it.length } }

    /** Bytes [first]..[last] of [key], when one span holds them. */
    fun read(
        key: String,
        first: Long,
        last: Long,
    ): ByteArray? {
        if (last < first) return null
        val hash = hashOf(key)
        val span =
            synchronized(lock) {
                load().firstOrNull { it.hash == hash && it.start <= first && it.end > last }?.also { it.lastUsed = clock() }
            } ?: return null
        return runCatching {
            fileSystem.openReadOnly(span.file).use { handle ->
                val bytes = ByteArray((last - first + 1).toInt())
                var at = 0
                while (at < bytes.size) {
                    val read = handle.read(first - span.start + at, bytes, at, bytes.size - at)
                    if (read <= 0) error("span ${span.file.name} ended early")
                    at += read
                }
                bytes
            }
        }.getOrElse {
            Log.w(TAG, "Dropping an unreadable span: ${it.message}")
            remove(span)
            null
        }
    }

    /** Stores what a transfer of [key] from [first] writes, once [Writer.commit] says it completed. */
    fun writer(
        key: String,
        first: Long,
    ): Writer? =
        runCatching {
            fileSystem.createDirectories(dir)
            val temp = dir / "write-${synchronized(lock) { nextTemp++ }}-${clock()}.tmp"
            Writer(key, first, temp, fileSystem.sink(temp).buffer())
        }.getOrElse {
            Log.w(TAG, "Not caching a transfer: ${it.message}")
            null
        }

    inner class Writer internal constructor(
        private val key: String,
        private val first: Long,
        private val temp: Path,
        private val sink: BufferedSink,
    ) {
        private var written = 0L

        fun write(
            bytes: ByteArray,
            offset: Int,
            count: Int,
        ) {
            sink.write(bytes, offset, count)
            written += count
        }

        /** The transfer completed: its bytes become a span, and the oldest go past the size limit. */
        fun commit() {
            runCatching { sink.close() }
            if (written <= 0) {
                fileSystem.delete(temp, mustExist = false)
                return
            }
            val hash = hashOf(key)
            val file = dir / "${hash}_${first}_$written.span"
            runCatching { fileSystem.atomicMove(temp, file) }.onFailure {
                fileSystem.delete(temp, mustExist = false)
                return
            }
            synchronized(lock) {
                load().removeAll { it.file == file }
                spans += Span(hash, first, written, file, clock())
                evict()
            }
        }

        /** The transfer failed or was cut: nothing is kept. */
        fun abort() {
            runCatching { sink.close() }
            fileSystem.delete(temp, mustExist = false)
        }
    }

    /** Drops every span of [keys], as SimpleCache.removeResource does for each. */
    fun removeKeys(keys: Collection<String>) {
        val hashes = keys.map(::hashOf).toSet()
        synchronized(lock) { load().filter { it.hash in hashes } }.forEach(::remove)
    }

    /** Drops everything, as clearing the video cache in settings does. */
    fun removeAll() {
        synchronized(lock) { load().toList() }.forEach(::remove)
    }

    private fun remove(span: Span) {
        synchronized(lock) { spans.remove(span) }
        runCatching { fileSystem.delete(span.file, mustExist = false) }
    }

    private fun evict() {
        val limit = maxBytes()
        if (limit <= 0) return
        var total = spans.sumOf { it.length }
        while (total > limit && spans.isNotEmpty()) {
            val oldest = spans.minBy { it.lastUsed }
            spans.remove(oldest)
            runCatching { fileSystem.delete(oldest.file, mustExist = false) }
            total -= oldest.length
        }
    }

    /** The spans on disk, read once; temp files of a write that never finished are dropped. */
    private fun load(): MutableList<Span> {
        if (loaded) return spans
        loaded = true
        val files = runCatching { fileSystem.list(dir) }.getOrDefault(emptyList())
        for (file in files) {
            val name = file.name
            if (name.endsWith(".tmp")) {
                runCatching { fileSystem.delete(file) }
                continue
            }
            val parts = name.removeSuffix(".span").split('_')
            if (!name.endsWith(".span") || parts.size != 3) continue
            val start = parts[1].toLongOrNull() ?: continue
            val length = parts[2].toLongOrNull() ?: continue
            val used = runCatching { fileSystem.metadata(file).lastModifiedAtMillis }.getOrNull() ?: 0L
            spans += Span(parts[0], start, length, file, used - Long.MAX_VALUE / 2)
        }
        return spans
    }

    private fun hashOf(key: String): String = hex(sha1(key.encodeToByteArray()))

    private companion object {
        const val TAG = "PlayerCacheManager"
    }
}
