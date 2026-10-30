package io.github.aedev.flow.testing

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The part of Google Truth upstream's tests use, on kotlin.test, so they port line for line to
 * common code: Truth is a JVM library. Overloads pick the subject by static type, as Truth's do.
 */
fun assertThat(actual: String?): StringSubject = StringSubject(actual, null)

fun assertThat(actual: Boolean?): BooleanSubject = BooleanSubject(actual, null)

fun assertThat(actual: Float?): FloatSubject = FloatSubject(actual, null)

fun <T> assertThat(actual: Iterable<T>?): IterableSubject<T> = IterableSubject(actual, null)

fun <K, V> assertThat(actual: Map<K, V>?): MapSubject<K, V> = MapSubject(actual, null)

fun <T : Comparable<T>> assertThat(actual: T?): ComparableSubject<T> = ComparableSubject(actual, null)

fun assertThat(actual: Any?): Subject<Any?> = Subject(actual, null)

fun assertWithMessage(message: String): MessageBuilder = MessageBuilder(message)

class MessageBuilder(
    private val message: String,
) {
    fun that(actual: String?): StringSubject = StringSubject(actual, message)

    fun that(actual: Boolean?): BooleanSubject = BooleanSubject(actual, message)

    fun <T> that(actual: Iterable<T>?): IterableSubject<T> = IterableSubject(actual, message)

    fun <T : Comparable<T>> that(actual: T?): ComparableSubject<T> = ComparableSubject(actual, message)

    fun that(actual: Any?): Subject<Any?> = Subject(actual, message)
}

open class Subject<T>(
    protected val actual: T?,
    protected val message: String?,
) {
    // Truth compares integral boxes by value, so a Long equals the Int literal 9.
    fun isEqualTo(expected: Any?) =
        if (expected.isIntegral() && actual.isIntegral()) {
            assertEquals((expected as Number).toLong(), (actual as Number).toLong(), message)
        } else {
            assertEquals(expected, actual, message)
        }

    private fun Any?.isIntegral() = this is Int || this is Long || this is Short || this is Byte

    fun isNotEqualTo(unexpected: Any?) = assertNotEquals(unexpected, actual, message)

    fun isNull() = assertNull(actual, message)

    fun isNotNull() {
        assertNotNull(actual, message)
    }

    fun isInstanceOf(type: kotlin.reflect.KClass<*>) = assertTrue(type.isInstance(actual), "${describe()} is not a ${type.simpleName}")

    protected fun describe(): String = (message?.let { "$it: " } ?: "") + "<$actual>"

    protected fun nonNull(): T = actual ?: fail("${describe()} is null")
}

class BooleanSubject(
    actual: Boolean?,
    message: String?,
) : Subject<Boolean>(actual, message) {
    fun isTrue() = assertEquals(true, actual, message)

    fun isFalse() = assertEquals(false, actual, message)
}

open class ComparableSubject<T : Comparable<T>>(
    actual: T?,
    message: String?,
) : Subject<T>(actual, message) {
    fun isGreaterThan(other: T) = assertTrue(nonNull() > other, "${describe()} is not greater than <$other>")

    fun isLessThan(other: T) = assertTrue(nonNull() < other, "${describe()} is not less than <$other>")

    fun isAtLeast(other: T) = assertTrue(nonNull() >= other, "${describe()} is not at least <$other>")

    fun isAtMost(other: T) = assertTrue(nonNull() <= other, "${describe()} is not at most <$other>")

    /** Truth's `isIn(Range.closed(a, b))`. */
    fun isIn(range: ClosedRange<T>) = assertTrue(nonNull() in range, "${describe()} is not in $range")
}

class FloatSubject(
    actual: Float?,
    message: String?,
) : ComparableSubject<Float>(actual, message) {
    fun isWithin(tolerance: Float): TolerantFloat = TolerantFloat(nonNull(), tolerance, describe())

    class TolerantFloat(
        private val actual: Float,
        private val tolerance: Float,
        private val description: String,
    ) {
        fun of(expected: Float) = assertTrue(abs(actual - expected) <= tolerance, "$description is not within $tolerance of $expected")
    }
}

class StringSubject(
    actual: String?,
    message: String?,
) : ComparableSubject<String>(actual, message) {
    fun contains(part: CharSequence) = assertTrue(part in nonNull(), "${describe()} does not contain <$part>")

    fun doesNotContain(part: CharSequence) = assertTrue(part !in nonNull(), "${describe()} contains <$part>")

    fun startsWith(prefix: String) = assertTrue(nonNull().startsWith(prefix), "${describe()} does not start with <$prefix>")

    fun endsWith(suffix: String) = assertTrue(nonNull().endsWith(suffix), "${describe()} does not end with <$suffix>")

    fun isEmpty() = assertTrue(nonNull().isEmpty(), "${describe()} is not empty")

    fun isNotEmpty() = assertTrue(nonNull().isNotEmpty(), "${describe()} is empty")

    fun matches(regex: String) = assertTrue(Regex(regex).matches(nonNull()), "${describe()} does not match $regex")
}

class MapSubject<K, V>(
    actual: Map<K, V>?,
    message: String?,
) : Subject<Map<K, V>>(actual, message) {
    fun isEmpty() = assertTrue(nonNull().isEmpty(), "${describe()} is not empty")

    fun isNotEmpty() = assertTrue(nonNull().isNotEmpty(), "${describe()} is empty")

    fun hasSize(size: Int) = assertEquals(size, nonNull().size, describe())

    fun containsKey(key: K) = assertTrue(key in nonNull(), "${describe()} has no key <$key>")

    fun doesNotContainKey(key: K) = assertTrue(key !in nonNull(), "${describe()} has key <$key>")

    fun containsEntry(
        key: K,
        value: V,
    ) = assertEquals(value, nonNull()[key], "${describe()} at <$key>")
}

class IterableSubject<T>(
    actual: Iterable<T>?,
    message: String?,
) : Subject<Iterable<T>>(actual, message) {
    private val list get() = nonNull().toList()

    fun isEmpty() = assertTrue(list.isEmpty(), "${describe()} is not empty")

    fun isNotEmpty() = assertTrue(list.isNotEmpty(), "${describe()} is empty")

    fun hasSize(size: Int) = assertEquals(size, list.size, describe())

    fun contains(element: Any?) = assertTrue(element in list, "${describe()} does not contain <$element>")

    fun doesNotContain(element: Any?) = assertTrue(element !in list, "${describe()} contains <$element>")

    fun containsNoDuplicates() = assertEquals(list.size, list.toSet().size, "${describe()} has duplicates")

    /** The same elements, as many times each, in any order unless [Ordered.inOrder] follows. */
    fun containsExactly(vararg expected: Any?): Ordered {
        val remaining = list.toMutableList<Any?>()
        val missing = expected.filterNot { remaining.remove(it) }
        assertTrue(missing.isEmpty() && remaining.isEmpty(), "${describe()} is not exactly ${expected.toList()} (missing $missing, unexpected $remaining)")
        return Ordered { assertEquals(expected.toList(), list, describe()) }
    }

    fun containsExactlyElementsIn(expected: Iterable<*>): Ordered = containsExactly(*expected.toList().toTypedArray())

    /** Every one of [expected], with others allowed; [Ordered.inOrder] then wants them in this order. */
    fun containsAtLeast(vararg expected: Any?): Ordered {
        val remaining = list.toMutableList<Any?>()
        val missing = expected.filterNot { remaining.remove(it) }
        assertTrue(missing.isEmpty(), "${describe()} is missing $missing")
        return Ordered {
            var from = 0
            for (element in expected) {
                val at = list.subList(from, list.size).indexOf(element)
                assertTrue(at >= 0, "${describe()} does not have ${expected.toList()} in order")
                from += at + 1
            }
        }
    }

    /** Each element at least the one before it. */
    fun isInOrder() = isInOrder(naturalOrder<Comparable<Any>>() as Comparator<in T>)

    fun isInOrder(comparator: Comparator<in T>) =
        assertTrue(list.zipWithNext().all { (a, b) -> comparator.compare(a, b) <= 0 }, "${describe()} is not in order")

    fun interface Ordered {
        fun inOrder()
    }
}
