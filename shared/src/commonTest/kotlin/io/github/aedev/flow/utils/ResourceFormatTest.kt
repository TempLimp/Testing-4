package io.github.aedev.flow.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Expected values are what java.util.Formatter (Android's getString) prints with Locale.ROOT. */
class ResourceFormatTest {
    private fun format(
        format: String,
        vararg args: Any?,
    ) = formatResourceString(format, args)

    @Test
    fun stringsAndIntegers() {
        assertEquals("Subscribed to Flow", format("Subscribed to %s", "Flow"))
        assertEquals("b a", format("%2\$s %1\$s", "a", "b"))
        assertEquals("1 and 2", format("%d and %d", 1, 2L))
        // An implicit index counts only implicit specifiers, as in java.util.Formatter.
        assertEquals("3 then 3 then x", format("%1\$d then %s then %s", 3, "x"))
        assertEquals("1.0 null", format("%s %s", 1.0, null))
        assertEquals("-5% • 7", format("%1\$d%% • %2\$s", -5, 7))
    }

    @Test
    fun decimalsRoundHalfUpOnTheShortestDecimal() {
        assertEquals("Preamp: 1.3dB", format("Preamp: %1\$.1fdB", 1.25))
        assertEquals("0.2", format("%.1f", 0.15))
        assertEquals("1.01", format("%.2f", 1.005))
        assertEquals("10.0", format("%.1f", 9.95))
        assertEquals("100", format("%.0f", 99.5))
        assertEquals("2.00×", format("%1\$.2f×", 2f))
        assertEquals("0.1", format("%.1f", 0.1f))
        assertEquals("1.500000", format("%f", 1.5))
        assertEquals("0.00", format("%.2f", 1.0E-7))
        assertEquals("10000000000.0", format("%.1f", 1.0E10))
        assertEquals("-0.0", format("%.1f", -0.04))
        assertEquals("0.0", format("%.1f", 0.0))
    }

    @Test
    fun malformedSpecifiersThrowAsOnAndroid() {
        assertFailsWith<IllegalArgumentException> { format("50% played", 1) }
        assertFailsWith<IllegalArgumentException> { format("%1\$s and %2\$s", "only one") }
        assertFailsWith<IllegalArgumentException> { format("%d", "x") }
        assertFailsWith<IllegalArgumentException> { format("%.1f", 1) }
    }
}
