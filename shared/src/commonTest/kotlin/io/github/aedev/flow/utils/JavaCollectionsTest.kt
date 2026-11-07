package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import io.github.aedev.flow.testing.assertThat
import kotlin.test.Test
import io.github.aedev.flow.utils.getOrDefault as javaGetOrDefault
import io.github.aedev.flow.utils.merge as javaMerge
import io.github.aedev.flow.utils.putIfAbsent as javaPutIfAbsent
import io.github.aedev.flow.utils.removeIf as javaRemoveIf
import io.github.aedev.flow.utils.replaceAll as javaReplaceAll

/**
 * The java.util defaults the port supplies for iOS. Aliased, so the jvm run tests these extensions too rather
 * than the Java members that shadow them there; the expectations are java.util's documented results.
 */
class JavaCollectionsTest {
    @Test
    fun mergeAddsCombinesAndRemovesOnNull() {
        val map = hashMapOf("a" to 1)
        assertEquals(2, map.javaMerge("b", 2, Int::plus))
        assertEquals(4, map.javaMerge("a", 3, Int::plus))
        assertNull(map.javaMerge("a", 9) { _, _ -> null })
        assertThat(map).containsExactly("b", 2)
    }

    @Test
    fun putIfAbsentKeepsAPresentValue() {
        val map = hashMapOf<String, String?>("a" to "1", "n" to null)
        assertEquals("1", map.javaPutIfAbsent("a", "2"))
        assertNull(map.javaPutIfAbsent("b", "3"))
        assertNull(map.javaPutIfAbsent("n", "4"))
        assertThat(map).containsExactly("a", "1", "n", "4", "b", "3")
    }

    @Test
    fun getOrDefaultOnlyForAnAbsentKey() {
        val map = mapOf<String, Int?>("a" to 1, "n" to null)
        assertEquals(1, map.javaGetOrDefault("a", 5))
        assertNull(map.javaGetOrDefault("n", 5))
        assertEquals(5, map.javaGetOrDefault("z", 5))
    }

    @Test
    fun replaceAllAndRemoveIf() {
        val map = linkedMapOf("a" to 1, "b" to 2, "c" to 3)
        map.javaReplaceAll { key, value -> if (key == "b") value * 10 else value }
        assertThat(map).containsExactly("a", 1, "b", 20, "c", 3)
        assertEquals(true, map.entries.javaRemoveIf { it.value > 2 })
        assertThat(map).containsExactly("a", 1)
    }

    @Test
    fun accessOrderMovesReadAndRewrittenKeysToTheEnd() {
        val map = JavaLinkedHashMap<String, Int>(accessOrder = true)
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3
        map["a"]
        map["b"] = 20
        assertEquals(true, "c" in map)
        assertEquals(listOf("c", "a", "b"), map.keys.toList())
        assertEquals(listOf(3, 1, 20), map.values.toList())
    }

    @Test
    fun aBoundDropsTheEldestOnlyWhenANewKeyArrives() {
        val lru = JavaLinkedHashMap<String, Int>(accessOrder = true, maxSize = 2)
        lru["a"] = 1
        lru["b"] = 2
        lru["a"]
        lru["c"] = 3
        assertEquals(listOf("a", "c"), lru.keys.toList())
        lru["a"] = 10
        assertEquals(listOf("c", "a"), lru.keys.toList())

        val fifo = JavaLinkedHashMap<String, Int>(accessOrder = false, maxSize = 2)
        fifo["a"] = 1
        fifo["b"] = 2
        fifo["a"]
        fifo["a"] = 10
        fifo["c"] = 3
        assertEquals(listOf("b", "c"), fifo.keys.toList())
    }

    private enum class Day { MON, TUE, WED }

    @Test
    fun anEnumMapIteratesInDeclarationOrder() {
        val map = JavaEnumMap<Day, String>()
        map[Day.WED] = "w"
        map[Day.MON] = "m"
        map[Day.WED] = "w2"
        map[Day.TUE] = "t"
        assertEquals(listOf(Day.MON, Day.TUE, Day.WED), map.keys.toList())
        assertEquals(listOf("m", "t", "w2"), map.values.toList())
    }

    @Test
    fun aConcurrentSetAddsOnce() {
        val set = concurrentSetOf<String>()
        assertEquals(true, set.add("a"))
        assertEquals(false, set.add("a"))
        set += listOf("b", "c")
        set.remove("a")
        assertEquals(setOf("b", "c"), set.toSet())
    }
}
