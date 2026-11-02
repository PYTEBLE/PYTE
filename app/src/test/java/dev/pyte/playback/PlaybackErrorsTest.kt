package dev.pyte.playback

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackErrorsTest {
    @Test
    fun `nested HTTP status is visible without leaking signed URL`() {
        val error = IOException("Track failed", IOException("IOS: Audio-Server HTTP 403 https://secret.example/?sig=private"))
        val message = playbackErrorMessage("ERROR_CODE_IO_UNSPECIFIED", error)
        assertTrue(message.contains("HTTP 403"))
        assertFalse(message.contains("secret.example"))
        assertFalse(message.contains("private"))
    }

    @Test
    fun `Media3 response codes are recognized`() {
        assertTrue(playbackErrorMessage("BAD_HTTP_STATUS", IOException("Response code: 429")).contains("HTTP 429"))
    }

    @Test
    fun `non HTTP errors retain diagnostic error code`() {
        assertTrue(playbackErrorMessage("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", IOException("offline"))
            .contains("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"))
    }
}
