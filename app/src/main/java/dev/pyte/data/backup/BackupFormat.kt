package dev.pyte.data.backup

import androidx.annotation.StringRes
import dev.pyte.R
import dev.pyte.data.settings.AppSettings
import dev.pyte.data.settings.BatterySettings
import dev.pyte.data.settings.EffectLevel
import dev.pyte.playback.EqualizerSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val BACKUP_FORMAT = "pyte-backup"
internal const val BACKUP_VERSION = 1
internal const val BACKUP_MAX_BYTES = 20 * 1024 * 1024

class BackupException(@StringRes val messageRes: Int) : Exception()

data class BackupSummary(val playlists: Int, val favorites: Int, val history: Int)

@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val settings: BackupSettings,
    val tracks: List<BackupTrack> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val favorites: List<BackupFavorite> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
)

@Serializable
data class BackupSettings(
    val skipSilence: Boolean,
    val recordHistory: Boolean,
    val downloadLimitMb: Long,
    val dynamicCoverTheme: Boolean,
    val effectLevel: String,
    val hapticFeedback: Boolean,
    val autoRecommendations: Boolean,
    val equalizer: EqualizerSettings,
    val battery: BatterySettings,
)

@Serializable
data class BackupTrack(val videoId: String, val title: String, val artist: String, val durationMs: Long, val thumbnailUrl: String? = null)

@Serializable
data class BackupPlaylist(
    val id: String,
    val title: String,
    val owner: String? = null,
    val thumbnailUrl: String? = null,
    val sourceUrl: String,
    val importedAt: Long,
    val localTitle: String? = null,
    val tracks: List<BackupLink> = emptyList(),
    val excludedVideoIds: List<String> = emptyList(),
)

@Serializable
data class BackupLink(val videoId: String, val locallyAdded: Boolean = false)

@Serializable
data class BackupFavorite(val videoId: String, val addedAt: Long)

@Serializable
data class BackupHistory(val videoId: String, val lastPlayedAt: Long)

private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

internal fun encodeBackup(file: BackupFile): String = backupJson.encodeToString(file)

internal fun parseBackup(text: String): BackupFile {
    val file = try {
        backupJson.decodeFromString<BackupFile>(text)
    } catch (e: IllegalArgumentException) {
        throw BackupException(R.string.data_import_invalid)
    }
    if (file.format != BACKUP_FORMAT || file.version != BACKUP_VERSION) throw BackupException(R.string.data_import_invalid)
    val tracks = file.tracks.map { it.videoId }.toSet()
    val referenced = file.playlists.flatMap { playlist -> playlist.tracks.map { it.videoId } } +
        file.favorites.map { it.videoId } + file.history.map { it.videoId }
    val valid = file.playlists.all { playlist -> playlist.id.isNotBlank() &&
        playlist.excludedVideoIds.none { excluded -> excluded.isBlank() || playlist.tracks.any { link -> link.videoId == excluded } } } &&
        tracks.none { it.isBlank() } && tracks.containsAll(referenced)
    if (!valid) throw BackupException(R.string.data_import_invalid)
    return file
}

internal fun AppSettings.toBackup() = BackupSettings(
    skipSilence = skipSilence,
    recordHistory = recordHistory,
    downloadLimitMb = downloadLimitMb,
    dynamicCoverTheme = dynamicCoverTheme,
    effectLevel = effectLevel.name,
    hapticFeedback = hapticFeedback,
    autoRecommendations = autoRecommendations,
    equalizer = equalizer,
    battery = battery,
)

internal fun BackupSettings.toAppSettings() = AppSettings(
    skipSilence = skipSilence,
    recordHistory = recordHistory,
    downloadLimitMb = downloadLimitMb,
    dynamicCoverTheme = dynamicCoverTheme,
    equalizer = equalizer,
    effectLevel = EffectLevel.fromStored(effectLevel),
    hapticFeedback = hapticFeedback,
    autoRecommendations = autoRecommendations,
    battery = battery,
)
