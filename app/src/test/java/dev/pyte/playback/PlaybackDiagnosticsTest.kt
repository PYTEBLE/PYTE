package dev.pyte.playback

import dev.pyte.data.remote.StreamAttemptFailure
import dev.pyte.data.remote.StreamUnavailableException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackDiagnosticsTest {
    @Test
    fun `report retains every client attempt through wrapped causes`() {
        val source = StreamUnavailableException("private URL https://secret.example/?sig=hidden", attempts = listOf(
            StreamAttemptFailure("ANDROID_VR", "audio-probe", "HTTP 403"),
            StreamAttemptFailure("IOS", "audio-format", "LOGIN_REQUIRED"),
        ))
        val report = buildPlaybackReport("test", "IO_UNSPECIFIED", IOException(source))
        assertTrue(report.contains("ANDROID_VR / audio-probe / HTTP 403"))
        assertTrue(report.contains("IOS / audio-format / LOGIN_REQUIRED"))
        assertFalse(report.contains("secret.example"))
        assertFalse(report.contains("hidden"))
    }

    @Test
    fun `unexpected failures expose only type names not exception messages`() {
        val report = buildPlaybackReport("test", "IO_UNSPECIFIED", IOException("private-address"))
        assertTrue(report.contains("IOException"))
        assertFalse(report.contains("private-address"))
    }
}
