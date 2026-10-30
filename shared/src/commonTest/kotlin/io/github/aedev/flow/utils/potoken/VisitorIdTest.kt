package io.github.aedev.flow.utils.potoken

import io.github.aedev.flow.testing.assertEquals
import io.github.aedev.flow.testing.assertNull
import kotlin.test.Test

class VisitorIdTest {
    /** A real visitorData captured from youtube.com; its visitor ID is `rlqDISqy6-0`. */
    private val realVisitorData =
        "CgtybHFESVNxeTYtMCjA9pLUBjIKCgJMQhIEGgAgP2LfAgrcAjIxLllUPWhpRGRwQmZwWFRUNU9WRkJT"

    @Test
    fun extractsTheVisitorIdFromRealVisitorData() {
        assertEquals("rlqDISqy6-0", VisitorId.extract(realVisitorData))
    }

    @Test
    fun urlEncodedPaddingDoesNotDefeatExtraction() {
        assertEquals("rlqDISqy6-0", VisitorId.extract("$realVisitorData%3D%3D"))
    }

    @Test
    fun gvsBindingPrefersTheVisitorIdOverTheBlob() {
        val binding = VisitorId.gvsBinding(realVisitorData)
        assertEquals("rlqDISqy6-0", binding)
        // The whole point: GVS binds to the short id, not the several-hundred-character blob.
        assertEquals(11, binding.length)
    }

    @Test
    fun gvsBindingFallsBackToTheRawValueWhenNoIdCanBeRead() {
        val notProtobuf = "not-base64-at-all!!"
        assertEquals(notProtobuf, VisitorId.gvsBinding(notProtobuf))
    }

    @Test
    fun blankAndMalformedInputYieldNull() {
        assertNull(VisitorId.extract(null))
        assertNull(VisitorId.extract(""))
        assertNull(VisitorId.extract("   "))
        // Valid base64, but too short to contain an id.
        assertNull(VisitorId.extract("CgQ="))
    }
}
