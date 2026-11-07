package io.github.aedev.flow.testing

import kotlin.random.Random

/**
 * `java.util.Random`, number for number: upstream's tests and benchmarks seed it, and their fixtures (the
 * benchmark universes, the shuffled histories) are whatever its sequence makes. A kotlin.random.Random, so the
 * code under test takes it where upstream takes the Java one; Kotlin's `shuffled(random)` and `random(random)`
 * ask it for the same bounds `Collections.shuffle` does. JavaRandomTest checks it against the JDK's.
 */
open class JavaRandom(
    seed: Long = Random.nextLong(),
) : Random() {
    private var state: Long = (seed xor MULTIPLIER) and MASK

    protected fun next(bits: Int): Int {
        state = (state * MULTIPLIER + ADDEND) and MASK
        return (state ushr (48 - bits)).toInt()
    }

    override fun nextBits(bitCount: Int): Int = next(bitCount)

    override fun nextInt(): Int = next(32)

    override fun nextInt(until: Int): Int {
        require(until > 0) { "bound must be positive" }
        var r = next(31)
        val m = until - 1
        if (until and m == 0) return ((until * r.toLong()) shr 31).toInt()
        var u = r
        while (true) {
            r = u % until
            if (u - r + m >= 0) return r
            u = next(31)
        }
    }

    override fun nextLong(): Long = (next(32).toLong() shl 32) + next(32)

    override fun nextBoolean(): Boolean = next(1) != 0

    override fun nextFloat(): Float = next(24) / (1 shl 24).toFloat()

    override fun nextDouble(): Double = ((next(26).toLong() shl 27) + next(27)) * DOUBLE_UNIT

    private companion object {
        const val MULTIPLIER = 0x5DEECE66DL
        const val ADDEND = 0xBL
        const val MASK = (1L shl 48) - 1
        const val DOUBLE_UNIT = 1.0 / (1L shl 53)
    }
}
