package dev.pyte.data.local

import android.database.sqlite.SQLiteDatabase
import dev.pyte.data.remote.InnerTubeClient
import dev.pyte.data.remote.ImportedPlaylist
import dev.pyte.data.remote.PlaylistImporter
import dev.pyte.data.repository.LibraryRepository
import dev.pyte.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LibraryDatabaseTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val song = Track("YGr2G2fc48U", "schönhauser", "Gustav", 155_000, null)
    private fun repo(db: PyteDatabase) = LibraryRepository(db,
        PlaylistImporter(InnerTubeClient(OkHttpClient(), Json)), dev.pyte.data.settings.SettingsRepository(context))
    private fun playlist(vararg songs: Track, title: String = "Source title") =
        ImportedPlaylist("PLtest", title, "Owner", null, songs.toList())

    private fun databaseTest(block: suspend (PyteDatabase) -> Unit) = runBlocking {
        withContext(Dispatchers.IO) {
            val name = "test-${UUID.randomUUID()}.db"
            val db = PyteDatabase.open(context, name)
            try { block(db) } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun `undo removes only the new local addition and preserves source duplicates favorites and history`() = databaseTest { db ->
        val library = repo(db)
        val extra = song.copy(videoId = "dQw4w9WgXcQ", title = "Extra")
        library.saveImported(playlist(song, song))
        library.setFavorite(extra, true)
        library.recordPlayed(extra)
        assertTrue(library.addToPlaylist("PLtest", extra))
        assertFalse(library.addToPlaylist("PLtest", song))
        assertFalse(library.undoPlaylistAddition("PLtest", song.videoId))
        assertTrue(library.undoPlaylistAddition("PLtest", extra.videoId))
        assertFalse(library.undoPlaylistAddition("PLtest", extra.videoId))
        val detail = library.observePlaylist("PLtest").first()!!
        assertEquals(listOf(song.videoId, song.videoId), detail.second.map { it.videoId })
        assertEquals(2, detail.first.trackCount)
        assertEquals(listOf(0, 1), db.playlistDao().getPlaylistLinks("PLtest").map { it.position })
        assertEquals(extra.videoId, library.observeFavorites().first().single().videoId)
        assertEquals(extra.videoId, library.observeHistory().first().single().videoId)
    }

    @Test fun `undo on a newly created or deleted playlist never deletes or resurrects the playlist`() = databaseTest { db ->
        val library = repo(db)
        val id = library.createPlaylist("Neue Sammlung", song)
        assertTrue(library.undoPlaylistAddition(id, song.videoId))
        val empty = library.observePlaylist(id).first()!!
        assertEquals(0, empty.first.trackCount)
        assertTrue(empty.second.isEmpty())
        library.deletePlaylist(id)
        assertFalse(library.undoPlaylistAddition(id, song.videoId))
        assertNull(library.observePlaylist(id).first())
    }

    @Test
    fun `refresh preserves renamed title favorites import date and duplicate positions`() = databaseTest { db ->
        val library = repo(db)
        library.saveImported(playlist(song))
        val importedAt = db.playlistDao().getPlaylist("PLtest")!!.importedAt
        library.renamePlaylist("PLtest", " My name ")
        library.setFavorite(song, true)
        library.saveImported(playlist(song, song, title = "Changed upstream"))
        val detail = library.observePlaylist("PLtest").first()!!
        assertEquals("My name", detail.first.title)
        assertEquals(importedAt, detail.first.importedAt)
        assertEquals(2, detail.second.size)
        assertTrue(song.videoId in library.observeFavoriteIds().first())
    }

    @Test
    fun `search uses safe token prefixes handles accents and updates index`() = databaseTest { db ->
        val library = repo(db)
        library.saveImported(playlist(song))
        assertEquals(song.videoId, library.search("schon gust").first().single().videoId)
        assertTrue(library.search("\"* :()").first().isEmpty())
        library.saveImported(playlist(song.copy(title = "New title")))
        assertTrue(library.search("schönhauser").first().isEmpty())
        assertEquals(song.videoId, library.search("new").first().single().videoId)
    }

    @Test
    fun `deleting playlist retains favorites and history but hides orphaned tracks from search`() = databaseTest { db ->
        val library = repo(db)
        val orphan = song.copy(videoId = "dQw4w9WgXcQ", title = "Orphan")
        library.saveImported(playlist(song, orphan))
        library.setFavorite(song, true)
        library.recordPlayed(song)
        library.deletePlaylist("PLtest")
        assertEquals(song.videoId, library.observeFavorites().first().single().videoId)
        assertEquals(song.videoId, library.observeHistory().first().single().videoId)
        assertTrue(library.search("orphan").first().isEmpty())
    }

    @Test
    fun `disabled history does not record and clearing preserves library and favorites`() = databaseTest { db ->
        val library = repo(db)
        library.setHistoryEnabled(true)
        try {
            library.saveImported(playlist(song))
            library.setFavorite(song, true)
            library.recordPlayed(song)
            assertEquals(1, library.observeHistory().first().size)
            library.setHistoryEnabled(false)
            library.recordPlayed(song.copy(videoId = "newSongId01"))
            assertEquals(1, library.observeHistory().first().size)
            library.clearHistory()
            assertTrue(library.observeHistory().first().isEmpty())
            assertEquals(song.videoId, library.observeFavorites().first().single().videoId)
            assertEquals(song.videoId, library.observePlaylist("PLtest").first()!!.second.single().videoId)
            library.setHistoryEnabled(true)
            library.recordPlayed(song)
            assertEquals(1, library.observeHistory().first().size)
        } finally {
            library.setHistoryEnabled(true)
        }
    }

    @Test
    fun `local additions are ordered deduplicated and retained through repeated refreshes`() = databaseTest { db ->
        val library = repo(db)
        val extra = song.copy(videoId = "dQw4w9WgXcQ", title = "Local addition")
        library.saveImported(playlist(song))
        assertTrue(library.addToPlaylist("PLtest", extra))
        assertFalse(library.addToPlaylist("PLtest", extra))
        library.renamePlaylist("PLtest", "My mix")
        library.saveImported(playlist(song, extra))
        library.saveImported(playlist(song))
        val detail = library.observePlaylist("PLtest").first()!!
        assertEquals(listOf(song.videoId, extra.videoId), detail.second.map { it.videoId })
        assertEquals(2, detail.first.trackCount)
        assertEquals("My mix", detail.first.title)
        assertEquals(extra.videoId, library.search("addition").first().single().videoId)
        assertTrue(db.playlistDao().getPlaylistLinks("PLtest").last().locallyAdded)
    }

    @Test
    fun `new playlists are local and adding to deleted playlists does not resurrect them`() = databaseTest { db ->
        val library = repo(db)
        val id = library.createPlaylist(" New mix ", song)
        val detail = library.observePlaylist(id).first()!!
        assertTrue(detail.first.isLocal)
        assertEquals("New mix", detail.first.title)
        assertEquals("", detail.first.sourceUrl)
        assertEquals(1, detail.first.trackCount)
        try { library.refreshPlaylist(id); fail("Local playlists must never refresh from YouTube") }
        catch (_: IllegalArgumentException) { }
        library.deletePlaylist(id)
        try { library.addToPlaylist(id, song); fail("Deleted playlist must stay deleted") }
        catch (_: IllegalStateException) { }
        assertNull(library.observePlaylist(id).first())
    }

    @Test
    fun `empty refresh leaves saved playlist intact`() = databaseTest { db ->
        val library = repo(db)
        library.saveImported(playlist(song))
        try { library.saveImported(playlist()); fail("Expected rejection") } catch (_: Exception) { }
        assertEquals(song.videoId, library.observePlaylist("PLtest").first()!!.second.single().videoId)
    }

    @Test
    fun `version one data migrates without losing playlists favorites or order`() = migrationTest(1)

    @Test
    fun `version two data migrates without losing playlists favorites or order`() = migrationTest(2)

    @Test
    fun `version three data migrates without losing local additions`() = migrationTest(3)

    @Test fun `version four migrates without losing data`() = migrationTest(4)

    @Test fun `removal survives refresh preserves other playlists favorites history and permits readding`() = databaseTest { db ->
        val library = repo(db)
        val other = song.copy(videoId = "dQw4w9WgXcQ")
        library.saveImported(playlist(song, song, other))
        val local = library.createPlaylist("Other", song)
        library.setFavorite(song, true)
        library.recordPlayed(song)
        db.offlineDao().select(OfflinePlaylistEntity("PLtest"))
        db.offlineDao().select(OfflinePlaylistEntity(local))
        assertTrue(library.removeFromPlaylist("PLtest", song.videoId))
        assertFalse(library.removeFromPlaylist("PLtest", song.videoId))
        library.saveImported(playlist(song, other, song))
        library.saveImported(playlist(song, other))
        val detail = library.observePlaylist("PLtest").first()!!
        assertEquals(listOf(other.videoId), detail.second.map { it.videoId })
        assertEquals(1, detail.first.trackCount)
        assertEquals(listOf(0), db.playlistDao().getPlaylistLinks("PLtest").map { it.position })
        assertTrue(db.offlineDao().isWanted(song.videoId))
        assertEquals(song.videoId, library.observeFavorites().first().single().videoId)
        assertEquals(song.videoId, library.observeHistory().first().single().videoId)
        assertTrue(library.addToPlaylist("PLtest", song))
        assertTrue(db.playlistDao().getExclusions("PLtest").isEmpty())
        library.saveImported(playlist(song, other))
        assertEquals(listOf(other.videoId, song.videoId), library.observePlaylist("PLtest").first()!!.second.map { it.videoId })
    }

    @Test fun `removing last songs keeps empty playlists and deletion cleans exclusions`() = databaseTest { db ->
        val library = repo(db)
        library.saveImported(playlist(song))
        assertTrue(library.removeFromPlaylist("PLtest", song.videoId))
        library.saveImported(playlist(song))
        assertEquals(0, library.observePlaylist("PLtest").first()!!.first.trackCount)
        assertTrue(library.observePlaylist("PLtest").first()!!.second.isEmpty())
        library.deletePlaylist("PLtest")
        assertTrue(db.playlistDao().getExclusions("PLtest").isEmpty())
        assertFalse(library.removeFromPlaylist("PLtest", song.videoId))
        val local = library.createPlaylist("Local", song)
        assertTrue(library.removeFromPlaylist(local, song.videoId))
        assertTrue(library.observePlaylist(local).first()!!.second.isEmpty())
        assertTrue(db.playlistDao().getExclusions(local).isEmpty())
    }

    @Test fun `exclusions survive database reopening and backup restoration`() = runBlocking {
        withContext(Dispatchers.IO) {
            val name = "exclusions-${UUID.randomUUID()}.db"
            val restoredName = "restore-${UUID.randomUUID()}.db"
            val backup = java.io.File(context.cacheDir, "${UUID.randomUUID()}.json")
            val uri = android.net.Uri.fromFile(backup)
            val settings = dev.pyte.data.settings.SettingsRepository(context)
            val db = PyteDatabase.open(context, name)
            try {
                repo(db).saveImported(playlist(song))
                repo(db).removeFromPlaylist("PLtest", song.videoId)
                dev.pyte.data.backup.BackupRepository(context, db, settings).export(uri)
            } finally { db.close() }
            try {
                PyteDatabase.open(context, name).let { reopened ->
                    try {
                        repo(reopened).saveImported(playlist(song))
                        assertTrue(repo(reopened).observePlaylist("PLtest").first()!!.second.isEmpty())
                    } finally { reopened.close() }
                }
                PyteDatabase.open(context, restoredName).let { restored ->
                    try {
                        dev.pyte.data.backup.BackupRepository(context, restored, settings).import(uri)
                        repo(restored).saveImported(playlist(song))
                        assertTrue(repo(restored).observePlaylist("PLtest").first()!!.second.isEmpty())
                    } finally { restored.close() }
                }
            } finally {
                context.deleteDatabase(name)
                context.deleteDatabase(restoredName)
                backup.delete()
            }
        }
    }

    @Test
    fun `offline selection excludes history favorites and unselected playlists and deduplicates shared songs`() = databaseTest { db ->
        val library = repo(db)
        library.saveImported(playlist(song, song))
        val other = library.createPlaylist("Other", song)
        val historyOnly = song.copy(videoId = "dQw4w9WgXcQ")
        library.recordPlayed(historyOnly)
        library.setFavorite(historyOnly, true)
        val dao = db.offlineDao()
        assertTrue(dao.wantedTracks().isEmpty())
        dao.select(OfflinePlaylistEntity("PLtest"))
        dao.select(OfflinePlaylistEntity(other))
        assertEquals(listOf(song.videoId), dao.wantedTracks().map { it.videoId })
        assertFalse(dao.isWanted(historyOnly.videoId))
        library.deletePlaylist("PLtest")
        assertTrue(dao.isWanted(song.videoId))
        dao.deselect(other)
        assertTrue(dao.wantedTracks().isEmpty())
    }

    private fun migrationTest(version: Int) = runBlocking {
        withContext(Dispatchers.IO) {
            val name = "migration-${UUID.randomUUID()}.db"
            val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL("CREATE TABLE playlists(id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, owner TEXT, thumbnailUrl TEXT, sourceUrl TEXT NOT NULL, trackCount INTEGER NOT NULL, importedAt INTEGER NOT NULL)")
                old.execSQL("CREATE TABLE tracks(videoId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, durationMs INTEGER NOT NULL, thumbnailUrl TEXT)")
                old.execSQL("CREATE TABLE playlist_tracks(playlistId TEXT NOT NULL, position INTEGER NOT NULL, videoId TEXT NOT NULL, PRIMARY KEY(playlistId, position))")
                old.execSQL("CREATE INDEX index_playlist_tracks_videoId ON playlist_tracks(videoId)")
                old.execSQL("CREATE TABLE favorites(videoId TEXT NOT NULL PRIMARY KEY, addedAt INTEGER NOT NULL)")
                old.execSQL("INSERT INTO playlists VALUES('PLtest', 'Old library', NULL, NULL, 'https://music.youtube.com/playlist?list=PLtest', 1, 100)")
                old.execSQL("INSERT INTO tracks VALUES('YGr2G2fc48U', 'schönhauser', 'Gustav', 155000, NULL)")
                old.execSQL("INSERT INTO playlist_tracks VALUES('PLtest', 0, 'YGr2G2fc48U')")
                old.execSQL("INSERT INTO favorites VALUES('YGr2G2fc48U', 200)")
                if (version >= 2) {
                    old.execSQL("ALTER TABLE playlists ADD COLUMN localTitle TEXT")
                    old.execSQL("UPDATE playlists SET localTitle = 'My old name'")
                    old.execSQL("CREATE TABLE history(videoId TEXT NOT NULL PRIMARY KEY, lastPlayedAt INTEGER NOT NULL)")
                    old.execSQL("INSERT INTO history VALUES('YGr2G2fc48U', 300)")
                    old.execSQL("CREATE VIRTUAL TABLE tracks_fts USING FTS4(videoId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, notindexed=`videoId`, tokenize=unicode61)")
                    old.execSQL("INSERT INTO tracks_fts(rowid, videoId, title, artist) SELECT rowid, videoId, title, artist FROM tracks")
                    old.execSQL("CREATE TRIGGER tracks_search_update AFTER UPDATE ON tracks BEGIN DELETE FROM tracks_fts WHERE rowid = old.rowid; INSERT INTO tracks_fts(rowid, videoId, title, artist) VALUES(new.rowid, new.videoId, new.title, new.artist); END")
                }
                if (version >= 3) old.execSQL("ALTER TABLE playlist_tracks ADD COLUMN locallyAdded INTEGER NOT NULL DEFAULT 0")
                if (version >= 4) {
                    old.execSQL("CREATE TABLE offline_playlists (playlistId TEXT NOT NULL PRIMARY KEY)")
                    old.execSQL("CREATE TABLE downloads (videoId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, bytes INTEGER NOT NULL, total INTEGER NOT NULL, error TEXT)")
                }
                old.version = version
            }
            val db = PyteDatabase.open(context, name)
            try {
                val library = repo(db)
                assertEquals(if (version >= 2) "My old name" else "Old library", library.observePlaylist("PLtest").first()!!.first.title)
                assertFalse(db.playlistDao().getPlaylistLinks("PLtest").single().locallyAdded)
                if (version >= 2) assertEquals(song.videoId, library.observeHistory().first().single().videoId)
                assertTrue(db.offlineDao().wantedTracks().isEmpty())
                assertEquals(song.videoId, library.observeFavorites().first().single().videoId)
                assertEquals(song.videoId, library.search("schon").first().single().videoId)
                library.saveImported(playlist(song.copy(title = "Updated")))
                assertEquals(song.videoId, library.search("updated").first().single().videoId)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }
}
