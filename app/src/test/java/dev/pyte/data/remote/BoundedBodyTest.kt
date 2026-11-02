package dev.pyte.data.remote

import org.junit.Assert.*
import org.junit.Test

class BoundedBodyTest {
    @Test fun `body size limit rejects excessive content`() {
        assertArrayEquals(byteArrayOf(1, 2), byteArrayOf(1, 2).inputStream().readBounded(2))
        try { ByteArray(1024).inputStream().readBounded(128); fail("Expected bounded response") }
        catch (_: java.io.IOException) { }
    }
}
