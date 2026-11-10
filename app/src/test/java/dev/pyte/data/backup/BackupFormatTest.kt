package dev.pyte.data.backup

import dev.pyte.R
import dev.pyte.data.settings.BatterySettings
import dev.pyte.data.settings.EffectLevel
import dev.pyte.playback.EqualizerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatTest {
    private val settings = BackupSettings(
        skipSilence = false, recordHistory = true, downloadLimitMb = 2048, dynamicCoverTheme = true,
        effectLevel = "INTENSE", hapticFeedback = true, autoRecommendations = false,
        equalizer = EqualizerSettings(), battery = BatterySettings(),
    )

    private fun file(
        tracks: List<BackupTrack> = listOf(BackupTrack("abc", "Song", "Artist", 1000)),
        playlists: List<BackupPlaylist> = emptyList(),
    ) = BackupFile(BACKUP_FORMAT, BACKUP_VERSION, 1L, settings, tracks, playlists)

    @Test fun roundTripKeepsPlaylistOrderAndLocalChanges() {
        val source = file(playlists = listOf(
            BackupPlaylist("local:1", "Mix", sourceUrl = "", importedAt = 5, localTitle = "Abend",
                tracks = listOf(BackupLink("abc", locallyAdded = true))),
        ))
        assertEquals(source, parseBackup(encodeBackup(source)))
    }

    @Test fun rejectsForeignFilesAndOtherVersions() {
        assertInvalid { parseBackup(encodeBackup(file().copy(format = "other"))) }
        assertInvalid { parseBackup(encodeBackup(file().copy(version = 2))) }
        assertInvalid { parseBackup("not json") }
    }

    @Test fun rejectsReferencesToMissingTracks() {
        val playlist = BackupPlaylist("p", "Liste", sourceUrl = "", importedAt = 1, tracks = listOf(BackupLink("missing")))
        assertInvalid { parseBackup(encodeBackup(file(tracks = emptyList(), playlists = listOf(playlist)))) }
    }

    @Test fun settingsMapToAppSettingsWithoutTouchingPausedDownloads() {
        val app = settings.toAppSettings()
        assertEquals(EffectLevel.INTENSE, app.effectLevel)
        assertEquals(false, app.downloadsPaused)
        assertEquals(settings, app.toBackup())
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected BackupException")
        } catch (e: BackupException) {
            assertEquals(R.string.data_import_invalid, e.messageRes)
        }
    }
}
