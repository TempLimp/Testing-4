package io.github.aedev.flow.utils

import io.github.aedev.flow.testing.JavaRandom
import io.github.aedev.flow.testing.assertEquals
import java.util.zip.GZIPInputStream
import kotlin.test.Test

/** The port's stand-ins for JVM pieces, checked against the JDK and ICU4J they stand in for. */
class JavaPartsJvmTest {
    @Test
    fun javaRandomGivesJavaUtilRandomsNumbers() {
        listOf(0L, 7L, 42L, 907L, -1L, Long.MAX_VALUE).forEach { seed ->
            val jdk = java.util.Random(seed)
            val port = JavaRandom(seed)
            repeat(2_000) { i ->
                when (i % 7) {
                    0 -> assertEquals("nextInt", jdk.nextInt(), port.nextInt())
                    1 -> assertEquals("nextInt(bound)", jdk.nextInt(1 + i), port.nextInt(1 + i))
                    2 -> assertEquals("nextInt(2^k)", jdk.nextInt(1 shl (i % 30)), port.nextInt(1 shl (i % 30)))
                    3 -> assertEquals("nextLong", jdk.nextLong(), port.nextLong())
                    4 -> assertEquals("nextDouble", jdk.nextDouble(), port.nextDouble())
                    5 -> assertEquals("nextFloat", jdk.nextFloat(), port.nextFloat())
                    else -> assertEquals("nextBoolean", jdk.nextBoolean(), port.nextBoolean())
                }
            }
            val list = (0 until 50).toList()
            assertEquals("shuffle", list.shuffled(java.util.Random(seed)), list.shuffled(JavaRandom(seed)))
        }
    }

    @Test
    fun scriptsMatchTheJdkOnEveryBmpCharacter() {
        val names = UnicodeScript.entries.filter { it != UnicodeScript.OTHER }.associateBy { it.name }
        var compared = 0
        for (code in 0..0xFFFF) {
            val jdk = Character.UnicodeScript.of(code)
            // ICU4J 77 knows a few characters JDK 21's Unicode 15 does not yet; those are no script to compare.
            if (jdk == Character.UnicodeScript.UNKNOWN) continue
            assertEquals("U+%04X".format(code), names[jdk.name] ?: UnicodeScript.OTHER, UnicodeScript.of(code))
            compared++
        }
        println("scripts compared on $compared BMP characters")
    }

    @Test
    fun gzipRoundTripsThroughTheJdk() {
        val text = "The recap of a month, ".repeat(200)
        val back = GZIPInputStream(gzip(text.encodeToByteArray()).inputStream()).readBytes().decodeToString()
        assertEquals(text, back)
    }
}
