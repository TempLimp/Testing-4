package io.github.aedev.flow.utils.potoken

import io.github.aedev.flow.testing.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test

class JavaScriptUtilTest {
    @Test
    fun integrityResponseRequiresUsableTokenAndLifetime() {
        val (token, lifetime) = parseIntegrityTokenData("[\"AQI=\",43200]")

        assertEquals("new Uint8Array([1,2])", token)
        assertEquals(43200L, lifetime)
    }

    @Test
    fun degradedIntegrityResponseIsRejected() {
        assertFailsWith<PoTokenException> {
            parseIntegrityTokenData("[\"\",0]")
        }
    }
}
