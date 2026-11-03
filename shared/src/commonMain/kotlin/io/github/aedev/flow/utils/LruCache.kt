package io.github.aedev.flow.utils

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * `android.util.LruCache` as upstream uses it: every entry counts one, a read refreshes the entry, and putting
 * past [maxSize] evicts the least recently used. Thread-safe, like Android's.
 */
class LruCache<K : Any, V : Any>(
    private val maxSize: Int,
) {
    private val lock = SynchronizedObject()
    private val entries = LinkedHashMap<K, V>()

    init {
        require(maxSize > 0) { "maxSize <= 0" }
    }

    operator fun get(key: K): V? =
        synchronized(lock) {
            val value = entries.remove(key) ?: return null
            entries[key] = value
            value
        }

    fun put(
        key: K,
        value: V,
    ): V? =
        synchronized(lock) {
            val previous = entries.remove(key)
            entries[key] = value
            while (entries.size > maxSize) entries.remove(entries.keys.first())
            previous
        }

    fun remove(key: K): V? = synchronized(lock) { entries.remove(key) }

    fun evictAll() = synchronized(lock) { entries.clear() }

    fun size(): Int = synchronized(lock) { entries.size }

    fun snapshot(): Map<K, V> = synchronized(lock) { LinkedHashMap(entries) }
}
