package dev.pyte.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pyte.R
import dev.pyte.data.local.FavoriteEntity
import dev.pyte.data.local.HistoryEntity
import dev.pyte.data.local.PlaylistEntity
import dev.pyte.data.local.PlaylistTrackEntity
import dev.pyte.data.local.PyteDatabase
import dev.pyte.data.local.TrackEntity
import dev.pyte.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PyteDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun export(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val file = db.withTransaction {
            val playlists = db.playlistDao()
            BackupFile(
                format = BACKUP_FORMAT,
                version = BACKUP_VERSION,
                exportedAt = System.currentTimeMillis(),
                settings = settings.settings.first().toBackup(),
                tracks = db.trackDao().getAllTracks().map { BackupTrack(it.videoId, it.title, it.artist, it.durationMs, it.thumbnailUrl) },
                playlists = playlists.getPlaylists().map { playlist ->
                    BackupPlaylist(
                        id = playlist.id,
                        title = playlist.title,
                        owner = playlist.owner,
                        thumbnailUrl = playlist.thumbnailUrl,
                        sourceUrl = playlist.sourceUrl,
                        importedAt = playlist.importedAt,
                        localTitle = playlist.localTitle,
                        tracks = playlists.getPlaylistLinks(playlist.id).map { BackupLink(it.videoId, it.locallyAdded) },
                    )
                },
                favorites = db.favoriteDao().getFavorites().map { BackupFavorite(it.videoId, it.addedAt) },
                history = db.trackDao().getHistory().map { BackupHistory(it.videoId, it.lastPlayedAt) },
            )
        }
        val text = encodeBackup(file)
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
            ?: throw BackupException(R.string.data_export_error)
        BackupSummary(file.playlists.size, file.favorites.size, file.history.size)
    }

    /** Merges playlists, favorites and history from the backup; settings are replaced. Invalid files change nothing. */
    suspend fun import(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { String(it.readLimited(BACKUP_MAX_BYTES), Charsets.UTF_8) }
            ?: throw BackupException(R.string.data_import_error)
        val file = parseBackup(text)
        db.withTransaction { restore(file) }
        settings.restore(file.settings.toAppSettings())
        BackupSummary(file.playlists.size, file.favorites.size, file.history.size)
    }

    private suspend fun restore(file: BackupFile) {
        val tracks = db.trackDao()
        val playlists = db.playlistDao()
        tracks.upsertTracks(file.tracks.map { TrackEntity(it.videoId, it.title, it.artist, it.durationMs, it.thumbnailUrl) })
        file.playlists.forEach { playlist ->
            playlists.upsertPlaylist(PlaylistEntity(playlist.id, playlist.title, playlist.owner, playlist.thumbnailUrl,
                playlist.sourceUrl, playlist.tracks.size, playlist.importedAt, playlist.localTitle))
            playlists.clearPlaylistTracks(playlist.id)
            playlists.insertPlaylistTracks(playlist.tracks.mapIndexed { index, link ->
                PlaylistTrackEntity(playlist.id, index, link.videoId, link.locallyAdded)
            })
        }
        file.favorites.forEach { db.favoriteDao().add(FavoriteEntity(it.videoId, it.addedAt)) }
        file.history.forEach { tracks.recordHistory(HistoryEntity(it.videoId, it.lastPlayedAt)) }
    }

    private fun InputStream.readLimited(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > max) throw BackupException(R.string.data_import_invalid)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
