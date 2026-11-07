package io.github.aedev.flow.utils

import io.ktor.util.collections.ConcurrentMap

/**
 * `ConcurrentHashMap.putIfAbsent`, which Ktor's common ConcurrentMap lacks: atomic through its
 * `computeIfAbsent`. Returns the value already there, or null when [value] went in.
 */
fun <K : Any, V : Any> ConcurrentMap<K, V>.putIfAbsent(
    key: K,
    value: V,
): V? {
    var inserted = false
    val current =
        computeIfAbsent(key) {
            inserted = true
            value
        }
    return if (inserted) null else current
}

/** `ConcurrentHashMap.newKeySet()`: a set safe to read and change from several threads, over Ktor's ConcurrentMap. */
fun <T : Any> concurrentSetOf(): MutableSet<T> = ConcurrentSet()

private class ConcurrentSet<T : Any> : AbstractMutableSet<T>() {
    private val map = ConcurrentMap<T, Boolean>()

    override val size: Int get() = map.size

    override fun add(element: T): Boolean = map.put(element, true) == null

    override fun remove(element: T): Boolean = map.remove(element) != null

    override fun contains(element: T): Boolean = map.containsKey(element)

    override fun clear() = map.clear()

    override fun iterator(): MutableIterator<T> = map.keys.iterator()
}
