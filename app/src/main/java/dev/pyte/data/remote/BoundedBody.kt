package dev.pyte.data.remote

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw IOException("Response too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
