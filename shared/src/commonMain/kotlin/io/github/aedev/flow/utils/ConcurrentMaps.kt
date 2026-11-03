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
