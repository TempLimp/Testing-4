package io.github.aedev.flow.utils

import io.ktor.util.collections.ConcurrentMap

/**
 * `ConcurrentHashMap.putIfAbsent`: atomic through Ktor's `computeIfAbsent`. Returns the value already there, or null
 * when [value] went in. Not named `putIfAbsent`: on jvm Ktor's ConcurrentMap inherits java.util.Map's default
 * `putIfAbsent` (a get, then a put), which as a member would win over this extension, so two callers could both see
 * the key absent and one deferred end up in the map with no owner to complete it.
 */
fun <K : Any, V : Any> ConcurrentMap<K, V>.atomicPutIfAbsent(
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
