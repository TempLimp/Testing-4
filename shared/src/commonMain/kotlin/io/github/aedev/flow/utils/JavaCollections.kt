package io.github.aedev.flow.utils

// java.util.Map and Collection default methods that common Kotlin lacks, with Java's semantics, so upstream's calls
// port unchanged. On jvm the Java members win over these extensions; on iOS these run.

/** `Map.merge`: [value] when [key] is absent, else [remapping] of the old and new values; a null result removes the key. */
fun <K, V : Any> MutableMap<K, V>.merge(
    key: K,
    value: V,
    remapping: (V, V) -> V?,
): V? {
    val old = get(key)
    val new = if (old == null) value else remapping(old, value)
    if (new == null) remove(key) else put(key, new)
    return new
}

/** `Map.putIfAbsent`: puts [value] unless [key] has a non-null value, and returns the value that was there. */
fun <K, V> MutableMap<K, V>.putIfAbsent(
    key: K,
    value: V,
): V? = get(key) ?: run {
    put(key, value)
    null
}

/** `Map.getOrDefault`: [defaultValue] only when [key] is absent. */
@Suppress("UNCHECKED_CAST")
fun <K, V> Map<out K, V>.getOrDefault(
    key: K,
    defaultValue: V,
): V = get(key) ?: if (containsKey(key)) null as V else defaultValue

/** `Map.replaceAll`: every value replaced by [function] of its entry. */
fun <K, V> MutableMap<K, V>.replaceAll(function: (K, V) -> V) {
    for (entry in entries) entry.setValue(function(entry.key, entry.value))
}

/** `Collection.removeIf`. */
fun <T> MutableIterable<T>.removeIf(predicate: (T) -> Boolean): Boolean = removeAll(predicate)

/**
 * `java.util.LinkedHashMap(capacity, loadFactor, accessOrder)` with upstream's `removeEldestEntry` bound
 * (`size > maxSize`), which Kotlin/Native's final LinkedHashMap cannot be subclassed for. With [accessOrder] a
 * `get` or a `put` of a present key moves it to the end; adding a new key past [maxSize] drops the eldest.
 */
class JavaLinkedHashMap<K, V>(
    private val accessOrder: Boolean,
    private val maxSize: Int = Int.MAX_VALUE,
) : AbstractMutableMap<K, V>() {
    private val map = LinkedHashMap<K, V>()

    override val size: Int get() = map.size

    override fun containsKey(key: K): Boolean = map.containsKey(key)

    override fun get(key: K): V? {
        if (!accessOrder || !map.containsKey(key)) return map[key]
        @Suppress("UNCHECKED_CAST")
        val value = map.remove(key) as V
        map[key] = value
        return value
    }

    override fun put(
        key: K,
        value: V,
    ): V? {
        if (map.containsKey(key)) {
            if (!accessOrder) return map.put(key, value)
            val old = map.remove(key)
            map[key] = value
            return old
        }
        map[key] = value
        if (map.size > maxSize) map.remove(map.keys.first())
        return null
    }

    override fun remove(key: K): V? = map.remove(key)

    override fun clear() = map.clear()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = map.entries
}

/** `java.util.EnumMap`: iterates in the keys' declaration order, whatever order they were put in. */
class JavaEnumMap<K : Enum<K>, V> : AbstractMutableMap<K, V>() {
    private val map = LinkedHashMap<K, V>()

    override val size: Int get() = map.size

    override fun containsKey(key: K): Boolean = map.containsKey(key)

    override fun get(key: K): V? = map[key]

    override fun put(
        key: K,
        value: V,
    ): V? {
        if (map.containsKey(key)) return map.put(key, value)
        map[key] = value
        val ordered = map.entries.sortedBy { it.key.ordinal }.map { it.key to it.value }
        map.clear()
        ordered.forEach { (k, v) -> map[k] = v }
        return null
    }

    override fun remove(key: K): V? = map.remove(key)

    override fun clear() = map.clear()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = map.entries
}
