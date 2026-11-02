package dev.pyte.data.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryTest {
    @Test fun `settings have safe defaults persist and change independently`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = SettingsRepository(context)
        assertEquals(AppSettings(), repository.settings.first())
        try {
            repository.setSkipSilence(true)
            repository.setRecordHistory(false)
            val reopened = SettingsRepository(context)
            assertEquals(AppSettings(skipSilence = true, recordHistory = false), reopened.settings.first())
            repository.setSkipSilence(false)
            assertFalse(reopened.settings.first().recordHistory)
        } finally {
            repository.setSkipSilence(false)
            repository.setRecordHistory(true)
        }
    }
}
