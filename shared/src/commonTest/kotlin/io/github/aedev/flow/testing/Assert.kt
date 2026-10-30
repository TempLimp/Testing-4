package io.github.aedev.flow.testing

import kotlin.test.assertSame as kotlinAssertSame

// org.junit.Assert's calls, message first as JUnit has it, so upstream's tests keep their argument order.

fun assertEquals(
    expected: Any?,
    actual: Any?,
) = kotlin.test.assertEquals(expected, actual)

fun assertEquals(
    message: String,
    expected: Any?,
    actual: Any?,
) = kotlin.test.assertEquals(expected, actual, message)

fun assertEquals(
    expected: Float,
    actual: Float,
    delta: Float,
) = kotlin.test.assertEquals(expected, actual, delta)

fun assertEquals(
    expected: Double,
    actual: Double,
    delta: Double,
) = kotlin.test.assertEquals(expected, actual, delta)

fun assertTrue(condition: Boolean) = kotlin.test.assertTrue(condition)

fun assertTrue(
    message: String,
    condition: Boolean,
) = kotlin.test.assertTrue(condition, message)

fun assertFalse(condition: Boolean) = kotlin.test.assertFalse(condition)

fun assertFalse(
    message: String,
    condition: Boolean,
) = kotlin.test.assertFalse(condition, message)

fun assertNull(actual: Any?) = kotlin.test.assertNull(actual)

fun assertNull(
    message: String,
    actual: Any?,
) = kotlin.test.assertNull(actual, message)

fun assertNotNull(actual: Any?) {
    kotlin.test.assertNotNull(actual)
}

fun assertNotNull(
    message: String,
    actual: Any?,
) {
    kotlin.test.assertNotNull(actual, message)
}

fun assertSame(
    expected: Any?,
    actual: Any?,
) = kotlinAssertSame(expected, actual)
