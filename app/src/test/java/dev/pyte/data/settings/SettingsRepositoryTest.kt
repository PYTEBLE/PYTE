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
    @Test fun `effect intensity and haptics persist independently of cover colors`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = SettingsRepository(context)
        try {
            repository.setEffectLevel(EffectLevel.INTENSE)
            repository.setHapticFeedback(false)
            repository.setDynamicCoverTheme(false)
            val reopened = SettingsRepository(context).settings.first()
            assertEquals(EffectLevel.INTENSE, reopened.effectLevel)
            assertFalse(reopened.hapticFeedback)
            assertFalse(reopened.dynamicCoverTheme)
        } finally {
            repository.setEffectLevel(EffectLevel.LIVELY)
            repository.setHapticFeedback(true)
            repository.setDynamicCoverTheme(true)
        }
    }
    @Test fun `sound profiles and theme preferences persist without changing playback settings`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = SettingsRepository(context)
        try {
            repository.setDynamicCoverTheme(false)
            repository.enableEqualizer(true)
            repository.selectSoundProfile("bass")
            val points = repository.settings.first().equalizer.points
            repository.saveSoundProfile("My headphones", points)
            val reopened = SettingsRepository(context)
            val saved = reopened.settings.first()
            assertFalse(saved.dynamicCoverTheme)
            assertTrue(saved.equalizer.enabled)
            assertEquals(points, saved.equalizer.profiles.single().points)
            repository.saveSoundProfile("MY HEADPHONES", dev.pyte.playback.EqualizerCurves.flat)
            assertEquals(1, reopened.settings.first().equalizer.profiles.size)
            repository.selectSoundProfile("neutral")
            assertEquals(dev.pyte.playback.EqualizerCurves.flat, reopened.settings.first().equalizer.points)
            repository.deleteSoundProfile(saved.equalizer.profiles.single().id)
            assertTrue(reopened.settings.first().equalizer.profiles.isEmpty())
        } finally {
            repository.settings.first().equalizer.profiles.forEach { repository.deleteSoundProfile(it.id) }
            repository.enableEqualizer(false)
            repository.selectSoundProfile("neutral")
            repository.setDynamicCoverTheme(true)
        }
    }
    @Test fun `download settings persist limits are bounded and playback preferences stay independent`() = runBlocking {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        try {
            repository.pauseDownloads(true)
            repository.setDownloadLimit(1)
            val reopened = SettingsRepository(RuntimeEnvironment.getApplication())
            assertTrue(reopened.settings.first().downloadsPaused)
            assertEquals(512L, reopened.settings.first().downloadLimitMb)
            repository.setDownloadLimit(100_000)
            assertEquals(16384L, reopened.settings.first().downloadLimitMb)
            assertFalse(reopened.settings.first().skipSilence)
            assertTrue(reopened.settings.first().recordHistory)
        } finally {
            repository.pauseDownloads(false)
            repository.setDownloadLimit(2048)
        }
    }
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
